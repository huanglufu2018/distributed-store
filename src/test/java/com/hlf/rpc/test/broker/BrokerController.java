package com.hlf.rpc.test.broker;

import com.hlf.rpc.RemotingServer;
import com.hlf.rpc.netty.NettyRemotingServer;
import com.hlf.rpc.netty.NettyServerConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BrokerController {

    private NettyServerConfig nettyServerConfig;
    private RemotingServer remotingServer;

    public BrokerController(NettyServerConfig nettyServerConfig) {
        this.nettyServerConfig = nettyServerConfig;
    }

    public boolean initialize() {
        try {
            this.remotingServer = new NettyRemotingServer(this.nettyServerConfig);
            ExecutorService remotingExecutor = Executors.newFixedThreadPool(nettyServerConfig.getServerWorkerThreads());
            this.remotingServer.registerDefaultProcessor(new SendMessageProcessor(), remotingExecutor);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void start() {
        this.remotingServer.start();
    }
}
