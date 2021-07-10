package com.hlf.rpc.test.store;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;

public class MessageDecoder {
    public static final char NAME_VALUE_SEPARATOR = 1;
    public static final char PROPERTY_SEPARATOR = 2;
    public static final int MSG_ID_LENGTH = 8; // 8字节消息物理偏移量

    public static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
    final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String createMessageId(final ByteBuffer input, final long offset) {
        input.flip();
        input.limit(MSG_ID_LENGTH);
        input.putLong(offset);// 8字节消息物理偏移量
        return bytes2string(input.array());
    }

    public static String bytes2string(byte[] src) {
        char[] hexChars = new char[src.length * 2];
        for (int j = 0; j < src.length; j++) {
            int v = src[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String messageProperties2String(Map<String, String> properties) {
        StringBuilder sb = new StringBuilder();
        if (properties != null) {
            for (final Map.Entry<String, String> entry : properties.entrySet()) {
                final String name = entry.getKey();
                final String value = entry.getValue();

                sb.append(name);
                sb.append(NAME_VALUE_SEPARATOR);
                sb.append(value);
                sb.append(PROPERTY_SEPARATOR);
            }
        }
        return sb.toString();
    }
}
