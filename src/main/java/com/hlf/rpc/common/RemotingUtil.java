package com.hlf.rpc.common;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Enumeration;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * 远程调用工具类
 * @author huanglufu
 *
 */
public class RemotingUtil {

	public static final String OS_NAME = System.getProperty("os.name");

	private static boolean isLinuxPlatform = false;
	private static boolean isWindowsPlatform = false;

	static {
		if (OS_NAME != null && OS_NAME.toLowerCase().contains("linux")) {
			isLinuxPlatform = true;
		}

		if (OS_NAME != null && OS_NAME.toLowerCase().contains("windows")) {
			isWindowsPlatform = true;
		}
	}

	public static boolean isWindowsPlatform() {
		return isWindowsPlatform;
	}

	public static boolean isLinuxPlatform() {
		return isLinuxPlatform;
	}

	/**
	 * 打开Selector
	 * 
	 * @return
	 * @throws IOException
	 */
	public static Selector openSelector() throws IOException {

		Selector result = null;
		if (isLinuxPlatform()) {// ePoll
			try {
				final Class<?> providerClazz = Class.forName("sun.nio.ch.EPollSelectorProvider");
				if (providerClazz != null) {
					try {
						Method method = providerClazz.getMethod("provider");
						if (method != null) {
							SelectorProvider selectorProvider = (SelectorProvider) method.invoke(null);
							if (selectorProvider != null) {
								result = selectorProvider.openSelector();
							}
						}
					} catch (Exception e) {
//						 log.warn("Open ePoll Selector for linux platform exception", e);
					}
				}

			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		if (result == null) {
			result = Selector.open();
		}

		return result;
	}

	public static String getLocalAddress() {
		try {
			// Traversal Network interface to get the first non-loopback and non-private
			// address
			Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
			ArrayList<String> ipv4Result = new ArrayList<String>();
			ArrayList<String> ipv6Result = new ArrayList<String>();
			while (enumeration.hasMoreElements()) {
				final NetworkInterface networkInterface = enumeration.nextElement();
				final Enumeration<InetAddress> en = networkInterface.getInetAddresses();
				while (en.hasMoreElements()) {
					final InetAddress address = en.nextElement();
					if (!address.isLoopbackAddress()) {
						if (address instanceof Inet6Address) {
							ipv6Result.add(normalizeHostAddress(address));
						} else {
							ipv4Result.add(normalizeHostAddress(address));
						}
					}
				}
			}

			// prefer ipv4
			if (!ipv4Result.isEmpty()) {
				for (String ip : ipv4Result) {
					if (ip.startsWith("127.0") || ip.startsWith("192.168")) {
						continue;
					}

					return ip;
				}

				return ipv4Result.get(ipv4Result.size() - 1);
			} else if (!ipv6Result.isEmpty()) {
				return ipv6Result.get(0);
			}
			// If failed to find,fall back to localhost
			final InetAddress localHost = InetAddress.getLocalHost();
			return normalizeHostAddress(localHost);
		} catch (Exception e) {
//			log.error("Failed to obtain local address", e);
		}

		return null;
	}

	public static String normalizeHostAddress(final InetAddress localHost) {
		if (localHost instanceof Inet6Address) {
			return "[" + localHost.getHostAddress() + "]";
		} else {
			return localHost.getHostAddress();
		}
	}

	/**
	 * SocketAddress与String的互转
	 * @param addr
	 * @return
	 */
	public static SocketAddress string2SocketAddress(final String addr) {
		String[] s = addr.split(":");
		InetSocketAddress isa = new InetSocketAddress(s[0], Integer.parseInt(s[1]));
		return isa;
	}

	public static String socketAddress2String(final SocketAddress addr) {
		StringBuilder sb = new StringBuilder();
		InetSocketAddress inetSocketAddress = (InetSocketAddress) addr;
		sb.append(inetSocketAddress.getAddress().getHostAddress());
		sb.append(":");
		sb.append(inetSocketAddress.getPort());
		return sb.toString();
	}
	
	/**
	 * 注意此处是java NIO中的SocketChannel而不是Netty中的SocketChannel
	 * @param remote
	 * @return
	 */
	public static SocketChannel connect(SocketAddress remote) {
		return connect(remote, 5 * 1000);
	}
	
	/**
	 * 打开与远程服务端的连接
	 * @param remote
	 * @param timeoutMillis
	 * @return
	 */
	public static SocketChannel connect(SocketAddress remote, final int timeoutMillis) {	
		SocketChannel sc = null;
		try {
			sc = SocketChannel.open();
			sc.configureBlocking(true);
			sc.socket().setSoLinger(false, -1);
			sc.socket().setTcpNoDelay(true);
			sc.socket().setReceiveBufferSize(1024 * 64);
			sc.socket().setSendBufferSize(1024 * 64);
			sc.socket().connect(remote, timeoutMillis);
			sc.configureBlocking(false);
			return sc;
		} catch (Exception e) {
			if (sc != null) {
				try {
					sc.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		return null;
	}
	
	/**
	 * 关闭Channel，注意此时的Channel是Netty中的Channel，而不是NIO中的
	 * @param channel
	 */
	public static void closeChannel(Channel channel) {
		String addrRemote = RemotingHelper.parseChannelRemoteAddr(channel);
		channel.close().addListener(new ChannelFutureListener() {
			
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
//				log.info("closeChannel: close the connection to remote address[{}] result: {}", addrRemote,
//	                    future.isSuccess());
			}
		});
	}
}
