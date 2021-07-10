package com.hlf.rpc.test.producer;

import com.hlf.rpc.RemotingClient;
import com.hlf.rpc.exception.RemotingCommandException;
import com.hlf.rpc.exception.RemotingConnectException;
import com.hlf.rpc.exception.RemotingSendRequestException;
import com.hlf.rpc.exception.RemotingTimeoutException;
import com.hlf.rpc.netty.NettyClientConfig;
import com.hlf.rpc.netty.NettyRemotingClient;
import com.hlf.rpc.protocol.RemotingCommand;
import com.hlf.rpc.test.model.RequestCode;
import com.hlf.rpc.test.broker.SendMessageResponseHeader;
import com.hlf.rpc.test.store.Message;
import com.hlf.rpc.test.store.MessageDecoder;

/**
 * 消息生产者
 */
public class DefaultMQProducer {
    private int sendMsgTimeout = 3000;
    private NettyClientConfig nettyClientConfig;
    private RemotingClient remotingClient;
    private String brokerAddr;

    public DefaultMQProducer(NettyClientConfig nettyClientConfig) {
        this.nettyClientConfig = nettyClientConfig;
        this.remotingClient = new NettyRemotingClient(this.nettyClientConfig);
    }

    public void setBrokerAddr(String brokerAddr) {
        this.brokerAddr = brokerAddr;
    }

    public void start() {
        if (this.remotingClient != null) {
            this.remotingClient.start();
        }
    }

    public String send(Message msg) throws InterruptedException, RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException {
        // 1.构造请求
        SendMessageRequestHeader requestHeader = new SendMessageRequestHeader();
        requestHeader.setTopic(msg.getTopic());
        requestHeader.setBornTimestamp(System.currentTimeMillis());
        requestHeader.setProperties(MessageDecoder.messageProperties2String(msg.getProperties()));
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, requestHeader);
        request.setBody(msg.getBody());

        // 2.发送请求
        RemotingCommand response = this.remotingClient.invokeSync(this.brokerAddr, request, this.sendMsgTimeout);

        try {
            // 3.解析响应
            SendMessageResponseHeader responseHeader =
                    (SendMessageResponseHeader) response
                            .decodeCommandCustomHeader(SendMessageResponseHeader.class);
            return responseHeader.getMsgId();
        } catch (RemotingCommandException e) {
            e.printStackTrace();
        }
        return null;
    }


}
