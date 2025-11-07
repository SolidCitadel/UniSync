package com.unisync.user.credentials.controller;

import com.unisync.shared.security.ServiceAuthValidator;
import com.unisync.shared.security.exception.UnauthorizedException;
import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.service.CredentialsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/credentials")
@RequiredArgsConstructor
@Tag(name = "Credentials", description = "외부 서비스 인증 정보 관리 API")
public class CredentialsController {

    private final CredentialsService credentialsService;
    private final ServiceAuthValidator serviceAuthValidator;

    /**
     * Canvas 토큰 등록 (사용자용)
     */
    @PostMapping("/canvas")
    @Operation(summary = "Canvas 토큰 등록", description = "사용자의 Canvas API 토큰을 등록합니다.")
    public ResponseEntity<RegisterCanvasTokenResponse> registerCanvasToken(
            @RequestHeader(value = "X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody RegisterCanvasTokenRequest request
    ) {
        log.info("POST /credentials/canvas - Cognito Sub: {}", cognitoSub);

        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(cognitoSub, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 조회 (내부 API용 - Lambda 호환성)
     * Lambda가 userId로 Canvas 토큰을 조회할 때 사용합니다.
     * @deprecated Lambda가 cognitoSub를 사용하도록 마이그레이션 예정
     */
    @GetMapping("/{userId}/canvas")
    @Operation(summary = "Canvas 토큰 조회 (내부 API - Legacy)", description = "사용자의 Canvas 토큰을 조회합니다. 서비스 간 호출용입니다.")
    @Deprecated
    public ResponseEntity<CanvasTokenResponse> getCanvasTokenByUserId(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey
    ) {
        log.info("GET /credentials/{}/canvas (Legacy userId API)", userId);

        // API Key 검증
        if (apiKey != null) {
            String caller = serviceAuthValidator.validateAndGetCaller(apiKey);
            log.info("Internal API called by service: {}", caller);
        } else {
            throw new UnauthorizedException("Missing X-Api-Key header");
        }

        CanvasTokenResponse response = credentialsService.getCanvasToken(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 조회 (cognitoSub 기반 - 사용자용)
     */
    @GetMapping("/canvas")
    @Operation(summary = "Canvas 토큰 조회", description = "본인의 Canvas 토큰 정보를 조회합니다.")
    public ResponseEntity<CanvasTokenResponse> getCanvasToken(
            @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        log.info("GET /credentials/canvas - Cognito Sub: {}", cognitoSub);

        CanvasTokenResponse response = credentialsService.getCanvasTokenByCognitoSub(cognitoSub);

        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 조회 (cognitoSub 기반 - 내부 API용)
     * Lambda가 cognitoSub로 Canvas 토큰을 조회할 때 사용합니다.
     */
    @GetMapping("/canvas/by-cognito-sub/{cognitoSub}")
    @Operation(summary = "Canvas 토큰 조회 (내부 API - cognitoSub)", description = "cognitoSub로 Canvas 토큰을 조회합니다. 서비스 간 호출용입니다.")
    public ResponseEntity<CanvasTokenResponse> getCanvasTokenByCognitoSub(
            @PathVariable String cognitoSub,
            @RequestHeader(value = "X-Api-Key") String apiKey
    ) {
        log.info("GET /credentials/canvas/by-cognito-sub/{} (Internal API)", cognitoSub);

        // API Key 검증
        String caller = serviceAuthValidator.validateAndGetCaller(apiKey);
        log.info("Internal API called by service: {}", caller);

        CanvasTokenResponse response = credentialsService.getCanvasTokenByCognitoSub(cognitoSub);

        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 삭제 (사용자용)
     */
    @DeleteMapping("/canvas")
    @Operation(summary = "Canvas 토큰 삭제", description = "사용자의 Canvas API 토큰을 삭제합니다.")
    public ResponseEntity<Void> deleteCanvasToken(
            @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        log.info("DELETE /credentials/canvas - Cognito Sub: {}", cognitoSub);

        credentialsService.deleteCanvasToken(cognitoSub);

        return ResponseEntity.noContent().build();
    }
}