package com.unisync.user.sync.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.common.exception.GlobalExceptionHandler;
import com.unisync.user.common.util.JwtUtil;
import com.unisync.user.sync.dto.CanvasSyncResponse;
import com.unisync.user.sync.exception.CanvasSyncException;
import com.unisync.user.sync.service.CanvasSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SyncController 단위 테스트
 *
 * MockMvc를 사용하여 실제 HTTP 요청 없이 컨트롤러 레이어만 테스트합니다.
 * CanvasSyncService와 JwtUtil은 Mock으로 대체합니다.
 */
@WebMvcTest(SyncController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("SyncController 단위 테스트")
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CanvasSyncService canvasSyncService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("Canvas 동기화 성공")
    void syncCanvas_Success() throws Exception {
        // Given: JWT와 서비스가 정상 응답
        String authHeader = "Bearer test-jwt-token";
        String cognitoSub = "test-cognito-sub-123";

        CanvasSyncResponse response = CanvasSyncResponse.builder()
                .coursesCount(3)
                .assignmentsCount(15)
                .syncedAt("2025-11-20T12:00:00Z")
                .build();

        when(jwtUtil.extractCognitoSub(authHeader)).thenReturn(cognitoSub);
        when(canvasSyncService.syncCanvas(cognitoSub)).thenReturn(response);

        // When & Then: POST /v1/sync/canvas
        mockMvc.perform(post("/v1/sync/canvas")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesCount").value(3))
                .andExpect(jsonPath("$.assignmentsCount").value(15))
                .andExpect(jsonPath("$.syncedAt").value("2025-11-20T12:00:00Z"));
    }

    @Test
    @DisplayName("Canvas 동기화 성공 - 과제가 0개인 경우")
    void syncCanvas_Success_NoAssignments() throws Exception {
        // Given: 과제가 없는 응답
        String authHeader = "Bearer test-jwt-token";
        String cognitoSub = "test-cognito-sub-123";

        CanvasSyncResponse response = CanvasSyncResponse.builder()
                .coursesCount(2)
                .assignmentsCount(0)
                .syncedAt("2025-11-20T12:00:00Z")
                .build();

        when(jwtUtil.extractCognitoSub(authHeader)).thenReturn(cognitoSub);
        when(canvasSyncService.syncCanvas(cognitoSub)).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/sync/canvas")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesCount").value(2))
                .andExpect(jsonPath("$.assignmentsCount").value(0))
                .andExpect(jsonPath("$.syncedAt").value("2025-11-20T12:00:00Z"));
    }

    // Note: Authorization 헤더 없음 테스트는 Spring의 기본 동작에 의존하므로 생략

    @Test
    @DisplayName("Canvas 동기화 실패 - Lambda 호출 오류")
    void syncCanvas_Fail_LambdaError() throws Exception {
        // Given: Lambda 호출 실패
        String authHeader = "Bearer test-jwt-token";
        String cognitoSub = "test-cognito-sub-123";

        when(jwtUtil.extractCognitoSub(authHeader)).thenReturn(cognitoSub);
        when(canvasSyncService.syncCanvas(cognitoSub))
                .thenThrow(new CanvasSyncException("Canvas 동기화 Lambda 호출 실패", null));

        // When & Then: CanvasSyncException 발생 시 500 응답
        mockMvc.perform(post("/v1/sync/canvas")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("CANVAS_SYNC_ERROR"))
                .andExpect(jsonPath("$.message").value("Canvas 동기화 Lambda 호출 실패"));
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - Canvas 토큰 없음")
    void syncCanvas_Fail_NoCanvasToken() throws Exception {
        // Given: Canvas 토큰이 없어 Lambda에서 에러 발생
        String authHeader = "Bearer test-jwt-token";
        String cognitoSub = "test-cognito-sub-123";

        when(jwtUtil.extractCognitoSub(authHeader)).thenReturn(cognitoSub);
        when(canvasSyncService.syncCanvas(cognitoSub))
                .thenThrow(new CanvasSyncException("Canvas token not found for user", null));

        // When & Then
        mockMvc.perform(post("/v1/sync/canvas")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("CANVAS_SYNC_ERROR"))
                .andExpect(jsonPath("$.message").value("Canvas token not found for user"));
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - JWT 파싱 오류")
    void syncCanvas_Fail_JwtParsingError() throws Exception {
        // Given: JWT 파싱 실패
        String authHeader = "Bearer invalid-jwt-token";

        when(jwtUtil.extractCognitoSub(authHeader))
                .thenThrow(new RuntimeException("Invalid JWT token"));

        // When & Then
        mockMvc.perform(post("/v1/sync/canvas")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("JWT에서 cognitoSub 추출 검증")
    void syncCanvas_VerifyCognitoSubExtraction() throws Exception {
        // Given
        String authHeader = "Bearer test-jwt-token-with-cognito-sub";
        String expectedCognitoSub = "extracted-cognito-sub-456";

        CanvasSyncResponse response = CanvasSyncResponse.builder()
                .coursesCount(1)
                .assignmentsCount(5)
                .syncedAt("2025-11-20T12:00:00Z")
                .build();

        when(jwtUtil.extractCognitoSub(authHeader)).thenReturn(expectedCognitoSub);
        when(canvasSyncService.syncCanvas(expectedCognitoSub)).thenReturn(response);

        // When & Then: cognitoSub가 올바르게 추출되어 서비스에 전달되는지 확인
        mockMvc.perform(post("/v1/sync/canvas")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        // 추가 검증: jwtUtil이 올바르게 호출되었는지 확인
        org.mockito.Mockito.verify(jwtUtil).extractCognitoSub(authHeader);
        org.mockito.Mockito.verify(canvasSyncService).syncCanvas(expectedCognitoSub);
    }
}
