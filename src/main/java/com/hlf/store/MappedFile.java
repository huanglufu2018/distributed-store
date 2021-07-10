package com.hlf.store;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存映射文件
 */
public class MappedFile {

    // 操作系统每页大小，默认4K
    public static final int OS_PAGE_SIZE = 1024 * 4;// 页大小4KB
    // 文件名
    private String fileName;
    // 物理文件
    private File file;
    // 文件大小
    private int fileSize;
    // 文件通道
    private FileChannel fileChannel;
    // mmap对应的内存映射ByteBuffer
    private MappedByteBuffer mappedByteBuffer;

    // 当前内存映射文件中的写指针
    private final AtomicInteger wrotePosition = new AtomicInteger(0);
    // 文件的起始偏移量
    private long fileFromOffset;

    /**
     * 构造方法，创建MappedFile
     * @param fileName
     * @param fileSize
     * @throws IOException
     */
    public MappedFile(String fileName, int fileSize) throws IOException {
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
        ensureDirOk(this.file.getParent());

        try {
            this.fileChannel = new RandomAccessFile(this.file, "rw").getChannel();
            this.mappedByteBuffer = this.fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            System.out.println("启用mmap");

            ok = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(!ok && this.fileChannel != null) {
                this.fileChannel.close();
            }
        }
    }


    public static void ensureDirOk(final String dirName) {
        if (dirName != null) {
            File f = new File(dirName);
            if (!f.exists()) {
                boolean result = f.mkdirs();
                System.out.println(dirName + " mkdir " + (result ? "OK" : "Failed"));
            }
        }
    }

    /**
     * 往文件中追加消息
     * @param msgInner
     * @param cb
     * @return
     */
    public AppendMessageResult appendMessagesInner(final MessageExtBrokerInner msgInner, final AppendMessageCallback cb) {
        assert msgInner != null;
        assert cb != null;

        // 获取内存映射文件的当前写指针
        int currentPos = this.wrotePosition.get();
        if (currentPos < this.fileSize) {
            ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
            byteBuffer.position(currentPos);
            AppendMessageResult result = cb.doAppend(this.fileFromOffset, byteBuffer, this.fileSize - currentPos, msgInner);
            this.wrotePosition.addAndGet(result.getWroteBytes());
            return result;
        }

        return new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR);

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

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public void setFileChannel(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }

    public MappedByteBuffer getMappedByteBuffer() {
        return mappedByteBuffer;
    }

    public void setMappedByteBuffer(MappedByteBuffer mappedByteBuffer) {
        this.mappedByteBuffer = mappedByteBuffer;
    }

    public AtomicInteger getWrotePosition() {
        return wrotePosition;
    }
}
