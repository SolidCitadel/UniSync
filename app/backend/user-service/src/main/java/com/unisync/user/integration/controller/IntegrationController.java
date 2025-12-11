package com.unisync.user.integration.controller;

import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.service.CredentialsService;
import com.unisync.user.integration.dto.IntegrationStatusResponse;
import com.unisync.user.integration.service.IntegrationStatusService;
import com.unisync.user.sync.dto.CanvasSyncResponse;
import com.unisync.user.sync.service.CanvasSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * 외부 서비스 연동 통합 API
 * - 통합 상태 조회
 * - Canvas 토큰 관리
 * - Canvas 동기화
 */
@Slf4j
@RestController
@RequestMapping("/v1/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "외부 서비스 연동 API (Canvas, Google Calendar 등)")
public class IntegrationController {

    private final IntegrationStatusService integrationStatusService;
    private final CredentialsService credentialsService;
    private final CanvasSyncService canvasSyncService;

    /**
     * 사용자의 외부 서비스 통합 상태 조회
     */
    @GetMapping("/status")
    @Operation(summary = "통합 상태 조회", description = "Canvas, Google Calendar 등의 통합 상태를 조회합니다.")
    public ResponseEntity<IntegrationStatusResponse> getIntegrationStatus(
            @Parameter(hidden = true) @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        log.info("GET /v1/integrations/status - Cognito Sub: {}", cognitoSub);
        IntegrationStatusResponse status = integrationStatusService.getIntegrationStatus(cognitoSub);
        return ResponseEntity.ok(status);
    }

    /**
     * Canvas 토큰 등록
     */
    @PostMapping("/canvas/credentials")
    @Operation(summary = "Canvas 토큰 등록", description = "사용자의 Canvas API 토큰을 등록합니다.")
    public ResponseEntity<RegisterCanvasTokenResponse> registerCanvasToken(
            @Parameter(hidden = true) @RequestHeader(value = "X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody RegisterCanvasTokenRequest request
    ) {
        log.info("POST /v1/integrations/canvas/credentials - Cognito Sub: {}", cognitoSub);
        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(cognitoSub, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 조회
     */
    @GetMapping("/canvas/credentials")
    @Operation(summary = "Canvas 토큰 조회", description = "본인의 Canvas 토큰 정보를 조회합니다.")
    public ResponseEntity<CanvasTokenResponse> getCanvasToken(
            @Parameter(hidden = true) @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        log.info("GET /v1/integrations/canvas/credentials - Cognito Sub: {}", cognitoSub);
        CanvasTokenResponse response = credentialsService.getCanvasTokenByCognitoSub(cognitoSub);
        return ResponseEntity.ok(response);
    }

    /**
     * Canvas 토큰 삭제
     */
    @DeleteMapping("/canvas/credentials")
    @Operation(summary = "Canvas 토큰 삭제", description = "사용자의 Canvas API 토큰을 삭제합니다.")
    public ResponseEntity<Void> deleteCanvasToken(
            @Parameter(hidden = true) @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        log.info("DELETE /v1/integrations/canvas/credentials - Cognito Sub: {}", cognitoSub);
        credentialsService.deleteCanvasToken(cognitoSub);
        return ResponseEntity.noContent().build();
    }

    /**
     * Canvas 수동 동기화 시작
     */
    @PostMapping("/canvas/sync")
    @Operation(summary = "Canvas 동기화", description = "Canvas 과목/과제를 동기화합니다. syncMode=courses|assignments")
    public ResponseEntity<CanvasSyncResponse> syncCanvas(
        @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
        @RequestParam(name = "mode", defaultValue = "assignments") String syncMode
    ) {
        if (!Set.of("courses", "assignments").contains(syncMode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sync mode: " + syncMode);
        }
        log.info("POST /v1/integrations/canvas/sync - Cognito Sub: {}, mode={}", cognitoSub, syncMode);
        CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub, syncMode);
        log.info("Canvas sync response: {} courses, {} assignments",
                response.getCoursesCount(),
                response.getAssignmentsCount());
        return ResponseEntity.ok(response);
    }
}
