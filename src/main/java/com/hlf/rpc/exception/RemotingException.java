package com.hlf.rpc.exception;

public class RemotingException extends Exception {

    private static final long serialVersionUID = -5690687334570505110L;

    public RemotingException(String message) {
        super(message);
    }

    public RemotingException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
