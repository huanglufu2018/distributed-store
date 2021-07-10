package com.hlf.store;

public enum AppendMessageStatus {

    PUT_OK,// 追加成功
    END_OF_FILE,// 超过文件大小
    MESSAGE_SIZE_EXCEEDED,// 消息长度，超过最大允许长度
    PROPERTIES_SIZE_EXCEEDED,// 消息属性超过最大允许长度
    UNKNOWN_ERROR// 未知异常
}
