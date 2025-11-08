package com.unisync.schedule.todos.exception;

public class TodoNotFoundException extends RuntimeException {
    public TodoNotFoundException(String message) {
        super(message);
    }

    public TodoNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
