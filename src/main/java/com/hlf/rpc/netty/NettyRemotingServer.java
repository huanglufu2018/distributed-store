package com.hlf.rpc.netty;

import com.hlf.rpc.ChannelEventListener;
import com.hlf.rpc.RPCHook;
import com.hlf.rpc.RemotingServer;
import com.hlf.rpc.common.Pair;
import com.hlf.rpc.exception.RemotingSendRequestException;
import com.hlf.rpc.exception.RemotingTimeoutException;
import com.hlf.rpc.protocol.RemotingCommand;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于Netty实现的rpc通信服务端
 */
public class NettyRemotingServer extends NettyRemotingAbstract implements RemotingServer {

    private final ServerBootstrap serverBootstrap;
    private final EventLoopGroup eventLoopGroupSelector;
    private final EventLoopGroup eventLoopGroupBoss;
    private final NettyServerConfig nettyServerConfig;

    private final ExecutorService publicExecutor;
    private final ChannelEventListener channelEventListener;

    // 默认的事件执行线程池
    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    private int port;
    private NettyEncoder encoder;
    private NettyServerHandler serverHandler;

    public NettyRemotingServer(final NettyServerConfig nettyServerConfig) {
        this(nettyServerConfig, null);
    }

    public NettyRemotingServer(final NettyServerConfig nettyServerConfig,
                               final ChannelEventListener channelEventListener) {
        this.serverBootstrap = new ServerBootstrap();
        this.nettyServerConfig = nettyServerConfig;
        this.channelEventListener = channelEventListener;

        int publicThreadNums = nettyServerConfig.getServerCallbackExecutorThreads();
        if (publicThreadNums <= 0) {
            publicThreadNums = 4;
        }

        // 创建publicExecutor线程池
        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNums, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyServerPublicExecotor_" + this.threadIndex.incrementAndGet());
            }
        });

        // 简化处理，省略useEpoll判断
        // 创建Boss线程组
        this.eventLoopGroupBoss = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyNIOBoss_%d", this.threadIndex.incrementAndGet()));
            }
        });

        /**
         * 	创建Selector线程组，组中的线程数根据nettyServerConfig配置设定
         */
        this.eventLoopGroupSelector = new NioEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger();
            private int threadTotal = nettyServerConfig.getServerSelectorThreads();
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyServerNIOSelector_%d_%d", threadTotal, threadIndex.incrementAndGet()));
            }
        });

        // 省略Ssl
    }


    /**
     * 启动rpc通信服务端
     */
    public void start() {

        // 初始化默认事件执行线程池
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
                nettyServerConfig.getServerWorkerThreads(),
                new ThreadFactory() {
                    private AtomicInteger threadIndex = new AtomicInteger(0);
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "NettyServerCodecThread_" + this.threadIndex.incrementAndGet());
                    }
                });

        // 准备共享的Netty Handler
        prepareSharableHandlers();

        ServerBootstrap childHandler =
                this.serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupSelector)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 1024)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.SO_KEEPALIVE, false)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.SO_SNDBUF, nettyServerConfig.getServerSocketSndBufSize())
                        .childOption(ChannelOption.SO_RCVBUF, nettyServerConfig.getServerSocketRcvBufSize())
                        .localAddress(new InetSocketAddress(this.nettyServerConfig.getListenPort()))
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline()
                                        .addLast(defaultEventExecutorGroup,
                                                new NettyEncoder(),
                                                new NettyDecoder(),
                                                new NettyServerHandler());
                            }
                        });

        if (nettyServerConfig.isServerPooledByteBufAllocatorEnable()) {
            childHandler.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        }

        try {
            ChannelFuture sync = this.serverBootstrap.bind().sync();
            InetSocketAddress addr = (InetSocketAddress) sync.channel().localAddress();
            this.port = addr.getPort();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止rpc服务端
     */
    public void shutdown() {
        try {
            this.eventLoopGroupBoss.shutdownGracefully();
            this.eventLoopGroupSelector.shutdownGracefully();
            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {

        }
        if (this.publicExecutor != null) {
            try {
                this.publicExecutor.shutdown();
            } catch (Exception e) {

            }
        }
    }

    /**
     * 注册RPCHook
     * @param rpcHook
     */
    public void registerRPCHook(RPCHook rpcHook) {
        if (rpcHook != null && !rpcHooks.contains(rpcHook)) {
            rpcHooks.add(rpcHook);
        }
    }

    /**
     * 注册请求处理器
     * @param requestCode
     * @param processor
     * @param executor
     */
    public void registerProcessor(int requestCode, NettyRequestProcessor processor, ExecutorService executor) {
        ExecutorService executorThis = executor;
        if (executor == null) {
            executorThis = this.publicExecutor;
        }
        Pair<NettyRequestProcessor, ExecutorService> pair = new Pair<>(processor, executorThis);
        this.processorTable.put(requestCode, pair);
    }

    /**
     * 注册请求处理器
     * @param processor
     * @param executor
     */
    public void registerDefaultProcessor(NettyRequestProcessor processor, ExecutorService executor) {
        this.defaultRequestProcessor = new Pair<>(processor, executor);
    }

    public int localListenPort() {
        return this.port;
    }

    /**
     * 根据请求编号，获取对应的请求处理器
     * @param requestCode
     * @return
     */
    public Pair<NettyRequestProcessor, ExecutorService> getProcessorPair(int requestCode) {
        return this.processorTable.get(requestCode);
    }

    /**
     * 以同步的方式发送rpc请求
     * @param channel
     * @param request
     * @param timeoutMillis
     * @return
     * @throws InterruptedException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     */
    public RemotingCommand invokeSync(Channel channel, RemotingCommand request, long timeoutMillis) throws InterruptedException, RemotingTimeoutException, RemotingSendRequestException {
        return this.invokeSyncImpl(channel, request, timeoutMillis);
    }



    private void prepareSharableHandlers() {
        encoder = new NettyEncoder();
        serverHandler = new NettyServerHandler();
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return this.publicExecutor;
    }


    /**--------------------------Handler----------------------**/
    /**
     *  NettyServer消息处理
     * @author huanglufu
     *
     */
    @ChannelHandler.Sharable
    class NettyServerHandler extends SimpleChannelInboundHandler<RemotingCommand> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand msg) throws Exception {
            // 服务端接收到网络数据包后，对请求进行处理
            processMessageReceived(ctx, msg);
        }
    }


}
