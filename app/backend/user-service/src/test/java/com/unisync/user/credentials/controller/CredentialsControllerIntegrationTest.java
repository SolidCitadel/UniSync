package com.unisync.user.credentials.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.service.CanvasApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CredentialsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @MockBean
    private RestTemplate restTemplate;

    @Value("${unisync.api-keys.canvas-sync-lambda}")
    private String canvasSyncApiKey;

    private static final String TEST_COGNITO_SUB = "test-cognito-sub";
    private static final String TEST_CANVAS_TOKEN = "test-canvas-token-123";

    @BeforeEach
    void setUp() {
        credentialsRepository.deleteAll();

        // Canvas API 호출 모킹 (기본 성공 - CanvasProfile 반환)
        CanvasApiClient.CanvasProfile mockProfile = CanvasApiClient.CanvasProfile.builder()
                .id(12345L)
                .name("Test User")
                .loginId("2021105636")
                .primaryEmail("test@example.com")
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(CanvasApiClient.CanvasProfile.class)
        )).thenReturn(ResponseEntity.ok(mockProfile));
    }

    @Test
    @DisplayName("POST /api/v1/credentials/canvas - 토큰 등록 성공")
    void registerCanvasToken_Success() throws Exception {
        // Given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        // When & Then
        mockMvc.perform(post("/credentials/canvas")
                        .header("X-Cognito-Sub", TEST_COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Canvas token registered successfully"));

        // DB 확인
        assertThat(credentialsRepository.existsByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS))
                .isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/credentials/canvas - 토큰 비어있으면 400 에러")
    void registerCanvasToken_EmptyToken() throws Exception {
        // Given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken("")
                .build();

        // When & Then
        mockMvc.perform(post("/credentials/canvas")
                        .header("X-Cognito-Sub", TEST_COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /credentials/canvas - 본인 토큰 조회 성공 (사용자)")
    void getCanvasToken_AsUser_Success() throws Exception {
        // Given - 먼저 토큰 등록
        RegisterCanvasTokenRequest registerRequest = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        mockMvc.perform(post("/credentials/canvas")
                .header("X-Cognito-Sub", TEST_COGNITO_SUB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When & Then - 조회
        mockMvc.perform(get("/credentials/canvas")
                        .header("X-Cognito-Sub", TEST_COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvasToken").value(TEST_CANVAS_TOKEN))
                .andExpect(jsonPath("$.lastValidatedAt").exists());
    }

    @Test
    @DisplayName("GET /credentials/canvas - 등록되지 않은 사용자 토큰 조회 실패")
    void getCanvasToken_AsUser_NotFound() throws Exception {
        // When & Then - 등록하지 않은 사용자가 조회
        mockMvc.perform(get("/credentials/canvas")
                        .header("X-Cognito-Sub", "unregistered-cognito-sub"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CANVAS_TOKEN_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /credentials/canvas/by-cognito-sub/{cognitoSub} - 서비스 API Key로 조회 성공")
    void getCanvasToken_AsService_Success() throws Exception {
        // Given - 먼저 토큰 등록
        RegisterCanvasTokenRequest registerRequest = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        mockMvc.perform(post("/credentials/canvas")
                .header("X-Cognito-Sub", TEST_COGNITO_SUB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When & Then - 서비스가 API Key로 조회
        mockMvc.perform(get("/credentials/canvas/by-cognito-sub/{cognitoSub}", TEST_COGNITO_SUB)
                        .header("X-Api-Key", canvasSyncApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvasToken").value(TEST_CANVAS_TOKEN))
                .andExpect(jsonPath("$.lastValidatedAt").exists());
    }

    @Test
    @DisplayName("GET /credentials/canvas/by-cognito-sub/{cognitoSub} - 잘못된 API Key로 조회 실패")
    void getCanvasToken_AsService_InvalidApiKey() throws Exception {
        // When & Then
        mockMvc.perform(get("/credentials/canvas/by-cognito-sub/{cognitoSub}", TEST_COGNITO_SUB)
                        .header("X-Api-Key", "invalid-api-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /credentials/canvas - 토큰 삭제 성공")
    void deleteCanvasToken_Success() throws Exception {
        // Given - 먼저 토큰 등록
        RegisterCanvasTokenRequest registerRequest = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        mockMvc.perform(post("/credentials/canvas")
                .header("X-Cognito-Sub", TEST_COGNITO_SUB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When & Then - 삭제
        mockMvc.perform(delete("/credentials/canvas")
                        .header("X-Cognito-Sub", TEST_COGNITO_SUB))
                .andExpect(status().isNoContent());

        // DB 확인
        assertThat(credentialsRepository.existsByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS))
                .isFalse();
    }

    @Test
    @DisplayName("DELETE /credentials/canvas - 등록되지 않은 토큰 삭제 실패")
    void deleteCanvasToken_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(delete("/credentials/canvas")
                        .header("X-Cognito-Sub", TEST_COGNITO_SUB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CANVAS_TOKEN_NOT_FOUND"));
    }
}