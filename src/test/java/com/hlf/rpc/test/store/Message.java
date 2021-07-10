package com.hlf.rpc.test.store;

import java.io.Serializable;
import java.util.Map;

public class Message implements Serializable {

    private static final long serialVersionUID = 8445773977080406428L;

    public Message(String topic, byte[] body) {
        this.topic = topic;
        this.body = body;
    }

    // 所属主题
    private String topic;
    // 消息扩展属性
    private Map<String, String> properties;
    // 消息体
    private byte[] body;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
