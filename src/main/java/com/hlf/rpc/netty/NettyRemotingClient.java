package com.hlf.rpc.netty;

import com.hlf.rpc.ChannelEventListener;
import com.hlf.rpc.RPCHook;
import com.hlf.rpc.RemotingClient;
import com.hlf.rpc.common.Pair;
import com.hlf.rpc.common.RemotingHelper;
import com.hlf.rpc.common.RemotingUtil;
import com.hlf.rpc.exception.RemotingConnectException;
import com.hlf.rpc.exception.RemotingSendRequestException;
import com.hlf.rpc.exception.RemotingTimeoutException;
import com.hlf.rpc.protocol.RemotingCommand;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NettyRemotingClient extends NettyRemotingAbstract implements RemotingClient {

    private static final long LOCK_TIMEOUT_MILLIS = 3000;

    private final NettyClientConfig nettyClientConfig;
    private final EventLoopGroup eventLoopGroupWorker;
    private final Bootstrap bootstrap = new Bootstrap();

    private final Lock lockChannelTables = new ReentrantLock();
    private final ChannelEventListener channelEventListener;
    private final ConcurrentMap<String, ChannelWrapper> channelTables = new ConcurrentHashMap<>();

    // 公共的执行器
    private final ExecutorService publicExecutor;
    // 用于执行回调逻辑
    private ExecutorService callbackExecutor;
    // 默认的用于执行事件处理逻辑
    private DefaultEventExecutorGroup defaultEventExecutorGroup;


    public NettyRemotingClient(final NettyClientConfig nettyClientConfig) {
        this(nettyClientConfig, null);
    }

    public NettyRemotingClient(final NettyClientConfig nettyClientConfig,
            final ChannelEventListener channelEventListener) {
        this.nettyClientConfig = nettyClientConfig;
        this.channelEventListener = channelEventListener;
        int publicThreadNums = nettyClientConfig.getClientCallbackExecutorThreads();
        if (publicThreadNums <= 0) {
            publicThreadNums = 4;
        }

        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNums, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });

        this.eventLoopGroupWorker = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyClientSelector_%d", this.threadIndex.incrementAndGet()));
            }
        });
    }

    @Override
    public void start() {
        // 默认的事件执行组
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
                nettyClientConfig.getClientWorkerThreads(),
                new ThreadFactory() {

                    private AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "NettyClientWorkerThread_" + this.threadIndex.incrementAndGet());
                    }
                });

        Bootstrap handler = this.bootstrap.group(this.eventLoopGroupWorker).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyClientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.SO_SNDBUF, nettyClientConfig.getClientSocketSndBufSize())
                .option(ChannelOption.SO_RCVBUF, nettyClientConfig.getClientSocketRcvBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(
                                defaultEventExecutorGroup,
                                new NettyEncoder(),
                                new NettyDecoder(),
                                new NettyClientHandler());
                    }
                });
    }

    @Override
    public void shutdown() {
        try {
            for (ChannelWrapper cw : this.channelTables.values()) {
                this.closeChannel(null, cw.getChannel());
            }

            this.channelTables.clear();

            this.eventLoopGroupWorker.shutdownGracefully();

            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            // 输出日志
        }

        if (this.publicExecutor != null) {
            try {
                this.publicExecutor.shutdown();
            } catch (Exception e) {
                // 输出日志
            }
        }
    }

    @Override
    public void registerRPCHook(RPCHook rpcHook) {
        if (rpcHook != null && !rpcHooks.contains(rpcHook)) {
            rpcHooks.add(rpcHook);
        }
    }

    @Override
    public void registerProcessor(int requestCode, NettyRequestProcessor processor, ExecutorService executor) {
        ExecutorService executorThis = executor;
        if (null == executor) {
            executorThis = publicExecutor;
        }

        Pair<NettyRequestProcessor, ExecutorService> pair = new Pair<>(processor, executorThis);
        this.processorTable.put(requestCode, pair);
    }

    @Override
    public RemotingCommand invokeSync(String addr, RemotingCommand request, long timeoutMillis) throws InterruptedException, RemotingConnectException, RemotingTimeoutException, RemotingSendRequestException {
        long beginStartTime = System.currentTimeMillis();
        // 获取channel
        final Channel channel = this.getAndCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                // doBeforeRpcHooks...略
                // 处理请求
                long costTime = System.currentTimeMillis() - beginStartTime;
                if (timeoutMillis < costTime) {
                    // 超时异常
                    throw new RemotingTimeoutException("invokeSync call timeout");
                }

                // 发送请求
                RemotingCommand response = this.invokeSyncImpl(channel, request, timeoutMillis - costTime);
                // doAfterRpcHooks...略
                return response;
            } catch (RemotingSendRequestException e) {
                this.closeChannel(addr, channel);
                throw e;
            } catch (RemotingTimeoutException e) {
                if (nettyClientConfig.isClientCloseSocketIfTimeout()) {
                    this.closeChannel(addr, channel);
                }
                throw e;
            }
        } else {
            // 连接异常
            this.closeChannel(addr, channel);
            throw new RemotingConnectException(addr);
        }
    }

    private Channel getAndCreateChannel(final String addr) throws InterruptedException {
        if (addr == null) {
            return null;
        }
        ChannelWrapper cw = this.channelTables.get(addr);
        if (cw != null && cw.isOk()) {
            return cw.getChannel();
        }
        return this.createChannel(addr);
    }

    private Channel createChannel(String addr) throws InterruptedException {
        // 如果addr对应的Channel存在，先关闭
        ChannelWrapper cw = this.channelTables.get(addr);
        if (cw != null && cw.isOk()) {
            cw.getChannel().close();
            channelTables.remove(addr);
        }

        if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MICROSECONDS)) {
            try {
                // 先判断是否满足创建新连接条件
                boolean createNewConnection;
                cw = this.channelTables.get(addr);
                if (cw != null) {
                    if (cw.isOk()) {
                        cw.getChannel().close();
                        this.channelTables.remove(addr);
                        createNewConnection = true;
                    } else if(!cw.channelFuture.isDone()){
                        createNewConnection = false;
                    } else {
                        this.channelTables.remove(addr);
                        createNewConnection = false;
                    }
                } else {
                    createNewConnection = true;
                }

                // 如果满足创建新连接的条件，则创建新连接
                if (createNewConnection) {
                    ChannelFuture channelFuture = this.bootstrap.connect(RemotingHelper.string2SocketAddress(addr));
                    cw = new ChannelWrapper(channelFuture);
                    this.channelTables.put(addr, cw);
                }

            } catch (Exception e) {

            } finally {
                this.lockChannelTables.unlock();
            }
        } else {
            // 打印获取锁超时异常
        }

        if (cw != null) {
            ChannelFuture channelFuture = cw.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(this.nettyClientConfig.getConnectTimeoutMillis())) {
                if (cw.isOk()) {
                    return cw.getChannel();
                } else {
                    // 打印创建失败日志
                }
            }
        } else {
            // 打印超时异常
        }
        return null;
    }



    @Override
    public ExecutorService getCallbackExecutor() {
        return callbackExecutor != null ? callbackExecutor : publicExecutor;
    }


    /**
     * 关闭通道
     * @param addr
     * @param channel
     */
    public void closeChannel(final String addr, final Channel channel) {
        if (null == channel) {
            return ;
        }
        final String addrRemote = addr == null ? RemotingHelper.parseChannelRemoteAddr(channel) : addr;
        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    ChannelWrapper prevCW = this.channelTables.get(addrRemote);
                    if (null == prevCW) {
                        removeItemFromTable = false;
                    } else {
                        removeItemFromTable = false;
                    }
                    if (removeItemFromTable) {
                        this.channelTables.remove(addrRemote);
                    }
                    RemotingUtil.closeChannel(channel);
                } catch (Exception e) {
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
            }

        } catch (InterruptedException e) {
        }

    }

    /**
     * 静态内部类，包装ChannelFuture
     */
    static class ChannelWrapper {
        private final ChannelFuture channelFuture;
        public ChannelWrapper(ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        public boolean isOk() {
            return this.channelFuture.channel() != null && this.channelFuture.channel().isActive();
        }

        public boolean isWritable() {
            return this.channelFuture.channel().isWritable();
        }

        private Channel getChannel() {
            return this.channelFuture.channel();
        }

        public ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }

    /**
     * NettyClient处理器，处理客户端业务逻辑
     * @author huanglufu
     *
     */
    class NettyClientHandler extends SimpleChannelInboundHandler<RemotingCommand> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand msg) throws Exception {
            processMessageReceived(ctx, msg);
        }
    }
}
