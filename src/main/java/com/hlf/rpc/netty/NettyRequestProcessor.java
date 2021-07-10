package com.hlf.rpc.netty;

import com.hlf.rpc.protocol.RemotingCommand;
import io.netty.channel.ChannelHandlerContext;

/**
 * 基于Netty的请求处理器
 */
public interface NettyRequestProcessor {

    /**
     * 处理请求
     * @param ctx
     * @param request
     * @return
     * @throws Exception
     */
    RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) throws Exception;

    /**
     * 拒绝请求
     * @return
     */
    boolean rejectRequest();
}
