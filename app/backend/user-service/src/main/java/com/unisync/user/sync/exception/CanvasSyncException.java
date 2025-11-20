package com.unisync.user.sync.exception;

/**
 * Canvas 동기화 실패 예외
 */
public class CanvasSyncException extends RuntimeException {

    public CanvasSyncException(String message) {
        super(message);
    }

    public CanvasSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
