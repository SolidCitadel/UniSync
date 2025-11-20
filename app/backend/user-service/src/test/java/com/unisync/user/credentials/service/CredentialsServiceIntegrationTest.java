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
@Transactional
class CredentialsServiceIntegrationTest {

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @MockBean
    private RestTemplate restTemplate;

    private static final String TEST_COGNITO_SUB = "test-cognito-sub";
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

        // Canvas API 호출 모킹 (성공 - Profile 반환)
        CanvasApiClient.CanvasProfile mockProfile = CanvasApiClient.CanvasProfile.builder()
                .id(12345L)
                .name("Test User")
                .loginId("2021101234")
                .primaryEmail("test@example.com")
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(CanvasApiClient.CanvasProfile.class)
        )).thenReturn(ResponseEntity.ok(mockProfile));

        // When
        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");

        // DB 확인
        Credentials saved = credentialsRepository
                .findByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS)
                .orElseThrow();
        assertThat(saved.getCognitoSub()).isEqualTo(TEST_COGNITO_SUB);
        assertThat(saved.getProvider()).isEqualTo(CredentialProvider.CANVAS);
        assertThat(saved.getEncryptedToken()).isNotEqualTo(TEST_CANVAS_TOKEN); // 암호화되어야 함
        assertThat(saved.getLastValidatedAt()).isNotNull();
        assertThat(saved.getIsConnected()).isTrue(); // 연동 상태 확인
        assertThat(saved.getExternalUserId()).isEqualTo("12345");
        assertThat(saved.getExternalUsername()).isEqualTo("2021101234");
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
                eq(CanvasApiClient.CanvasProfile.class)
        )).thenThrow(HttpClientErrorException.Unauthorized.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null
        ));

        // When & Then
        assertThatThrownBy(() -> credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request))
                .isInstanceOf(InvalidCanvasTokenException.class)
                .hasMessageContaining("Invalid Canvas token");

        // DB에 저장되지 않아야 함
        assertThat(credentialsRepository.findByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS))
                .isEmpty();
    }

    @Test
    @DisplayName("Canvas 토큰 조회 성공 - 복호화 확인")
    void getCanvasToken_Success() {
        // Given - 먼저 토큰 등록
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(TEST_CANVAS_TOKEN)
                .build();

        CanvasApiClient.CanvasProfile mockProfile = CanvasApiClient.CanvasProfile.builder()
                .id(12345L)
                .name("Test User")
                .loginId("2021101234")
                .primaryEmail("test@example.com")
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(CanvasApiClient.CanvasProfile.class)
        )).thenReturn(ResponseEntity.ok(mockProfile));

        credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request);

        // When - 토큰 조회
        CanvasTokenResponse response = credentialsService.getCanvasTokenByCognitoSub(TEST_COGNITO_SUB);

        // Then - 복호화된 원본 토큰 반환 확인
        assertThat(response.getCanvasToken()).isEqualTo(TEST_CANVAS_TOKEN);
        assertThat(response.getLastValidatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Canvas 토큰 조회 실패 - 등록되지 않은 사용자")
    void getCanvasToken_NotFound() {
        // When & Then
        assertThatThrownBy(() -> credentialsService.getCanvasTokenByCognitoSub("unregistered-cognito-sub"))
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

        CanvasApiClient.CanvasProfile mockProfile = CanvasApiClient.CanvasProfile.builder()
                .id(12345L)
                .name("Test User")
                .loginId("2021101234")
                .primaryEmail("test@example.com")
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(CanvasApiClient.CanvasProfile.class)
        )).thenReturn(ResponseEntity.ok(mockProfile));

        credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request);

        // When - 토큰 삭제
        credentialsService.deleteCanvasToken(TEST_COGNITO_SUB);

        // Then - DB에서 삭제 확인
        assertThat(credentialsRepository.findByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS))
                .isEmpty();
    }

    @Test
    @DisplayName("Canvas 토큰 삭제 실패 - 등록되지 않은 사용자")
    void deleteCanvasToken_NotFound() {
        // When & Then
        assertThatThrownBy(() -> credentialsService.deleteCanvasToken("unregistered-cognito-sub"))
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

        CanvasApiClient.CanvasProfile mockProfile = CanvasApiClient.CanvasProfile.builder()
                .id(12345L)
                .name("Test User")
                .loginId("2021101234")
                .primaryEmail("test@example.com")
                .build();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(CanvasApiClient.CanvasProfile.class)
        )).thenReturn(ResponseEntity.ok(mockProfile));

        credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request1);

        // When - 두 번째 토큰 등록 (업데이트)
        RegisterCanvasTokenRequest request2 = RegisterCanvasTokenRequest.builder()
                .canvasToken("new-token")
                .build();

        credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request2);

        // Then - 새 토큰으로 업데이트 확인
        CanvasTokenResponse response = credentialsService.getCanvasTokenByCognitoSub(TEST_COGNITO_SUB);
        assertThat(response.getCanvasToken()).isEqualTo("new-token");

        // 레코드가 1개만 존재해야 함 (중복 생성 아님)
        long count = credentialsRepository.count();
        assertThat(count).isEqualTo(1);
    }
}