package com.unisync.shared.security.exception;

/**
 * API Key 검증 실패 또는 인증되지 않은 요청에 대한 예외
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}