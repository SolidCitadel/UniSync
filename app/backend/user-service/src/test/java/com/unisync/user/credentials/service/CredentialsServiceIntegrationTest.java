package com.unisync.user.credentials.service;

import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.exception.CanvasTokenNotFoundException;
import com.unisync.user.credentials.exception.InvalidCanvasTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CredentialsServiceIntegrationTest {

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @MockBean
    private RestTemplate restTemplate;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_CANVAS_TOKEN = "test-canvas-token-123";

    @BeforeEach
    void setUp() {
        credentialsRepository.deleteAll();
    }

    @Test
    @DisplayName("Canvas 토큰 등록 성공")
    void registerCanvasToken_Success() {
        // Given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        // Canvas API 호출 모킹 (성공)
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        // When
        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(TEST_USER_ID, request);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");

        // DB 확인
        Credentials saved = credentialsRepository
                .findByUserIdAndProvider(TEST_USER_ID, CredentialProvider.CANVAS)
                .orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(saved.getProvider()).isEqualTo(CredentialProvider.CANVAS);
        assertThat(saved.getEncryptedToken()).isNotEqualTo(TEST_CANVAS_TOKEN); // 암호화되어야 함
        assertThat(saved.getLastValidatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Canvas 토큰 등록 실패 - 유효하지 않은 토큰")
    void registerCanvasToken_InvalidToken() {
        // Given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken("invalid-token")
                .build();

        // Canvas API 호출 모킹 (401 Unauthorized)
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        // When & Then
        assertThatThrownBy(() -> credentialsService.registerCanvasToken(TEST_USER_ID, request))
                .isInstanceOf(InvalidCanvasTokenException.class)
                .hasMessageContaining("Invalid Canvas token");

        // DB에 저장되지 않아야 함
        assertThat(credentialsRepository.findByUserIdAndProvider(TEST_USER_ID, CredentialProvider.CANVAS))
                .isEmpty();
    }

    @Test
    @DisplayName("Canvas 토큰 조회 성공 - 복호화 확인")
    void getCanvasToken_Success() {
        // Given - 먼저 토큰 등록
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        credentialsService.registerCanvasToken(TEST_USER_ID, request);

        // When - 토큰 조회
        CanvasTokenResponse response = credentialsService.getCanvasToken(TEST_USER_ID);

        // Then - 복호화된 원본 토큰 반환 확인
        assertThat(response.getCanvasToken()).isEqualTo(TEST_CANVAS_TOKEN);
        assertThat(response.getLastValidatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Canvas 토큰 조회 실패 - 등록되지 않은 사용자")
    void getCanvasToken_NotFound() {
        // When & Then
        assertThatThrownBy(() -> credentialsService.getCanvasToken(999L))
                .isInstanceOf(CanvasTokenNotFoundException.class)
                .hasMessageContaining("Canvas token not found");
    }

    @Test
    @DisplayName("Canvas 토큰 삭제 성공")
    void deleteCanvasToken_Success() {
        // Given - 먼저 토큰 등록
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        credentialsService.registerCanvasToken(TEST_USER_ID, request);

        // When - 토큰 삭제
        credentialsService.deleteCanvasToken(TEST_USER_ID);

        // Then - DB에서 삭제 확인
        assertThat(credentialsRepository.findByUserIdAndProvider(TEST_USER_ID, CredentialProvider.CANVAS))
                .isEmpty();
    }

    @Test
    @DisplayName("Canvas 토큰 삭제 실패 - 등록되지 않은 사용자")
    void deleteCanvasToken_NotFound() {
        // When & Then
        assertThatThrownBy(() -> credentialsService.deleteCanvasToken(999L))
                .isInstanceOf(CanvasTokenNotFoundException.class)
                .hasMessageContaining("Canvas token not found");
    }

    @Test
    @DisplayName("같은 사용자가 토큰을 재등록하면 업데이트됨")
    void registerCanvasToken_Update() {
        // Given - 첫 번째 토큰 등록
        RegisterCanvasTokenRequest request1 = RegisterCanvasTokenRequest.builder()
                .canvasToken("old-token")
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        credentialsService.registerCanvasToken(TEST_USER_ID, request1);

        // When - 두 번째 토큰 등록 (업데이트)
        RegisterCanvasTokenRequest request2 = RegisterCanvasTokenRequest.builder()
                .canvasToken("new-token")
                .build();

        credentialsService.registerCanvasToken(TEST_USER_ID, request2);

        // Then - 새 토큰으로 업데이트 확인
        CanvasTokenResponse response = credentialsService.getCanvasToken(TEST_USER_ID);
        assertThat(response.getCanvasToken()).isEqualTo("new-token");

        // 레코드가 1개만 존재해야 함 (중복 생성 아님)
        long count = credentialsRepository.count();
        assertThat(count).isEqualTo(1);
    }
}