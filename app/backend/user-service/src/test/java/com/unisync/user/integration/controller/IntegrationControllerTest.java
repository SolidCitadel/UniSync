package com.unisync.user.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.service.CredentialsService;
import com.unisync.user.integration.dto.IntegrationInfo;
import com.unisync.user.integration.dto.IntegrationStatusResponse;
import com.unisync.user.integration.service.IntegrationStatusService;
import com.unisync.user.sync.dto.CanvasSyncResponse;
import com.unisync.user.sync.service.CanvasSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IntegrationController.class)
@DisplayName("IntegrationController 단위 테스트")
class IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntegrationStatusService integrationStatusService;

    @MockBean
    private CredentialsService credentialsService;

    @MockBean
    private CanvasSyncService canvasSyncService;

    private static final String COGNITO_SUB = "test-cognito-sub-123";

    @Test
    @DisplayName("GET /v1/integrations/status - 연동 상태 조회 성공")
    void getIntegrationStatus_Success() throws Exception {
        // Given
        IntegrationInfo canvasInfo = IntegrationInfo.builder()
                .isConnected(true)
                .lastValidatedAt(LocalDateTime.now())
                .externalUsername("testuser")
                .build();

        IntegrationStatusResponse response = IntegrationStatusResponse.builder()
                .canvas(canvasInfo)
                .build();

        given(integrationStatusService.getIntegrationStatus(COGNITO_SUB))
                .willReturn(response);

        // When & Then
        mockMvc.perform(get("/v1/integrations/status")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvas").exists())
                .andExpect(jsonPath("$.canvas.isConnected").value(true));

        then(integrationStatusService).should().getIntegrationStatus(COGNITO_SUB);
    }

    @Test
    @DisplayName("POST /v1/integrations/canvas/credentials - Canvas 토큰 등록 성공")
    void registerCanvasToken_Success() throws Exception {
        // Given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken("test-canvas-token")
                .build();

        RegisterCanvasTokenResponse response = RegisterCanvasTokenResponse.builder()
                .success(true)
                .message("Canvas token registered successfully")
                .build();

        given(credentialsService.registerCanvasToken(anyString(), any(RegisterCanvasTokenRequest.class)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/integrations/canvas/credentials")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Canvas token registered successfully"));

        then(credentialsService).should().registerCanvasToken(anyString(), any(RegisterCanvasTokenRequest.class));
    }

    @Test
    @DisplayName("POST /v1/integrations/canvas/credentials - 유효성 검증 실패 (토큰 없음)")
    void registerCanvasToken_ValidationFailure() throws Exception {
        // Given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(null) // 필수 값 누락
                .build();

        // When & Then
        mockMvc.perform(post("/v1/integrations/canvas/credentials")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/integrations/canvas/credentials - Canvas 토큰 조회 성공")
    void getCanvasToken_Success() throws Exception {
        // Given
        CanvasTokenResponse response = CanvasTokenResponse.builder()
                .canvasToken("decrypted-canvas-token")
                .lastValidatedAt(LocalDateTime.now())
                .build();

        given(credentialsService.getCanvasTokenByCognitoSub(COGNITO_SUB))
                .willReturn(response);

        // When & Then
        mockMvc.perform(get("/v1/integrations/canvas/credentials")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvasToken").value("decrypted-canvas-token"))
                .andExpect(jsonPath("$.lastValidatedAt").exists());

        then(credentialsService).should().getCanvasTokenByCognitoSub(COGNITO_SUB);
    }

    @Test
    @DisplayName("DELETE /v1/integrations/canvas/credentials - Canvas 토큰 삭제 성공")
    void deleteCanvasToken_Success() throws Exception {
        // Given - void 메서드이므로 아무것도 반환하지 않음

        // When & Then
        mockMvc.perform(delete("/v1/integrations/canvas/credentials")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNoContent());

        then(credentialsService).should().deleteCanvasToken(COGNITO_SUB);
    }

    @Test
    @DisplayName("POST /v1/integrations/canvas/sync - Canvas 동기화 성공")
    void syncCanvas_Success() throws Exception {
        // Given
        CanvasSyncResponse response = CanvasSyncResponse.builder()
                .success(true)
                .message("Canvas sync started")
                .coursesCount(5)
                .assignmentsCount(12)
                .syncedAt("2025-01-22T10:30:00")
                .build();

        given(canvasSyncService.syncCanvas(COGNITO_SUB, "full"))
                .willReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/integrations/canvas/sync")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("mode", "full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Canvas sync started"))
                .andExpect(jsonPath("$.coursesCount").value(5))
                .andExpect(jsonPath("$.assignmentsCount").value(12));

        then(canvasSyncService).should().syncCanvas(COGNITO_SUB, "full");
    }

    // Note: X-Cognito-Sub 헤더 검증은 API Gateway에서 처리하므로
    // Controller 단위 테스트에서는 생략 (통합 테스트 또는 System Tests에서 검증)

    @Test
    @DisplayName("Canvas 토큰 등록 → 상태 조회 플로우")
    void canvasIntegrationFlow() throws Exception {
        // Given
        RegisterCanvasTokenRequest registerRequest = RegisterCanvasTokenRequest.builder()
                .canvasToken("test-canvas-token")
                .build();

        RegisterCanvasTokenResponse registerResponse = RegisterCanvasTokenResponse.builder()
                .success(true)
                .message("Canvas token registered successfully")
                .build();

        IntegrationInfo canvasInfo = IntegrationInfo.builder()
                .isConnected(true)
                .lastValidatedAt(LocalDateTime.now())
                .build();

        IntegrationStatusResponse statusResponse = IntegrationStatusResponse.builder()
                .canvas(canvasInfo)
                .build();

        given(credentialsService.registerCanvasToken(anyString(), any(RegisterCanvasTokenRequest.class)))
                .willReturn(registerResponse);
        given(integrationStatusService.getIntegrationStatus(COGNITO_SUB))
                .willReturn(statusResponse);

        // When & Then - 1. 토큰 등록
        mockMvc.perform(post("/v1/integrations/canvas/credentials")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // When & Then - 2. 상태 조회
        mockMvc.perform(get("/v1/integrations/status")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvas.isConnected").value(true));

        // 검증
        then(credentialsService).should().registerCanvasToken(anyString(), any(RegisterCanvasTokenRequest.class));
        then(integrationStatusService).should().getIntegrationStatus(COGNITO_SUB);
    }
}
