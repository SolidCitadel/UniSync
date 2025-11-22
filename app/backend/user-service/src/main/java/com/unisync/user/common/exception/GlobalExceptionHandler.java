package com.unisync.user.common.exception;

import com.unisync.shared.security.exception.UnauthorizedException;
import com.unisync.user.auth.exception.AuthenticationException;
import com.unisync.user.auth.exception.DuplicateUserException;
import com.unisync.user.auth.exception.InvalidCredentialsException;
import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.credentials.exception.CanvasTokenNotFoundException;
import com.unisync.user.credentials.exception.InvalidCanvasTokenException;
import com.unisync.user.friend.exception.FriendshipAlreadyExistsException;
import com.unisync.user.friend.exception.FriendshipNotFoundException;
import com.unisync.user.friend.exception.SelfFriendshipException;
import com.unisync.user.group.exception.GroupNotFoundException;
import com.unisync.user.group.exception.InsufficientPermissionException;
import com.unisync.user.group.exception.MemberAlreadyExistsException;
import com.unisync.user.group.exception.MemberNotFoundException;
import com.unisync.user.sync.exception.CanvasSyncException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * Canvas 동기화 실패 예외 처리
     */
    @ExceptionHandler(CanvasSyncException.class)
    public ResponseEntity<ErrorResponse> handleCanvasSyncError(CanvasSyncException e) {
        log.error("Canvas 동기화 실패: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("CANVAS_SYNC_ERROR", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * 친구 관계를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(FriendshipNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFriendshipNotFound(FriendshipNotFoundException e) {
        log.error("친구 관계를 찾을 수 없음: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("FRIENDSHIP_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 이미 친구 관계가 존재함 예외 처리
     */
    @ExceptionHandler(FriendshipAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleFriendshipAlreadyExists(FriendshipAlreadyExistsException e) {
        log.error("이미 친구 관계가 존재함: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("FRIENDSHIP_ALREADY_EXISTS", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * 자기 자신에게 친구 요청 예외 처리
     */
    @ExceptionHandler(SelfFriendshipException.class)
    public ResponseEntity<ErrorResponse> handleSelfFriendship(SelfFriendshipException e) {
        log.error("자기 자신에게 친구 요청: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("SELF_FRIENDSHIP", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 그룹을 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGroupNotFound(GroupNotFoundException e) {
        log.error("그룹을 찾을 수 없음: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("GROUP_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 그룹 멤버를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMemberNotFound(MemberNotFoundException e) {
        log.error("멤버를 찾을 수 없음: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("MEMBER_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 이미 그룹 멤버임 예외 처리
     */
    @ExceptionHandler(MemberAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleMemberAlreadyExists(MemberAlreadyExistsException e) {
        log.error("이미 그룹 멤버임: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("MEMBER_ALREADY_EXISTS", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * 권한 부족 예외 처리
     */
    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPermission(InsufficientPermissionException e) {
        log.error("권한 부족: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("INSUFFICIENT_PERMISSION", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * 잘못된 인자 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.error("잘못된 인자: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("BAD_REQUEST", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 존재하지 않는 리소스/엔드포인트 처리 (404)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        log.error("존재하지 않는 리소스: {} {}", e.getHttpMethod(), e.getResourcePath());
        ErrorResponse errorResponse = new ErrorResponse("NOT_FOUND", "요청한 API를 찾을 수 없습니다: " + e.getHttpMethod() + " " + e.getResourcePath());
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
