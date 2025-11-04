package com.unisync.user.credentials.exception;

public class InvalidCanvasTokenException extends RuntimeException {

    public InvalidCanvasTokenException(String message) {
        super(message);
    }

    public InvalidCanvasTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}