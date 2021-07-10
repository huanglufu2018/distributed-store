package com.hlf.rpc.test.broker;

import com.hlf.rpc.exception.RemotingCommandException;
import com.hlf.rpc.netty.NettyRequestProcessor;
import com.hlf.rpc.protocol.RemotingCommand;
import com.hlf.rpc.test.model.RequestCode;
import com.hlf.rpc.test.producer.SendMessageRequestHeader;
import io.netty.channel.ChannelHandlerContext;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class SendMessageProcessor implements NettyRequestProcessor {

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) throws Exception {

        switch (request.getCode()) {
            case RequestCode.SEND_MESSAGE:
                // 1.处理请求，写入消息
                sendMessage(request);

                // 2.返回响应
                final RemotingCommand response = RemotingCommand.createResponseCommand(SendMessageResponseHeader.class);
                final SendMessageResponseHeader responseHeader = (SendMessageResponseHeader)response.readCustomHeader();
                responseHeader.setMsgId(UUID.randomUUID().toString().replace("-", ""));
                response.setOpaque(request.getOpaque());
                response.setCode(-1);
                if (response.getCode() != -1) {
                    return response;
                }
                return response;
            default:
               break;
        }
        return null;
    }


    private void sendMessage(RemotingCommand request) throws RemotingCommandException {
        SendMessageRequestHeader requestHeader = parseRequestHeader(request);
        final byte[] body = request.getBody();
        try {
            System.out.println("broker的SendMessageProcessor收到消息，消息topic为["+requestHeader.getTopic()+"],消息内容为["+new String(body, "UTF-8")+"]");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    protected SendMessageRequestHeader parseRequestHeader(RemotingCommand request)
            throws RemotingCommandException {
        SendMessageRequestHeader requestHeader =
                (SendMessageRequestHeader) request
                        .decodeCommandCustomHeader(SendMessageRequestHeader.class);
        return requestHeader;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
