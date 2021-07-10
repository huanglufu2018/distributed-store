package com.hlf.rpc;

public interface RemotingService {

    void start();

    void shutdown();

    void registerRPCHook(RPCHook rpcHook);
}
