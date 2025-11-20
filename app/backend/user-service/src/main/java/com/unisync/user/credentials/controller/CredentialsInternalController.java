package com.unisync.user.credentials.controller;

import com.unisync.shared.security.ServiceAuthValidator;
import com.unisync.user.credentials.dto.InternalCanvasTokenResponse;
import com.unisync.user.credentials.service.CredentialsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 내부 API용 Credentials Controller
 * Lambda 및 다른 마이크로서비스에서 호출하는 API
 *
 * 인증: X-Api-Key 헤더 (ServiceAuthValidator)
 * 경로: /internal/v1/credentials/**
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/credentials")
@RequiredArgsConstructor
@Tag(name = "Internal Credentials API", description = "내부 서비스 간 Credentials 조회 API (X-Api-Key 인증)")
public class CredentialsInternalController {

    private final CredentialsService credentialsService;
    private final ServiceAuthValidator serviceAuthValidator;

    /**
     * Canvas 토큰 조회 (내부 API - Lambda용)
     * Lambda가 cognitoSub로 Canvas 토큰을 조회할 때 사용합니다.
     *
     * 외부 API와 달리 복호화된 토큰과 추가 메타데이터를 반환합니다.
     *
     * @param cognitoSub Cognito Sub (사용자 식별자)
     * @param apiKey     내부 서비스 API Key (X-Api-Key 헤더)
     * @return 복호화된 Canvas 토큰 + 메타데이터
     */
    @GetMapping("/canvas/by-cognito-sub/{cognitoSub}")
    @Operation(
            summary = "Canvas 토큰 조회 (내부 API - cognitoSub)",
            description = "Lambda 및 내부 서비스가 cognitoSub로 Canvas 토큰을 조회합니다. X-Api-Key 인증 필요."
    )
    public ResponseEntity<InternalCanvasTokenResponse> getCanvasTokenByCognitoSub(
            @PathVariable String cognitoSub,
            @RequestHeader("X-Api-Key") String apiKey
    ) {
        log.info("[Internal API] GET /internal/v1/credentials/canvas/by-cognito-sub/{}", cognitoSub);

        // API Key 검증
        String caller = serviceAuthValidator.validateAndGetCaller(apiKey);
        log.info("Internal API called by service: {}", caller);

        InternalCanvasTokenResponse response = credentialsService.getCanvasTokenForInternalApi(cognitoSub);

        return ResponseEntity.ok(response);
    }
}
