package com.hlf.rpc.test.broker;

import com.hlf.rpc.CommandCustomHeader;

public class SendMessageResponseHeader implements CommandCustomHeader {

    private String msgId;

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    @Override
    public void checkFields() {

    }
}
