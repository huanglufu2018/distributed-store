package com.hlf.rpc.netty;

import com.hlf.rpc.RPCHook;
import com.hlf.rpc.common.Pair;
import com.hlf.rpc.common.RemotingHelper;
import com.hlf.rpc.exception.RemotingSendRequestException;
import com.hlf.rpc.exception.RemotingTimeoutException;
import com.hlf.rpc.protocol.RemotingCommand;
import com.hlf.rpc.protocol.RemotingSysResponseCode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public abstract class NettyRemotingAbstract {

    // <request code, ResponseFuture>
    protected final ConcurrentMap<Integer, ResponseFuture> responseTable = new ConcurrentHashMap<>(256);
    // 请求处理器注册表 <request code, Pair>
    protected final HashMap<Integer, Pair<NettyRequestProcessor, ExecutorService>>
            processorTable = new HashMap<>(64);
    // 默认的请求处理器
    protected Pair<NettyRequestProcessor, ExecutorService> defaultRequestProcessor;

    protected List<RPCHook> rpcHooks = new ArrayList<>();


    /**
     * 处理接收到的消息
     */
    public void processMessageReceived(ChannelHandlerContext ctx, RemotingCommand msg) {
        final RemotingCommand cmd = msg;
        if (cmd != null) {
            switch (cmd.getType()) {
                case REQUEST_COMMAND:
                    // 如果是请求命令，调用处理请求的方法
                    processRequestCommand(ctx, msg);
                    break;
                case RESPONSE_COMMAND:
                    // 如果是响应命令，调用处理响应的方法
                    processResponseCommand(ctx, msg);
                    break;
                default:
                    break;
            }
        }
    }



    /** ------------------------------处理对端的请求-----------------------------**/
    /**
     * 处理对端发送过来的请求
     */
    public void processRequestCommand(ChannelHandlerContext ctx, RemotingCommand cmd) {

        // 通过请求命令编号code，在处理器列表processorTable中查找对应的请求处理器NettyRequestProssor的实现类，
        // 对请求进行处理，如果没有找到则使用默认的请求处理器
        final Pair<NettyRequestProcessor, ExecutorService> matched = this.processorTable.get(cmd.getCode());
        final Pair<NettyRequestProcessor, ExecutorService> pair = null == matched ? this.defaultRequestProcessor : matched;

        int opaque = cmd.getOpaque();

        if (pair != null) {
            // 定义一个Runnable，用于异步执行响应
            Runnable run = () -> {
                try {
                    // 执行处理前的钩子函数,略...
                    // 处理请求
                    RemotingCommand response = pair.getObject1().processRequest(ctx, cmd);
                    // 执行请求处理后的钩子函数，略...
                    // 省略oneway相关判断....
                    if (response != null) {
                        // 将请求中的opaque信息返回给对端
                        response.setOpaque(opaque);
                        // 标记cmd的类型为response
                        response.markResponseType();

                        // 发送响应
                        try {
                            System.out.println("对端请求["+ opaque +"]处理完毕,写回响应：" + response);
                            ctx.writeAndFlush(response);
                            System.out.println("================================================");
                        } catch (Throwable e) {
                            // 打印日志
                        }
                    } else {

                    }
                } catch (Exception e) {

                }
            };

            // 如果请求被拒绝
            if (pair.getObject1().rejectRequest()) {
                final RemotingCommand response = RemotingCommand.createResponseCommand(
                        RemotingSysResponseCode.SYSTEM_BUSY,
                        "[REJECTREQUEST]system busy, start flow control for a while");
                response.setOpaque(opaque);
                ctx.writeAndFlush(response);
                return;
            }

            // 通过线程池异步执行
            try {
                final RequestTask requestTask = new RequestTask(run, ctx.channel(), cmd);
                pair.getObject2().submit(requestTask);
            } catch (RejectedExecutionException e) {
                final RemotingCommand response = RemotingCommand.createResponseCommand(
                        RemotingSysResponseCode.SYSTEM_BUSY,
                        "[OVERLOAD]system busy, start flow control for a while");
                response.setOpaque(opaque);
                ctx.writeAndFlush(response);
            }

        } else {
            // 没有对应的请求类型处理器，给对端响应异常信息
            String error = " request type " + cmd.getCode() + " not supported";
            final RemotingCommand response = RemotingCommand
                    .createResponseCommand(RemotingSysResponseCode.REQUEST_CODE_NOT_SUPPORTED, error);
            response.setOpaque(opaque);
            ctx.writeAndFlush(response);
        }


    }


    /** ------------------------------本端发送请求，处理对端返回的对本端请求的响应-----------------------------**/
    /**
     * rpc同步调用，发送请求
     * @param channel
     * @param request
     * @param timeoutMillis
     * @return
     * @throws InterruptedException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     */
    public RemotingCommand invokeSyncImpl(final Channel channel, final RemotingCommand request,
        final long timeoutMillis)
            throws InterruptedException, RemotingSendRequestException, RemotingTimeoutException {
        // 获取本次请求的标识
        final int opaque = request.getOpaque();
        try {
            // 创建ResponseFuture，以便完成请求的同步转异步
            // 请求发送失败时，会在ChannelFutureListener#operationComplete方法中设置ResponseFuture#responseCommand为Null
            // 请求发送成功，且没有超时，会在NettyRemotingAbstract#processResponseCommand方法中设置ResponseFuture#responseCommand
            final ResponseFuture responseFuture = new ResponseFuture(channel, opaque, timeoutMillis, null, null);
            this.responseTable.put(opaque, responseFuture);
            final SocketAddress addr = channel.remoteAddress();
            // 发送请求
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // 请求发送成功
                        responseFuture.setSendRequestOK(true);
                        return;
                    } else {
                        // 请求发送失败
                        responseFuture.setSendRequestOK(false);
                    }

                    responseTable.remove(opaque);
                    responseFuture.setCause(future.cause());
                    // 请求发送失败，响应结果为null
                    responseFuture.putResponse(null);
                }
            });

            // 请求发送后，同步等待响应结果
            System.out.println("client发送请求["+opaque+"}]后，等待返回响应....");
            RemotingCommand responseCommand = responseFuture.waitResponse(timeoutMillis);
            if (null == responseCommand) {
                if (responseFuture.isSendRequestOK()) {
                    // 请求发送成功，但reponseCommand为null，说明等待超时，抛出超时异常
                    throw new RemotingTimeoutException(RemotingHelper.parseSocketAddressAddr(addr), timeoutMillis,
                            responseFuture.getCause());
                } else {
                    // 请求发送失败，抛出远程请求发送失败异常
                    throw new RemotingSendRequestException(RemotingHelper.parseSocketAddressAddr(addr), responseFuture.getCause());
                }
            }

            return responseCommand;
        } finally {
            // 移除本次请求对应的ResponseFuture对象
            System.out.println("client本次请求[" + opaque + "]完毕,将其对应的响应从responseTable列表中移除");
            this.responseTable.remove(opaque);
        }
    }

    /**
     * 处理对端发送过来的，自己发出的请求对应的响应
     * @param ctx
     * @param cmd
     */
    public void processResponseCommand(ChannelHandlerContext ctx, RemotingCommand cmd) {
        int opaque = cmd.getOpaque();
        // 找到该opaque请求对应的ResponseFuture进行处理
        final ResponseFuture responseFuture = this.responseTable.get(opaque);
        if (responseFuture != null) {
            responseFuture.setResponseCommand(cmd);
            this.responseTable.remove(opaque);
            if (responseFuture.getInvokeCallback() != null) {
                executeInvokeCallback(responseFuture);
            } else {
                System.out.println("收到请求["+opaque+"]对应的响应["+cmd+"]，同步等待结束...");
                responseFuture.putResponse(cmd);
                responseFuture.release();
            }

        } else {
            // 打印日志
        }
    }

    /**
     * 使用回调执行器执行回调，如果回调执行器为空，则在当前线程中执行
     * @param responseFuture
     */
    private void executeInvokeCallback(final ResponseFuture responseFuture) {
        boolean runInThisThread = false;
        ExecutorService executor = this.getCallbackExecutor();
        if (executor != null) {
            try {
                executor.submit(() -> {
                    try {
                        responseFuture.executeInvokeCallback();
                    } catch (Throwable e) {
                    } finally {
                        responseFuture.release();
                    }
                });

            } catch (Exception e) {
                runInThisThread = true;
            }
        } else {
            runInThisThread = true;
        }

        if (runInThisThread) {
            try {
                responseFuture.executeInvokeCallback();
            } catch (Throwable e) {
            } finally {
                responseFuture.release();
            }
        }
    }

    public abstract ExecutorService getCallbackExecutor();




}
