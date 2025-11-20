package com.unisync.user.credentials.controller;

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
@RequestMapping("/v1/credentials")
@RequiredArgsConstructor
@Tag(name = "Credentials", description = "외부 서비스 인증 정보 관리 API (프론트엔드용)")
public class CredentialsController {

    private final CredentialsService credentialsService;

    /**
     * Canvas 토큰 등록 (사용자용)
     */
    @PostMapping("/canvas")
    @Operation(summary = "Canvas 토큰 등록", description = "사용자의 Canvas API 토큰을 등록합니다.")
    public ResponseEntity<RegisterCanvasTokenResponse> registerCanvasToken(
            @RequestHeader(value = "X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody RegisterCanvasTokenRequest request
    ) {
        log.info("POST /v1/credentials/canvas - Cognito Sub: {}", cognitoSub);

        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(cognitoSub, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 조회 (사용자용)
     */
    @GetMapping("/canvas")
    @Operation(summary = "Canvas 토큰 조회", description = "본인의 Canvas 토큰 정보를 조회합니다.")
    public ResponseEntity<CanvasTokenResponse> getCanvasToken(
            @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        log.info("GET /v1/credentials/canvas - Cognito Sub: {}", cognitoSub);

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