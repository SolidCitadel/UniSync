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
@RequestMapping("/api/v1/credentials")
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
            @RequestHeader(value = "X-User-Id") Long userId,
            @Valid @RequestBody RegisterCanvasTokenRequest request
    ) {
        log.info("POST /api/v1/credentials/canvas - User ID: {}", userId);

        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 조회 (내부 API용)
     * Lambda/Service가 사용자의 Canvas 토큰을 조회할 때 사용합니다.
     */
    @GetMapping("/{userId}/canvas")
    @Operation(summary = "Canvas 토큰 조회 (내부 API)", description = "사용자의 Canvas 토큰을 조회합니다. 서비스 간 호출용입니다.")
    public ResponseEntity<CanvasTokenResponse> getCanvasToken(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long requestUserId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey
    ) {
        log.info("GET /api/v1/credentials/{}/canvas", userId);

        // 권한 검증: 본인 또는 서비스
        if (requestUserId != null) {
            // 외부 요청 (Gateway 경유)
            if (!requestUserId.equals(userId)) {
                throw new UnauthorizedException("Cannot access other user's credentials");
            }
        } else if (apiKey != null) {
            // 내부 요청 (서비스 간 호출)
            String caller = serviceAuthValidator.validateAndGetCaller(apiKey);
            log.info("Internal API called by service: {}", caller);
        } else {
            throw new UnauthorizedException("Missing authentication headers");
        }

        CanvasTokenResponse response = credentialsService.getCanvasToken(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 삭제 (사용자용)
     */
    @DeleteMapping("/canvas")
    @Operation(summary = "Canvas 토큰 삭제", description = "사용자의 Canvas API 토큰을 삭제합니다.")
    public ResponseEntity<Void> deleteCanvasToken(
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        log.info("DELETE /api/v1/credentials/canvas - User ID: {}", userId);

        credentialsService.deleteCanvasToken(userId);

        return ResponseEntity.noContent().build();
    }
}