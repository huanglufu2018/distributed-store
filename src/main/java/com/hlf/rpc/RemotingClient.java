package com.hlf.rpc;

import com.hlf.rpc.exception.RemotingConnectException;
import com.hlf.rpc.exception.RemotingSendRequestException;
import com.hlf.rpc.exception.RemotingTimeoutException;
import com.hlf.rpc.netty.NettyRequestProcessor;
import com.hlf.rpc.protocol.RemotingCommand;

import java.util.concurrent.ExecutorService;

/**
 * rpc调用客户端
 */
public interface RemotingClient extends RemotingService {

    /**
     * 注请求处理器
     * @param requestCode
     * @param processor
     * @param executor
     */
    void registerProcessor(final int requestCode, final NettyRequestProcessor processor,
                           final ExecutorService executor);

    /**
     * 同步调用
     * @param addr
     * @param request
     * @param timeoutMillis
     * @return
     * @throws InterruptedException
     * @throws RemotingConnectException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     */
    RemotingCommand invokeSync(final String addr, final RemotingCommand request,
                               final long timeoutMillis) throws InterruptedException, RemotingConnectException,
            RemotingTimeoutException, RemotingSendRequestException;


}
