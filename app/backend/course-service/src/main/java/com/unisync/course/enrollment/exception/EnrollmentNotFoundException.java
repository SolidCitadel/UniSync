package com.unisync.course.enrollment.exception;

/**
 * Enrollment을 찾을 수 없을 때 발생하는 예외
 */
public class EnrollmentNotFoundException extends RuntimeException {
    public EnrollmentNotFoundException(String message) {
        super(message);
    }
}
