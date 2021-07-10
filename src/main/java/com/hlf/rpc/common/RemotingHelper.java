package com.hlf.rpc.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.hlf.rpc.exception.RemotingConnectException;
import com.hlf.rpc.exception.RemotingSendRequestException;
import com.hlf.rpc.exception.RemotingTimeoutException;
import com.hlf.rpc.protocol.RemotingCommand;
import io.netty.channel.Channel;

/**
 * 远程调用辅助工具类
 * @author huanglufu
 *
 */
public class RemotingHelper {
	
	public static final String ROCKETMQ_REMOTING = "HmqRemoting";
    public static final String DEFAULT_CHARSET = "UTF-8";
	
    /**
     * 获取异常信息的简单描述
     * @param e
     * @return
     */
	public static String exceptionSimpleDesc(final Throwable e) {
		StringBuffer sb = new StringBuffer();
		if (e != null) {
			sb.append(e.toString());
			
			StackTraceElement[] stackTrace = e.getStackTrace();
			if (stackTrace != null && stackTrace.length >0) {
				StackTraceElement element = stackTrace[0];
				sb.append(",");
				sb.append(element.toString());
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * 将字符串解析为SocketAddress对象
	 * @param addr
	 * @return
	 */
	public static SocketAddress string2SocketAddress(final String addr) {
		String[] s = addr.split(":");
		InetSocketAddress isa = new InetSocketAddress(s[0], Integer.parseInt(s[1]));
		return isa;
	}
	
	/**
	 * 远程同步调用
	 * @param addr
	 * @param request
	 * @param timeoutMillis
	 * @return
	 * @throws RemotingConnectException 
	 * @throws RemotingSendRequestException 
	 * @throws InterruptedException 
	 * @throws RemotingTimeoutException 
	 */
	public static RemotingCommand invokeSync(final String addr, final RemotingCommand request,
			final long timeoutMillis) throws RemotingConnectException, RemotingSendRequestException,
			InterruptedException, RemotingTimeoutException {
		
		long beginTime = System.currentTimeMillis();
		SocketAddress socketAddress = RemotingUtil.string2SocketAddress(addr);
		SocketChannel socketChannel = RemotingUtil.connect(socketAddress);// 不传超时参数则默认5秒超时
		
		if (socketChannel != null) {
			boolean sendRequestOK = false;
			
			try {
				socketChannel.configureBlocking(true);
				socketChannel.socket().setSoTimeout((int) timeoutMillis);
				
				// 序列化请求RemotingCommand
				ByteBuffer byteBufferRequest = request.encode();
				// 将请求数据写入通道，进行网络通信
				while (byteBufferRequest.hasRemaining()) {
					int length = socketChannel.write(byteBufferRequest);
					if (length > 0) {
						if (byteBufferRequest.hasRemaining()) {
							// byteBufferRequest中还有数据，但是已超时
							if ((System.currentTimeMillis() - beginTime) > timeoutMillis) {
								throw new RemotingSendRequestException(addr);
							}
						}
					} else {
						throw new RemotingSendRequestException(addr);
					}
					
					Thread.sleep(1);
				}
				
				// 代码运行到此处，请求发送成功
				sendRequestOK = true;
				
				// 将响应字节组数中的前4个字节(记录响应信息字节数组长度)从通道中读入ByteBuffer，通过自定义协议知道，使用int类型记录响应字节数组的长度，占用4个字节
				ByteBuffer byteBufferSize = ByteBuffer.allocate(4);
				while (byteBufferSize.hasRemaining()) {
					int length = socketChannel.read(byteBufferSize);
					if (length >0) {
						if (byteBufferSize.hasRemaining()) {
							if ((System.currentTimeMillis() - beginTime) > timeoutMillis) {
                                throw new RemotingTimeoutException(addr, timeoutMillis);
                            }
						}
					} else {
						throw new RemotingTimeoutException(addr, timeoutMillis);
					}
					Thread.sleep(1);
				}
				
				// 响应字节数组的长度
				int size = byteBufferSize.getInt();
				// 读取真正的响应内容
				ByteBuffer byteBufferBody = ByteBuffer.allocate(size);
				while (byteBufferBody.hasRemaining()) {
					int length = socketChannel.read(byteBufferBody);
					if (length > 0) {
						// 超时判断
						if (byteBufferBody.hasRemaining()) {
							if ((System.currentTimeMillis() - beginTime) > timeoutMillis) {
								throw new RemotingTimeoutException(addr, timeoutMillis);
							}
						}
					} else {
						throw new RemotingTimeoutException(addr, timeoutMillis);
					}
					
					Thread.sleep(1);
				}
				
				// 切换byteBufferBody为读模式
				byteBufferBody.flip();
				return RemotingCommand.decode(byteBufferBody);
				
			} catch (IOException e) {
//				log.error("invokeSync failure", e);
                if (sendRequestOK) {
                    throw new RemotingTimeoutException(addr, timeoutMillis);
                } else {
                    throw new RemotingSendRequestException(addr);
                }
			} finally {
				try {
					socketChannel.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else {
			throw new RemotingConnectException(addr);
		}
	}
	
	
	
	/**
	 * 解析Channel的远程地址
	 * @param channel
	 * @return
	 */
	public static String parseChannelRemoteAddr(final Channel channel) {
		if (null == channel) {
			return "";
		}
		
		SocketAddress remote = channel.remoteAddress();
		final String addr = remote != null ? remote.toString() : "";
		
		if (addr.length() >0) {
			int index = addr.lastIndexOf("/");
			if (index >= 0) {
				return addr.substring(index +1);
			}
			
			return addr;
		}
		
		return "";
	}
	
	public static String parseSocketAddressAddr(SocketAddress socketAddress) {
		if (socketAddress != null) {
			final String addr = socketAddress.toString();
			
			if (addr.length() >0) {
				return addr.substring(1);// addr的格式为：/192.168.1.103:80，去除第一个"/"
			}
		}
		
		return "";
	}
	
	
	
	
}
