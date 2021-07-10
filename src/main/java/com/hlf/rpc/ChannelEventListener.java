package com.hlf.rpc;

import io.netty.channel.Channel;

/**
 * 通道事件监听器，不同的通道事件触发时，做出相关处理
 */
public interface ChannelEventListener {

    void onChannelConnect(final String remoteAddr, final Channel channel);

    void onChannelClose(final String remoteAddr, final Channel channel);

    void onChannelException(final String remoteAddr, final Channel channel);

    void onChannelIdle(final String remoteAddr, final Channel channel);
}

