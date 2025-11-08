package com.unisync.schedule.schedules.exception;

public class ScheduleNotFoundException extends RuntimeException {
    public ScheduleNotFoundException(String message) {
        super(message);
    }

    public ScheduleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
