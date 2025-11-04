package com.unisync.user.common.exception;

import com.unisync.shared.security.exception.UnauthorizedException;
import com.unisync.user.auth.exception.AuthenticationException;
import com.unisync.user.auth.exception.DuplicateUserException;
import com.unisync.user.auth.exception.InvalidCredentialsException;
import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.credentials.exception.CanvasTokenNotFoundException;
import com.unisync.user.credentials.exception.InvalidCanvasTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 중복 사용자 예외 처리
     */
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUser(DuplicateUserException e) {
        log.error("중복 사용자 에러: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("DUPLICATE_USER", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * 잘못된 인증 정보 예외 처리
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        log.error("잘못된 인증 정보: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("INVALID_CREDENTIALS", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * 사용자를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        log.error("사용자를 찾을 수 없음: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("USER_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        log.error("인증 에러: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("AUTHENTICATION_ERROR", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * 권한 없음 예외 처리 (서비스 간 API Key 검증 실패)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        log.error("권한 없음: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("UNAUTHORIZED", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * 잘못된 Canvas 토큰 예외 처리
     */
    @ExceptionHandler(InvalidCanvasTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCanvasToken(InvalidCanvasTokenException e) {
        log.error("잘못된 Canvas 토큰: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("INVALID_CANVAS_TOKEN", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Canvas 토큰을 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(CanvasTokenNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCanvasTokenNotFound(CanvasTokenNotFoundException e) {
        log.error("Canvas 토큰을 찾을 수 없음: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("CANVAS_TOKEN_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * @Valid 검증 실패 시 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        response.put("status", "error");
        response.put("message", "입력값 검증에 실패했습니다");
        response.put("errors", errors);

        log.warn("Validation 실패: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 모든 예외 처리 (최종 catch-all)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        log.error("예상치 못한 예외 발생: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
