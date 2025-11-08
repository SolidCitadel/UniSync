package com.unisync.schedule.categories.exception;

public class DuplicateCategoryException extends RuntimeException {
    public DuplicateCategoryException(String message) {
        super(message);
    }

    public DuplicateCategoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
