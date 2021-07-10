package com.hlf.store.test;

import com.hlf.store.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RandomAccessFile不采用内存映射
 */
public class UnMappedFile {

    // 文件名
    private String fileName;
    // 物理文件
    private File file;
    // 文件大小
    private int fileSize;
    // 文件通道
    private RandomAccessFile randomAccessFile;

    // 写指针
    private final AtomicInteger wrotePosition = new AtomicInteger(0);
    // 文件的起始偏移量
    private long fileFromOffset;

    private static final int END_FILE_MIN_BLANK_LENGTH = 4 + 4;
    // 全局唯一消息ID内存缓存
    private final ByteBuffer msgIdMemory= ByteBuffer.allocate(MessageDecoder.MSG_ID_LENGTH);
    // 存储消息内容
    private final ByteBuffer msgStoreItemMemory = ByteBuffer.allocate(1024 + END_FILE_MIN_BLANK_LENGTH);
    // 消息最大长度
    private int maxMessageSize = 1024;



    /**
     * 构造方法，创建MappedFile
     * @param fileName
     * @param fileSize
     * @throws IOException
     */
    public UnMappedFile(String fileName, int fileSize) throws IOException {
        init(fileName, fileSize);
    }

    /**
     * 初始化MappedFile
     * @param fileName
     * @param fileSize
     * @throws IOException
     */
    private void init(String fileName, int fileSize) throws IOException {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.file = new File(fileName);
        this.fileFromOffset = Long.parseLong(file.getName());
        boolean ok = false;
        MappedFile.ensureDirOk(this.file.getParent());

        try {
            randomAccessFile = new RandomAccessFile(this.file, "rw");
            ok = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(!ok && this.randomAccessFile != null) {
                this.randomAccessFile.close();
            }
        }
    }


    /**
     * 往文件中追加消息
     * @param msgInner
     * @return
     */
    public AppendMessageResult appendMessagesInner(final MessageExtBrokerInner msgInner) throws IOException {
        assert msgInner != null;

        // 获取内存映射文件的当前写指针
        int currentPos = this.wrotePosition.get();
        if (currentPos < this.fileSize) {
            AppendMessageResult result = doAppend(this.fileFromOffset, this.fileSize - currentPos, msgInner);
            this.wrotePosition.addAndGet(result.getWroteBytes());
            return result;
        }

        return new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR);

    }


    public AppendMessageResult doAppend(long fileFromOffset, int maxBlank, MessageExtBrokerInner msgInner) throws IOException {

        // 在整个消息文件中的起始物理偏移量，long类型，占用8字节
        long wroteOffset = fileFromOffset + this.randomAccessFile.length();
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
        this.msgStoreItemMemory.putLong(fileFromOffset + this.randomAccessFile.length());

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

        long fileLength = randomAccessFile.length();
        // 将写文件指针移到文件尾。
        this.randomAccessFile.seek(fileLength);

        this.randomAccessFile.write(this.msgStoreItemMemory.array(), 0 , msgLength);


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








    public long getFileFromOffset() {
        return fileFromOffset;
    }

    public void setFileFromOffset(long fileFromOffset) {
        this.fileFromOffset = fileFromOffset;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public int getFileSize() {
        return fileSize;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public AtomicInteger getWrotePosition() {
        return wrotePosition;
    }
}
