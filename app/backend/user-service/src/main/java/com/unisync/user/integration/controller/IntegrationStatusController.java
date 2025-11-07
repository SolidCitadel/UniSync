package com.unisync.user.integration.controller;

import com.unisync.user.integration.dto.IntegrationStatusResponse;
import com.unisync.user.integration.service.IntegrationStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 서비스 연동 상태 조회 API
 */
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
@Tag(name = "Integration Status", description = "외부 연동 상태 조회 API")
public class IntegrationStatusController {

    private final IntegrationStatusService integrationStatusService;

    /**
     * 사용자의 외부 서비스 연동 상태 조회
     *
     * @param cognitoSub Cognito 사용자 ID (X-Cognito-Sub 헤더)
     * @return 연동 상태 정보
     */
    @GetMapping("/status")
    @Operation(summary = "연동 상태 조회", description = "Canvas, Google Calendar 등의 연동 상태를 조회합니다.")
    public ResponseEntity<IntegrationStatusResponse> getIntegrationStatus(
            @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        IntegrationStatusResponse status = integrationStatusService.getIntegrationStatus(cognitoSub);
        return ResponseEntity.ok(status);
    }
}