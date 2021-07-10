package com.hlf.store.test;

import com.hlf.store.AppendMessageResult;
import com.hlf.store.MessageDecoder;
import com.hlf.store.MessageExtBrokerInner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TestAppendMessage2 {

    // 添加500000 条消息总耗时：4117
    public static void main(String[] args) {
        try {
            UnMappedFile unMappedFile = new UnMappedFile("F:\\storetest\\0", 1024 * 1024 * 1024);

            MessageExtBrokerInner message = new MessageExtBrokerInner();
            message.setTopic("test");
            Map<String, String> properties = new HashMap<String, String>();
            properties.put("type", "message");
            message.setProperties(properties);
            message.setBody(getMessageBody().getBytes(MessageDecoder.CHARSET_UTF8));

            long msgSize = 500000;
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < msgSize; i++) {
                AppendMessageResult result = unMappedFile.appendMessagesInner(message);
//                System.out.println("第" + i + "条消息添加完毕, 消息长度：" + result.getWroteBytes() + "B");
            }
            long endTime = System.currentTimeMillis();
            System.out.println("添加" + msgSize + " 条消息总耗时：" + (endTime - startTime));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static String getMessageBody() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append("test");
            builder.append("-");
            builder.append("msg");
        }
        return builder.toString();
    }
}
