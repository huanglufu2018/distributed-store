package com.hlf.store;

import java.nio.ByteBuffer;

/**
 * append消息回调接口
 */
public interface AppendMessageCallback {

    AppendMessageResult doAppend(final long fileFromOffset, final ByteBuffer byteBuffer,
                                 final int maxBlank, final MessageExtBrokerInner msg);
}
