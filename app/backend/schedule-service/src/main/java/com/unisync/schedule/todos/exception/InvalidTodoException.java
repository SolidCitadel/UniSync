package com.unisync.schedule.todos.exception;

public class InvalidTodoException extends RuntimeException {
    public InvalidTodoException(String message) {
        super(message);
    }

    public InvalidTodoException(String message, Throwable cause) {
        super(message, cause);
    }
}
