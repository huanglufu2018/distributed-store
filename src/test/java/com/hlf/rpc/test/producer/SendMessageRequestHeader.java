package com.hlf.rpc.test.producer;

import com.hlf.rpc.CommandCustomHeader;

public class SendMessageRequestHeader implements CommandCustomHeader {

    private String topic;
    private String properties;
    private long bornTimestamp;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public long getBornTimestamp() {
        return bornTimestamp;
    }

    public void setBornTimestamp(long bornTimestamp) {
        this.bornTimestamp = bornTimestamp;
    }

    @Override
    public void checkFields() {

    }
}
