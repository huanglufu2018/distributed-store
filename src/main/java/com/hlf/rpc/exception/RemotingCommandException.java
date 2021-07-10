package com.hlf.rpc.exception;

public class RemotingCommandException extends RemotingException {

    private static final long serialVersionUID = -6061365915274953096L;

    public RemotingCommandException(String message) {
        super(message, null);
    }

    public RemotingCommandException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
