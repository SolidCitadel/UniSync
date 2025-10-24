package com.unisync.user.auth.controller;

import com.unisync.user.auth.dto.AuthResponse;
import com.unisync.user.auth.dto.SignInRequest;
import com.unisync.user.auth.dto.SignUpRequest;
import com.unisync.user.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "인증 API", description = "회원가입, 로그인 등 인증 관련 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "Cognito를 사용한 회원가입. 이메일, 비밀번호, 이름 필수")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpRequest request) {
        try {
            log.info("회원가입 요청: email={}", request.getEmail());
            AuthResponse response = authService.signUp(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            log.error("회원가입 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/signin")
    @Operation(summary = "로그인", description = "Cognito를 사용한 로그인. JWT 토큰 반환")
    public ResponseEntity<?> signIn(@Valid @RequestBody SignInRequest request) {
        try {
            log.info("로그인 요청: email={}", request.getEmail());
            AuthResponse response = authService.signIn(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("로그인 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "Access Token으로 현재 로그인한 사용자 정보 조회")
    public ResponseEntity<?> getMe(@RequestHeader("Authorization") String authorization) {
        try {
            String accessToken = extractToken(authorization);
            var user = authService.getUserByAccessToken(accessToken);

            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("isActive", user.getIsActive());
            response.put("createdAt", user.getCreatedAt());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("사용자 정보 조회 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "헬스체크", description = "User Service 상태 확인")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "user-service");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    // Utility methods

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization 헤더가 없거나 형식이 올바르지 않습니다");
        }
        return authorization.substring(7);
    }

    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
