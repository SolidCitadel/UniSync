package com.unisync.schedule.schedules.exception;

public class InvalidScheduleException extends RuntimeException {
    public InvalidScheduleException(String message) {
        super(message);
    }

    public InvalidScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}
