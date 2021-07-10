package com.hlf.store;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class MessageDecoder {

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
}
