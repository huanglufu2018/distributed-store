package com.hlf.rpc.protocol;

import com.hlf.rpc.CommandCustomHeader;
import com.hlf.rpc.annotation.CFNotNull;
import com.hlf.rpc.exception.RemotingCommandException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义通信协议，抽象为RemotingCommand
 * 1.定义协议头和协议体
 * 2.定义相关序列化编解码方法
 */
public class RemotingCommand {

    public static final String REMOTING_VERSION_KEY = "hmq.remoting.version";
    private static AtomicInteger requestId = new AtomicInteger(0);
    private static volatile int configVersion = -1;
    private static final int RPC_TYPE = 0; // 0, REQUEST_COMMAND

    /**---------------------自定义协议头处理相关------------------------**/
    private static final Map<Class<? extends CommandCustomHeader>, Field[]> CLASS_HASH_MAP =
            new HashMap<>();
    private static final Map<Class, String> CANONICAL_NAME_CACHE = new HashMap<>();
    private static final Map<Field, Boolean> NULLABLE_FIELD_CACHE = new HashMap<>();// 缓存可为空的字段
    private static final String STRING_CANONICAL_NAME = String.class.getCanonicalName();
    private static final String DOUBLE_CANONICAL_NAME_1 = Double.class.getCanonicalName();
    private static final String DOUBLE_CANONICAL_NAME_2 = double.class.getCanonicalName();
    private static final String INTEGER_CANONICAL_NAME_1 = Integer.class.getCanonicalName();// java.lang.Integer
    private static final String INTEGER_CANONICAL_NAME_2 = int.class.getCanonicalName(); // int
    private static final String LONG_CANONICAL_NAME_1 = Long.class.getCanonicalName();
    private static final String LONG_CANONICAL_NAME_2 = long.class.getCanonicalName();
    private static final String BOOLEAN_CANONICAL_NAME_1 = Boolean.class.getCanonicalName();
    private static final String BOOLEAN_CANONICAL_NAME_2 = boolean.class.getCanonicalName();

    /**--------------------- 序列化类型 --------------------------**/
    private static SerializeType serializeTypeConfigInThisServer = SerializeType.JSON;
    static {
        // 从环境中加载序列化协议
        // 略...
        serializeTypeConfigInThisServer = SerializeType.HMQ;
    }



    /** --------------定义协议头--------------**/
    // 请求类型编号(类比web服务中的Controller中的某个接口)
    private int code;
    // 版本号
    private int version = 0;
    // 请求标识
    private int opaque = requestId.getAndIncrement();
    // 类型标记，倒数第一位表示请求类型，0：请求，1：返回  默认请求
    private int flag = 0;
    private String remark;
    // 扩展协议头：供自定义头，
    // 抽取CommandCustomHeader子类对象的key-value进行填充
    private HashMap<String, String> extFields;

    // 加关键字transient，序列化时，该属性不会被序列化
    private transient CommandCustomHeader customHeader;
    // 当前RPC通信使用的序列化类型
    private SerializeType serializeTypeCurrentRPC = serializeTypeConfigInThisServer;

    /** ------ 协议体，传输具体的通信内容---------**/
    private transient byte[] body;

    // 构造函数
    protected RemotingCommand() {
    }


    /** -------定义静态方法，用于创建Request或者Response类型的RemotingCommand-------*/
    public static RemotingCommand createRequestCommand(int code, CommandCustomHeader customHeader) {
        RemotingCommand cmd = new RemotingCommand();
        cmd.setCode(code);
        cmd.customHeader = customHeader;
        setCmdVersion(cmd);
        return cmd;
    }

    private static void setCmdVersion(RemotingCommand cmd) {
        if (configVersion >= 0) {
            cmd.setVersion(configVersion);
        } else {
            String v = System.getProperty(REMOTING_VERSION_KEY);
            if (v != null) {
                int value = Integer.parseInt(v);
                cmd.setVersion(value);
                configVersion = value;
            }
        }
    }

    public static RemotingCommand createResponseCommand(Class<? extends CommandCustomHeader> classHeader) {
        return createResponseCommand(RemotingSysResponseCode.SYSTEM_ERROR, "not set any response code", classHeader);
    }

    public static RemotingCommand createResponseCommand(int code, String remark) {
        return createResponseCommand(code, remark, null);
    }

    public static RemotingCommand createResponseCommand(int code, String remark,
                                                        Class<? extends CommandCustomHeader> classHeader) {
        RemotingCommand cmd = new RemotingCommand();
        cmd.markResponseType();
        cmd.setCode(code);
        cmd.setRemark(remark);
        setCmdVersion(cmd);
        if (classHeader != null) {
            try {
                CommandCustomHeader objectHeader = classHeader.newInstance();
                cmd.customHeader = objectHeader;
            } catch (InstantiationException e) {
                return null;
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return cmd;
    }

    public void markResponseType() {
        int bits = 1 << RPC_TYPE;
        this.flag |= bits;
    }


    /**-----------------------序列化--------------------------**/
    public ByteBuffer encode() {
        // markProtocolType
        int length = 4;

        // 协议头的数据信息长度
        byte[] headerData = this.headerEncode();
        length += headerData.length;

        // 协议体的长度
        if (this.body != null) {
            length += body.length;
        }

        ByteBuffer result = ByteBuffer.allocate(4 + length);

        // RemotingCommand序列化后的总长度
        result.putInt(length);
        // 头的长度信息，占4字节
        result.put(markProtocolType(headerData.length, serializeTypeCurrentRPC));
        // 头的数据信息
        result.put(headerData);

        // body数据信息
        if (this.body != null) {
            result.put(body);
        }

        result.flip();
        return result;
    }

    /**
     * 协议头序列化编码
     * 协议头中包含：
     * int code
     * int version
     * int opaque
     * int flag
     * String remark
     * HashMap<String, String> extFields
     * @return
     */
    private byte[] headerEncode() {
        this.makeCustomHeaderToNet();
        return HMQSerializable.hMQProtocolEncode(this);
    }

    /**
     * 抽取不同的CommandCustomHeader子类的实例对象的字段(排除静态的及以this开头属性)
     * 对应的key-value填充到extField准备进行网络传输:
     * 1.从缓存中获取CommandCustomHeader子类的所有Field，若缓存中没有，通过Class解析
     * 2.抽取不同的CommandCustomHeader子类的实例对象的字段(排除静态的及以this开头属性)对应的key-value
     * 3.将Field对象的key,value存储在extFields中
     */
    public void makeCustomHeaderToNet() {
        if (this.customHeader != null) {
            // 先从缓存中取，CommandCustomHeader子类的所有字段
            Field[] fields = getClazzFields(customHeader.getClass());
            if (null == this.extFields) {
                this.extFields = new HashMap<String, String>();
            }

            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {// 排除静态属性
                    String name = field.getName();
                    if (!name.startsWith("this")) {// 排除以this开头的属性
                        Object value = null;
                        try {
                            field.setAccessible(true);
                            value = field.get(this.customHeader);
                        } catch (Exception e) {

                        }

                        if (value != null) {
                            this.extFields.put(name, value.toString());
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取某个CommandCustomHeader子类的所有Field
     * 1.先从缓存中取
     * 2.缓存中没有，通过反射获取，添加进缓存
     * @param classHeader
     * @return
     */
    private Field[] getClazzFields(Class<? extends CommandCustomHeader> classHeader) {
        Field[] field = CLASS_HASH_MAP.get(classHeader);

        if (field == null) {
            field = classHeader.getDeclaredFields();
            synchronized (CLASS_HASH_MAP) {// 可能多线程操作共享变量CLASS_HASH_MAP，加锁同步保证线程安全
                CLASS_HASH_MAP.put(classHeader, field);
            }
        }
        return field;
    }

    public ByteBuffer encodeHeader() {
        return encodeHeader(this.body != null ? this.body.length : 0);
    }

    public ByteBuffer encodeHeader(final int bodyLength) {
        int length = 4;

        byte[] headerData;
        headerData = this.headerEncode();

        length += headerData.length;

        length += bodyLength;

        ByteBuffer result = ByteBuffer.allocate(4 + length - bodyLength);

        result.putInt(length);
        result.put(markProtocolType(headerData.length, serializeTypeCurrentRPC));
        result.put(headerData);

        result.flip();

        return result;
    }

    /**
     * 标记协议类型，占4字节
     * @param source
     * @param type
     * @return
     */
    public static byte[] markProtocolType(int source, SerializeType type) {
        byte[] result = new byte[4];

        result[0] = type.getCode();
        result[1] = (byte) ((source >> 16) & 0xFF);
        result[2] = (byte) ((source >> 8) & 0xFF);
        result[3] = (byte) (source & 0xFF);
        return result;
    }


    /** --------------------------反序列化----------------------------**/
    public static RemotingCommand decode(final byte[] array) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        return decode(byteBuffer);
    }

    public static RemotingCommand decode(final ByteBuffer byteBuffer) {
        int length = byteBuffer.limit();
        int oriHeaderLen = byteBuffer.getInt(); // 4
        int headerLength = getHeaderLength(oriHeaderLen);

        byte[] headerData = new byte[headerLength];
        byteBuffer.get(headerData);
        RemotingCommand cmd = headerDecode(headerData, getProtocolType(oriHeaderLen));

        // 获取消息体
        int bodyLength = length - 4 - headerLength;
        byte[] bodyData = null;
        if (bodyLength > 0) {
            bodyData = new byte[bodyLength];
            byteBuffer.get(bodyData);
        }
        cmd.body = bodyData;
        return cmd;
    }

    public static int getHeaderLength(int length) {
        return length & 0xFFFFFF;
    }

    public static SerializeType getProtocolType(int source) {
        return SerializeType.valueOf((byte) ((source >> 24) & 0xFF));
    }

    /**
     * 协议头解析
     * @param headerData
     * @param type
     * @return
     */
    private static RemotingCommand headerDecode(byte[] headerData, SerializeType type) {
        switch (type) {
            case HMQ:
                RemotingCommand cmd = HMQSerializable.hMQProtocolDecode(headerData);
                cmd.setSerializeTypeCurrentRPC(type);
                return cmd;
            default:
                    break;
        }
        return null;
    }

    /**
     * 反序列化自定义协议头
     * @param classHeader
     * @return
     */
    public CommandCustomHeader decodeCommandCustomHeader(
            Class<? extends CommandCustomHeader> classHeader) throws RemotingCommandException {
        CommandCustomHeader objectHeader;
        try {
            objectHeader = classHeader.newInstance();
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }

        if (this.extFields != null) {

            Field[] fields = getClazzFields(classHeader);
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    String fieldName = field.getName();
                    if (!fieldName.startsWith("this")) {
                        try {
                            String value = this.extFields.get(fieldName);
                            if (null == value) {
                                if (!isFieldNullable(field)) {
                                    throw new RemotingCommandException("the custom field <" + fieldName + "> is null");
                                }
                                continue;
                            }

                            field.setAccessible(true);
                            String type = getCanonicalName(field.getType());
                            Object valueParsed;

                            if (type.equals(STRING_CANONICAL_NAME)) {
                                valueParsed = value;
                            } else if (type.equals(INTEGER_CANONICAL_NAME_1) || type.equals(INTEGER_CANONICAL_NAME_2)) {
                                valueParsed = Integer.parseInt(value);
                            } else if (type.equals(LONG_CANONICAL_NAME_1) || type.equals(LONG_CANONICAL_NAME_2)) {
                                valueParsed = Long.parseLong(value);
                            } else if (type.equals(BOOLEAN_CANONICAL_NAME_1) || type.equals(BOOLEAN_CANONICAL_NAME_2)) {
                                valueParsed = Boolean.parseBoolean(value);
                            } else if (type.equals(DOUBLE_CANONICAL_NAME_1) || type.equals(DOUBLE_CANONICAL_NAME_2)) {
                                valueParsed = Double.parseDouble(value);
                            } else {
                                throw new RemotingCommandException("the custom field <" + fieldName + "> type is not supported");
                            }

                            field.set(objectHeader, valueParsed);

                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            objectHeader.checkFields();
        }

        return objectHeader;
    }

    /**
     * 字段是否允许为空
     * @param field
     * @return
     */
    private boolean isFieldNullable(Field field) {
        if (!NULLABLE_FIELD_CACHE.containsKey(field)) {
            Annotation annotation = field.getAnnotation(CFNotNull.class);
            synchronized (NULLABLE_FIELD_CACHE) {
                NULLABLE_FIELD_CACHE.put(field, annotation == null);
            }
        }
        return NULLABLE_FIELD_CACHE.get(field);
    }

    private String getCanonicalName(Class clazz) {
        String name = CANONICAL_NAME_CACHE.get(clazz);

        if (name == null) {
            name = clazz.getCanonicalName();
            synchronized (CANONICAL_NAME_CACHE) {
                CANONICAL_NAME_CACHE.put(clazz, name);
            }
        }
        return name;
    }

    public RemotingCommandType getType() {
        if (this.isResponseType()) {
            return RemotingCommandType.RESPONSE_COMMAND;
        }

        return RemotingCommandType.REQUEST_COMMAND;
    }

    public boolean isResponseType() {
        int bits = 1 << RPC_TYPE;
        return (this.flag & bits) == bits;
    }

    public CommandCustomHeader readCustomHeader() {
        return customHeader;
    }

    public void writeCustomHeader(CommandCustomHeader customHeader) {
        this.customHeader = customHeader;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getOpaque() {
        return opaque;
    }

    public void setOpaque(int opaque) {
        this.opaque = opaque;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public HashMap<String, String> getExtFields() {
        return extFields;
    }

    public void setExtFields(HashMap<String, String> extFields) {
        this.extFields = extFields;
    }

    public CommandCustomHeader getCustomHeader() {
        return customHeader;
    }

    public void setCustomHeader(CommandCustomHeader customHeader) {
        this.customHeader = customHeader;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public static String getRemotingVersionKey() {
        return REMOTING_VERSION_KEY;
    }

    public SerializeType getSerializeTypeCurrentRPC() {
        return serializeTypeCurrentRPC;
    }

    public void setSerializeTypeCurrentRPC(SerializeType serializeTypeCurrentRPC) {
        this.serializeTypeCurrentRPC = serializeTypeCurrentRPC;
    }
}
