package com.unisync.user.credentials.service;

import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.exception.InvalidCanvasTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * 실제 Canvas API를 사용한 통합 테스트
 *
 * 환경변수 CANVAS_API_TOKEN이 설정되어 있을 때만 실행됩니다.
 */
@SpringBootTest
@Transactional
class CredentialsServiceRealCanvasTest {

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @Autowired
    private Environment env;

    private String realCanvasToken;

    private static final String TEST_COGNITO_SUB = "test-cognito-sub-999";

    @BeforeEach
    void setUp() {
        credentialsRepository.deleteAll();

        // Environment에서 직접 읽기 (없으면 null)
        realCanvasToken = env.getProperty("CANVAS_API_TOKEN");

        // 토큰이 없으면 에러 발생 - .env 파일 로드 실패를 명확히 알림
        if (realCanvasToken == null || realCanvasToken.isEmpty()) {
            throw new IllegalStateException(
                "CANVAS_API_TOKEN environment variable is not set. " +
                "Ensure .env file exists at project root and contains CANVAS_API_TOKEN. " +
                "The test runner loads environment variables from .env via build.gradle.kts."
            );
        }

        System.out.println("=".repeat(80));
        System.out.println("🔵 Running tests with REAL Canvas API");
        System.out.println("   Token: " + realCanvasToken.substring(0, Math.min(10, realCanvasToken.length())) + "...");
        System.out.println("=".repeat(80));
    }

    @Test
    @DisplayName("실제 Canvas API로 유효한 토큰 등록 성공")
    void registerCanvasToken_WithRealAPI_Success() {
        // Given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(realCanvasToken)
                .build();

        // When
        System.out.println("📤 Calling Canvas API to validate token...");
        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request);
        System.out.println("✅ Canvas API validation successful!");

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");

        // DB에 암호화되어 저장되었는지 확인
        Credentials saved = credentialsRepository
                .findByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS)
                .orElseThrow();

        System.out.println("🔐 Token stored in DB:");
        System.out.println("   Original: " + realCanvasToken.substring(0, Math.min(15, realCanvasToken.length())) + "...");
        System.out.println("   Encrypted: " + saved.getEncryptedToken().substring(0, Math.min(30, saved.getEncryptedToken().length())) + "...");
        System.out.println("   Last validated: " + saved.getLastValidatedAt());
        System.out.println("   Is connected: " + saved.getIsConnected());
        System.out.println("   External user ID: " + saved.getExternalUserId());
        System.out.println("   External username: " + saved.getExternalUsername());

        assertThat(saved.getCognitoSub()).isEqualTo(TEST_COGNITO_SUB);
        assertThat(saved.getProvider()).isEqualTo(CredentialProvider.CANVAS);
        assertThat(saved.getEncryptedToken()).isNotEqualTo(realCanvasToken); // 암호화되어야 함
        assertThat(saved.getEncryptedToken()).hasSizeGreaterThan(50); // 암호화되면 더 길어짐
        assertThat(saved.getLastValidatedAt()).isNotNull();

        // 새로 추가된 Canvas Profile 필드 검증
        assertThat(saved.getIsConnected()).isTrue(); // 연동 상태 활성화
        assertThat(saved.getExternalUserId()).isNotBlank(); // Canvas 사용자 ID
        assertThat(saved.getExternalUsername()).isNotBlank(); // Canvas 학번/로그인 ID
    }

    @Test
    @DisplayName("실제 Canvas API로 토큰 조회 및 복호화 검증")
    void getCanvasToken_WithRealAPI_DecryptionWorks() {
        // Given - 먼저 토큰 등록
        RegisterCanvasTokenRequest registerRequest = RegisterCanvasTokenRequest.builder()
                .canvasToken(realCanvasToken)
                .build();
        credentialsService.registerCanvasToken(TEST_COGNITO_SUB, registerRequest);

        // When - 토큰 조회
        System.out.println("🔓 Retrieving and decrypting token from DB...");
        CanvasTokenResponse response = credentialsService.getCanvasTokenByCognitoSub(TEST_COGNITO_SUB);

        // Then - 복호화된 토큰이 원본과 동일한지 확인
        System.out.println("✅ Token decrypted successfully!");
        System.out.println("   Decrypted matches original: " + response.getCanvasToken().equals(realCanvasToken));

        assertThat(response.getCanvasToken()).isEqualTo(realCanvasToken);
        assertThat(response.getLastValidatedAt()).isNotNull();
    }

    @Test
    @DisplayName("실제 Canvas API로 잘못된 토큰 검증 실패")
    void registerCanvasToken_WithRealAPI_InvalidToken() {
        // Given - 잘못된 토큰
        String invalidToken = "invalid-token-12345";
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(invalidToken)
                .build();

        // When & Then
        System.out.println("📤 Calling Canvas API with invalid token...");
        assertThatThrownBy(() -> credentialsService.registerCanvasToken(TEST_COGNITO_SUB, request))
                .isInstanceOf(InvalidCanvasTokenException.class)
                .hasMessageContaining("Invalid Canvas token");

        System.out.println("✅ Canvas API correctly rejected invalid token");

        // DB에 저장되지 않아야 함
        assertThat(credentialsRepository.findByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS))
                .isEmpty();
    }

    @Test
    @DisplayName("실제 Canvas API로 전체 워크플로우 테스트 (등록 → 조회 → 삭제)")
    void fullWorkflow_WithRealAPI() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔄 Testing full workflow with real Canvas API");
        System.out.println("=".repeat(80));

        // 1. 토큰 등록
        System.out.println("\n[Step 1] Registering Canvas token...");
        RegisterCanvasTokenRequest registerRequest = RegisterCanvasTokenRequest.builder()
                .canvasToken(realCanvasToken)
                .build();
        RegisterCanvasTokenResponse registerResponse = credentialsService.registerCanvasToken(TEST_COGNITO_SUB, registerRequest);
        assertThat(registerResponse.isSuccess()).isTrue();
        System.out.println("✅ Token registered successfully");

        // 2. 토큰 조회
        System.out.println("\n[Step 2] Retrieving Canvas token...");
        CanvasTokenResponse getResponse = credentialsService.getCanvasTokenByCognitoSub(TEST_COGNITO_SUB);
        assertThat(getResponse.getCanvasToken()).isEqualTo(realCanvasToken);
        System.out.println("✅ Token retrieved and decrypted successfully");

        // 3. 토큰 삭제
        System.out.println("\n[Step 3] Deleting Canvas token...");
        credentialsService.deleteCanvasToken(TEST_COGNITO_SUB);
        assertThat(credentialsRepository.findByCognitoSubAndProvider(TEST_COGNITO_SUB, CredentialProvider.CANVAS))
                .isEmpty();
        System.out.println("✅ Token deleted successfully");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ Full workflow completed successfully!");
        System.out.println("=".repeat(80) + "\n");
    }
}