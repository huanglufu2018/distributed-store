package com.hlf.rpc;

import com.hlf.rpc.common.Pair;
import com.hlf.rpc.exception.RemotingSendRequestException;
import com.hlf.rpc.exception.RemotingTimeoutException;
import com.hlf.rpc.netty.NettyRequestProcessor;
import com.hlf.rpc.protocol.RemotingCommand;
import io.netty.channel.Channel;

import java.util.concurrent.ExecutorService;

/**
 * rpc通信服务端
 */
public interface RemotingServer extends RemotingService {

    /**
     * 注册请求处理器
     * @param requestCode
     * @param processor
     * @param executor
     */
    void registerProcessor(final int requestCode, final NettyRequestProcessor processor,
                           final ExecutorService executor);

    /**
     * 注册默认的请求处理器
     * @param processor
     * @param executor
     */
    void registerDefaultProcessor(final NettyRequestProcessor processor, final ExecutorService executor);

    /**
     * 获取本地监听端口
     * @return
     */
    int localListenPort();

    /**
     * 获取requestCode对应的Pair，其中包含该requestCode对应的处理器，及用于执行处理逻辑的ExecutorService
     * @param requestCode
     * @return
     */
    Pair<NettyRequestProcessor, ExecutorService> getProcessorPair(final int requestCode);

    /**
     * 同步调用
     * @param channel
     * @param request
     * @param timeoutMillis
     * @return
     * @throws InterruptedException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     */
    RemotingCommand invokeSync(final Channel channel, final RemotingCommand request,
            final long timeoutMillis) throws InterruptedException, RemotingTimeoutException,
            RemotingSendRequestException;

    // 异步调用与oneway略....
}
