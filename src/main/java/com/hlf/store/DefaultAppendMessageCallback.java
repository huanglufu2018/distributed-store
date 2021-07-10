package com.hlf.store;

import java.nio.ByteBuffer;

public class DefaultAppendMessageCallback implements AppendMessageCallback {

    private static final int END_FILE_MIN_BLANK_LENGTH = 4 + 4;
    // 全局唯一消息ID内存缓存
    private final ByteBuffer msgIdMemory;
    // 存储消息内容
    private final ByteBuffer msgStoreItemMemory;
    // 消息最大长度
    private int maxMessageSize;

    public DefaultAppendMessageCallback(final int size) {
        this.msgIdMemory = ByteBuffer.allocate(MessageDecoder.MSG_ID_LENGTH);
        this.msgStoreItemMemory = ByteBuffer.allocate(size + END_FILE_MIN_BLANK_LENGTH);
        this.maxMessageSize = size;
    }

    /**
     * 将消息编码后，添加进ByteBuffer中
     * @param fileFromOffset
     * @param byteBuffer
     * @param maxBlank
     * @param msgInner
     * @return
     */
    public AppendMessageResult doAppend(long fileFromOffset, ByteBuffer byteBuffer, int maxBlank, MessageExtBrokerInner msgInner) {

        // 在整个消息文件中的起始物理偏移量，long类型，占用8字节
        long wroteOffset = fileFromOffset + byteBuffer.position();
        String msgId = MessageDecoder.createMessageId(this.msgIdMemory, wroteOffset);

        // 消息属性
        final byte[] propertiesData =
                msgInner.getPropertiesString() == null ? null : msgInner.getPropertiesString().getBytes(MessageDecoder.CHARSET_UTF8);
        final int propertiesLength = propertiesData == null ? 0: propertiesData.length;

        // 消息主题
        final byte[] topicData = msgInner.getTopic().getBytes(MessageDecoder.CHARSET_UTF8);
        final int topicLength = topicData.length;

        // 消息体
        final int bodyLength = msgInner.getBody() == null ? 0 : msgInner.getBody().length;


        // 计算消息总长度
        final int msgLength = calMsgLength(bodyLength, topicLength, propertiesLength);

        // 如果待插入的消息的长度大于配置的最大消息长度
        if (msgLength > this.maxMessageSize) {
            return new AppendMessageResult(AppendMessageStatus.MESSAGE_SIZE_EXCEEDED);
        }

        // 按消息存储格式填充msgStoreItemMemory
        // 初始化存储空间
        this.resetByteBuffer(msgStoreItemMemory, msgLength);

        // 消息总长度
        this.msgStoreItemMemory.putInt(msgLength);
        // 队列ID
        this.msgStoreItemMemory.putInt(msgInner.getQueueId());
        // 物理偏移量
        this.msgStoreItemMemory.putLong(fileFromOffset + byteBuffer.position());

        // 消息体长度及数据
        this.msgStoreItemMemory.putInt(bodyLength);
        if (bodyLength > 0)
            this.msgStoreItemMemory.put(msgInner.getBody());

        // 消息topic长度及数据
        this.msgStoreItemMemory.put((byte) topicLength);
        this.msgStoreItemMemory.put(topicData);

        // 消息属性长度及数据
        this.msgStoreItemMemory.putShort((short) propertiesLength);
        if (propertiesLength > 0) {
            this.msgStoreItemMemory.put(propertiesData);
        }

        // 写入消息到对应的内存映射文件中
        byteBuffer.put(this.msgStoreItemMemory.array(), 0 , msgLength);

        AppendMessageResult result = new AppendMessageResult(AppendMessageStatus.PUT_OK, wroteOffset, msgLength, msgId);
        return result;
    }

    /**
     * 计算消息总长度(根据消息存储格式计算)
     * @param bodyLength
     * @param topicLength
     * @param propertiesLength
     * @return
     */
    private int calMsgLength(int bodyLength, int topicLength, int propertiesLength) {
        final int msgLen = 4 // TOTALSIZE 该消息条目总长度 4字节
            + 4 // 消息消费队列ID 4字节
            + 8 // 物理偏移量
            + 4 + (bodyLength > 0 ? bodyLength : 0) // body data
            + 1 + topicLength // topic data
            + 2 + (propertiesLength > 0 ? propertiesLength : 0) // properties data
            + 0;
        return msgLen;
    }

    /**
     *  重置用于消息生成的堆ByteBuffer
     * @param byteBuffer
     * @param limit
     */
    private void resetByteBuffer(ByteBuffer byteBuffer, final int limit) {
        byteBuffer.flip();
        byteBuffer.limit(limit);
    }
}
