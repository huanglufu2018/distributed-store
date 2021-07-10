package com.hlf.rpc;

import com.hlf.rpc.netty.ResponseFuture;

public interface InvokeCallback {

    void operationComplete(final ResponseFuture responseFuture);
}
