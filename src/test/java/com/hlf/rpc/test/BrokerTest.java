package com.hlf.rpc.test;
import com.hlf.rpc.netty.NettyServerConfig;
import com.hlf.rpc.test.broker.BrokerController;

public class BrokerTest {

    public static void main(String[] args) {
        final NettyServerConfig nettyServerConfig = new NettyServerConfig();
        BrokerController brokerController = new BrokerController(nettyServerConfig);
        boolean result = brokerController.initialize();
        if (result) {
            brokerController.start();
        }
    }
}
