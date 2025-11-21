package com.unisync.user.credentials.service;

import com.unisync.user.common.config.EncryptionService;
import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.common.repository.UserRepository;
import com.unisync.user.common.service.SqsPublisher;
import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.exception.CanvasTokenNotFoundException;
import com.unisync.user.credentials.exception.InvalidCanvasTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CredentialsService 단위 테스트")
class CredentialsServiceTest {

    @Mock
    private CredentialsRepository credentialsRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private CanvasApiClient canvasApiClient;

    @Mock
    private SqsPublisher sqsPublisher;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CredentialsService credentialsService;

    private String cognitoSub;
    private String canvasToken;
    private String encryptedToken;
    private Credentials credentials;
    private CanvasApiClient.CanvasProfile canvasProfile;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-cognito-sub-123";
        canvasToken = "test-canvas-token";
        encryptedToken = "encrypted-test-token";

        // Phase 1: userTokenRegisteredQueue 필드 제거됨 (수동 동기화 사용)
        // ReflectionTestUtils.setField(credentialsService, "userTokenRegisteredQueue", "user-token-registered-queue");

        canvasProfile = CanvasApiClient.CanvasProfile.builder()
                .id(12345L)
                .name("Test User")
                .loginId("2021101234")
                .primaryEmail("test@example.com")
                .build();

        credentials = Credentials.builder()
                .id(1L)
                .cognitoSub(cognitoSub)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken(encryptedToken)
                .isConnected(true)
                .externalUserId("12345")
                .externalUsername("2021101234")
                .lastValidatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Canvas 토큰 등록 성공 - 신규 등록")
    void registerCanvasToken_Success_NewToken() {
        // given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(canvasToken)
                .build();

        given(canvasApiClient.validateTokenAndGetProfile(canvasToken))
                .willReturn(canvasProfile);
        given(encryptionService.encrypt(canvasToken))
                .willReturn(encryptedToken);
        given(credentialsRepository.findByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS))
                .willReturn(Optional.empty());
        given(credentialsRepository.save(any(Credentials.class)))
                .willReturn(credentials);

        // when
        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(cognitoSub, request);

        // then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");

        then(canvasApiClient).should().validateTokenAndGetProfile(canvasToken);
        then(encryptionService).should().encrypt(canvasToken);
        then(credentialsRepository).should().save(any(Credentials.class));
    }

    @Test
    @DisplayName("Canvas 토큰 등록 성공 - 기존 토큰 업데이트")
    void registerCanvasToken_Success_UpdateToken() {
        // given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken(canvasToken)
                .build();

        Credentials existingCredentials = Credentials.builder()
                .id(1L)
                .cognitoSub(cognitoSub)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken("old-encrypted-token")
                .isConnected(false)
                .build();

        given(canvasApiClient.validateTokenAndGetProfile(canvasToken))
                .willReturn(canvasProfile);
        given(encryptionService.encrypt(canvasToken))
                .willReturn(encryptedToken);
        given(credentialsRepository.findByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS))
                .willReturn(Optional.of(existingCredentials));
        given(credentialsRepository.save(any(Credentials.class)))
                .willReturn(credentials);

        // when
        RegisterCanvasTokenResponse response = credentialsService.registerCanvasToken(cognitoSub, request);

        // then
        assertThat(response.isSuccess()).isTrue();
        assertThat(existingCredentials.getEncryptedToken()).isEqualTo(encryptedToken);
        assertThat(existingCredentials.getIsConnected()).isTrue();
        assertThat(existingCredentials.getExternalUserId()).isEqualTo("12345");
        assertThat(existingCredentials.getExternalUsername()).isEqualTo("2021101234");
    }

    @Test
    @DisplayName("Canvas 토큰 등록 실패 - 유효하지 않은 토큰")
    void registerCanvasToken_Failure_InvalidToken() {
        // given
        RegisterCanvasTokenRequest request = RegisterCanvasTokenRequest.builder()
                .canvasToken("invalid-token")
                .build();

        given(canvasApiClient.validateTokenAndGetProfile("invalid-token"))
                .willThrow(new InvalidCanvasTokenException("Invalid Canvas token"));

        // when & then
        assertThatThrownBy(() -> credentialsService.registerCanvasToken(cognitoSub, request))
                .isInstanceOf(InvalidCanvasTokenException.class)
                .hasMessageContaining("Invalid Canvas token");

        then(encryptionService).should(never()).encrypt(anyString());
        then(credentialsRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("Canvas 토큰 조회 성공 - cognitoSub로 조회")
    void getCanvasTokenByCognitoSub_Success() {
        // given
        given(credentialsRepository.findByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS))
                .willReturn(Optional.of(credentials));
        given(encryptionService.decrypt(encryptedToken))
                .willReturn(canvasToken);

        // when
        CanvasTokenResponse response = credentialsService.getCanvasTokenByCognitoSub(cognitoSub);

        // then
        assertThat(response.getCanvasToken()).isEqualTo(canvasToken);
        assertThat(response.getLastValidatedAt()).isNotNull();

        then(credentialsRepository).should().findByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS);
        then(encryptionService).should().decrypt(encryptedToken);
    }

    @Test
    @DisplayName("Canvas 토큰 조회 실패 - 등록되지 않은 사용자")
    void getCanvasTokenByCognitoSub_Failure_NotFound() {
        // given
        given(credentialsRepository.findByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> credentialsService.getCanvasTokenByCognitoSub(cognitoSub))
                .isInstanceOf(CanvasTokenNotFoundException.class)
                .hasMessageContaining("Canvas token not found");

        then(encryptionService).should(never()).decrypt(anyString());
    }

    @Test
    @DisplayName("Canvas 토큰 삭제 성공")
    void deleteCanvasToken_Success() {
        // given
        given(credentialsRepository.existsByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS))
                .willReturn(true);

        // when
        credentialsService.deleteCanvasToken(cognitoSub);

        // then
        then(credentialsRepository).should().existsByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS);
        then(credentialsRepository).should().deleteByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS);
    }

    @Test
    @DisplayName("Canvas 토큰 삭제 실패 - 등록되지 않은 사용자")
    void deleteCanvasToken_Failure_NotFound() {
        // given
        given(credentialsRepository.existsByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> credentialsService.deleteCanvasToken(cognitoSub))
                .isInstanceOf(CanvasTokenNotFoundException.class)
                .hasMessageContaining("Canvas token not found");

        then(credentialsRepository).should(never()).deleteByCognitoSubAndProvider(anyString(), any());
    }
}
