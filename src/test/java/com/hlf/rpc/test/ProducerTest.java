package com.hlf.rpc.test;

import com.alibaba.fastjson.JSON;
import com.hlf.rpc.netty.NettyClientConfig;
import com.hlf.rpc.test.model.OrderDTO;
import com.hlf.rpc.test.producer.DefaultMQProducer;
import com.hlf.rpc.test.store.Message;

public class ProducerTest {

    public static void main(String[] args) {
        try {
            NettyClientConfig nettyClientConfig = new NettyClientConfig();
            String brokerAddr = "127.0.0.1:8888";

            DefaultMQProducer producer = new DefaultMQProducer(nettyClientConfig);
            producer.setBrokerAddr(brokerAddr);
            producer.start();

            for (int i = 0; i < 10; i++) {
                OrderDTO orderDTO = new OrderDTO("32432213000" + i, 5000.00, "厦门店-荣耀手机", 1, 0, "12432335");
                Message message = new Message("my-topic", JSON.toJSONString(orderDTO).getBytes("UTF-8"));
                String msgId = producer.send(message);
                System.out.println("消息发送完毕，返回的消息ID：" + msgId);
                System.out.println("=======================================");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
