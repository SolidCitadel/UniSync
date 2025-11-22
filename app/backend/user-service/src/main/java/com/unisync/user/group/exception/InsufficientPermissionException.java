package com.unisync.user.group.exception;

/**
 * 권한이 부족할 때 발생
 */
public class InsufficientPermissionException extends RuntimeException {

    public InsufficientPermissionException(String message) {
        super(message);
    }

    public InsufficientPermissionException() {
        super("Insufficient permissions for this operation");
    }
}
