package com.hlf.rpc.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 自定义序列化方式
 * 对RemotingCommand中的协议头进行序列化编解码
 */
public class HMQSerializable {

    private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");


    /** --------------------------编码------------------------**/
    /**
     * 对协议头进行编码(RemotingCommand中的6个协议头)
     * int code
     * int version
     * int opaque
     * int flag
     * String remark
     * HashMap<String, String> extFields
     * @param cmd
     * @return
     */
    // HMQ协的编码方式
    public static byte[] hMQProtocolEncode(RemotingCommand cmd) {
        // remark处理
        byte[] remarkBytes = null;
        int remarkLen = 0;
        if (cmd.getRemark() != null && cmd.getRemark().length() > 0) {
            remarkBytes = cmd.getRemark().getBytes(CHARSET_UTF8);
            remarkLen = remarkBytes.length;
        }
        // extFields处理
        byte[] extFieldsBytes = null;
        int extLen = 0;
        if (cmd.getExtFields() != null && !cmd.getExtFields().isEmpty()) {
            extFieldsBytes = mapSerialize(cmd.getExtFields());
            extLen = extFieldsBytes.length;
        }
        int totalLen = calTotalLen(remarkLen, extLen);

        ByteBuffer headerBuffer = ByteBuffer.allocate(totalLen);
        headerBuffer.putShort((short) cmd.getCode());
        headerBuffer.putShort((short) cmd.getVersion());
        headerBuffer.putInt(cmd.getOpaque());
        headerBuffer.putInt(cmd.getFlag());
        // remark处理
        if (remarkBytes != null) {
            headerBuffer.putInt(remarkLen);
            headerBuffer.put(remarkBytes);
        } else {
            headerBuffer.putInt(0);
        }
        if (extFieldsBytes != null) {
            headerBuffer.putInt(extLen);
            headerBuffer.put(extFieldsBytes);
        } else {
            headerBuffer.putInt(0);
        }
        return headerBuffer.array();
    }


    /**
     * 将HashMap序列化为字节数组
     * @param map
     * @return
     */
    public static byte[] mapSerialize(HashMap<String, String> map) {
        if (null == map || map.isEmpty()) {
            return null;
        }

        int totalLength = 0;
        int kvLength;

        // 通过循环遍历计算kv的长度
        Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if (entry.getKey() != null && entry.getValue() != null) {
                kvLength =
                        // 限定key最大2个字节
                        2 + entry.getKey().getBytes(CHARSET_UTF8).length
                                // 限定value最大4字节
                        + 4 + entry.getValue().getBytes(CHARSET_UTF8).length;
                totalLength += kvLength;
            }
        }

        ByteBuffer content = ByteBuffer.allocate(totalLength);
        byte[] key;
        byte[] val;

        // kv转换为字节数组，存储在Buffer中
        it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if (entry.getKey() != null && entry.getValue() != null) {
                key = entry.getKey().getBytes(CHARSET_UTF8);
                val = entry.getValue().getBytes(CHARSET_UTF8);

                content.putShort((short) key.length);
                content.put(key);

                content.putInt(val.length);
                content.put(val);
            }
        }

        return content.array();
    }

    /**
     * remark和ext的长度是变化的，
     * 其余请求头的长度都是固定的
     * @param remark
     * @param ext
     * @return
     */
    private static int calTotalLen(int remark, int ext) {
        int length = 2 // int code(虽然定义的是int，但是实际使用的时候小于32766)
                + 2 // int version(虽然定义的是int，但是实际使用的时候小于32766)
                + 4 // int opaque
                + 4 // int flag
                + 4 + remark // String remark
                + 4 + ext; // HashMap<String, String> extFields
        return length;
    }

    /**-------------------------解码-----------------------------**/
    public static RemotingCommand hMQProtocolDecode(final byte[] headerArray) {
        RemotingCommand cmd = new RemotingCommand();
        ByteBuffer headerBuffer = ByteBuffer.wrap(headerArray);

        // 处理固定长度协议头
        cmd.setCode(headerBuffer.getShort());
        cmd.setVersion(headerBuffer.getShort());
        cmd.setOpaque(headerBuffer.getInt());
        cmd.setFlag(headerBuffer.getInt());

        // 处理变长remark
        int remarkLen = headerBuffer.getInt();
        if (remarkLen > 0) {
            byte[] remarkContent = new byte[remarkLen];
            headerBuffer.get(remarkContent);
            cmd.setRemark(new String(remarkContent, CHARSET_UTF8));
        }

        // 处理变长extFields
        int extFieldsLen = headerBuffer.getInt();
        if (extFieldsLen > 0) {
            byte[] extFieldsBytes = new byte[extFieldsLen];
            headerBuffer.get(extFieldsBytes);
            cmd.setExtFields(mapDeserialize(extFieldsBytes));
        }

        return cmd;
    }

    /**
     * 反序列化map
     * @param bytes
     * @return
     */
    public static HashMap<String, String> mapDeserialize(byte[] bytes) {
        if (bytes == null || bytes.length <= 0) {
            return null;
        }

        HashMap<String, String> map = new HashMap<>();
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        short keySize;
        byte[] keyContent;

        int valSize;
        byte[] valContent;

        while (byteBuffer.hasRemaining()) {
            keySize = byteBuffer.getShort();
            keyContent = new byte[keySize];
            byteBuffer.get(keyContent);

            valSize = byteBuffer.getInt();
            valContent = new byte[valSize];
            byteBuffer.get(valContent);

            map.put(new String(keyContent, CHARSET_UTF8), new String(valContent, CHARSET_UTF8));
        }

        return map;
    }



}
