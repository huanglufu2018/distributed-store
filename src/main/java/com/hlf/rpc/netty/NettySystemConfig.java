package com.hlf.rpc.netty;

/**
 * Netty相关的系统配置
 * @author huanglufu
 *
 */
public class NettySystemConfig {
	
	public static final String COM_HLF_HMQ_REMOTING_NETTY_POOLED_BYTE_BUF_ALLOCATOR_ENABLE =
	        "com.hlf.hmq.nettyPooledByteBufAllocatorEnable";
	
	public static final String COM_HLF_HMQ_REMOTING_SOCKET_SND_BUFFER_SIZE = 
			"com.hlf.hmq.remoting.socket.sndbuf.size";
	public static final String COM_HLF_HMQ_REMOTING_SOCKET_RCV_BUFFER_SIZE = 
			"com.hlf.hmq.remoting.socket.rcvbuf.size";
	
	
	public static final String COM_HLF_HMQ_REMOTING_CLIENT_ASYNC_SEMAPHORE_VALUE = 
			"com.hlf.hmq.remoting.clientAsyncSemphoreValue";
	public static final String COM_HLF_HMQ_REMOTING_CLIENT_ONEWAY_SEMAPHORE_VALUE = 
			"com.hlf.hmq.remoting.clientOnewaySemphoreValue";
	
	
	
	public static final boolean NETTY_POOLED_BYTE_BUF_ALLOCATOR_ENABLE = 
	        Boolean.parseBoolean(System.getProperty(COM_HLF_HMQ_REMOTING_NETTY_POOLED_BYTE_BUF_ALLOCATOR_ENABLE, "false"));
	
	/**
	 * socket缓冲相关参数
	 */
	public static int socketSndBufSize = 
			Integer.parseInt(System.getProperty(COM_HLF_HMQ_REMOTING_SOCKET_SND_BUFFER_SIZE, "65535"));
	public static int socketRcvBufSize = 
			Integer.parseInt(System.getProperty(COM_HLF_HMQ_REMOTING_SOCKET_RCV_BUFFER_SIZE, "65535"));
	
	/**
	 * 	远程客户端限流相关参数
	 */
	public static final int CLIENT_ASYNC_SEMAPHORE_VALUE = 
	        Integer.parseInt(System.getProperty(COM_HLF_HMQ_REMOTING_CLIENT_ASYNC_SEMAPHORE_VALUE, "65535"));
	public static final int CLIENT_ONEWAY_SEMAPHORE_VALUE =
	        Integer.parseInt(System.getProperty(COM_HLF_HMQ_REMOTING_CLIENT_ONEWAY_SEMAPHORE_VALUE, "65535"));
}
