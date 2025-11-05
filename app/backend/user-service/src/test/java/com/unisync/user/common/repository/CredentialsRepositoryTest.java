package com.unisync.user.common.repository;

import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CredentialsRepository 테스트")
class CredentialsRepositoryTest {

    @Autowired
    private CredentialsRepository credentialsRepository;

    @Test
    @DisplayName("새로운 필드들과 함께 Credentials 저장 및 조회")
    void saveAndRetrieveCredentialsWithNewFields() {
        // Given: 새 필드들을 포함한 Credentials 생성
        Long userId = 1L;
        Credentials credentials = Credentials.builder()
                .userId(userId)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken("encrypted-token-12345")
                .isConnected(true)
                .externalUserId("canvas-user-123")
                .externalUsername("2021101234")
                .lastValidatedAt(LocalDateTime.now())
                .lastSyncedAt(LocalDateTime.now().minusMinutes(5))
                .build();

        // When: 저장
        Credentials saved = credentialsRepository.save(credentials);

        // Then: 저장 확인
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIsConnected()).isTrue();
        assertThat(saved.getExternalUserId()).isEqualTo("canvas-user-123");
        assertThat(saved.getExternalUsername()).isEqualTo("2021101234");
        assertThat(saved.getLastSyncedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 조회
        Optional<Credentials> retrieved = credentialsRepository.findByUserIdAndProvider(
                userId, CredentialProvider.CANVAS);

        // Then: 조회 확인
        assertThat(retrieved).isPresent();
        Credentials found = retrieved.get();
        assertThat(found.getIsConnected()).isTrue();
        assertThat(found.getExternalUserId()).isEqualTo("canvas-user-123");
        assertThat(found.getExternalUsername()).isEqualTo("2021101234");
    }

    @Test
    @DisplayName("isConnected 기본값이 false인지 확인")
    void isConnectedDefaultValue() {
        // Given: isConnected를 명시하지 않은 Credentials
        Credentials credentials = Credentials.builder()
                .userId(2L)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken("encrypted-token-67890")
                .build();

        // When: 저장
        Credentials saved = credentialsRepository.save(credentials);

        // Then: 기본값 false 확인
        assertThat(saved.getIsConnected()).isFalse();
    }

    @Test
    @DisplayName("findAllByUserId로 사용자의 모든 연동 정보 조회")
    void findAllByUserId() {
        // Given: 한 사용자에 대해 여러 provider의 credentials 저장
        Long userId = 3L;

        Credentials canvas = Credentials.builder()
                .userId(userId)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken("canvas-token")
                .isConnected(true)
                .externalUsername("canvas-user")
                .build();

        Credentials google = Credentials.builder()
                .userId(userId)
                .provider(CredentialProvider.GOOGLE_CALENDAR)
                .encryptedToken("google-token")
                .isConnected(false)
                .build();

        credentialsRepository.save(canvas);
        credentialsRepository.save(google);

        // When: 사용자의 모든 credentials 조회
        List<Credentials> allCredentials = credentialsRepository.findAllByUserId(userId);

        // Then: 2개 조회 확인
        assertThat(allCredentials).hasSize(2);
        assertThat(allCredentials)
                .extracting(Credentials::getProvider)
                .containsExactlyInAnyOrder(
                        CredentialProvider.CANVAS,
                        CredentialProvider.GOOGLE_CALENDAR
                );
    }

    @Test
    @DisplayName("Credentials 업데이트 시 필드 변경 확인")
    void updateCredentialsFields() {
        // Given: Credentials 저장
        Credentials credentials = Credentials.builder()
                .userId(4L)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken("original-token")
                .isConnected(false)
                .build();

        Credentials saved = credentialsRepository.save(credentials);
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: isConnected 업데이트
        saved.setIsConnected(true);
        saved.setExternalUserId("updated-user-id");
        saved.setExternalUsername("updated-username");
        Credentials updated = credentialsRepository.save(saved);

        // Then: 필드 변경 확인
        assertThat(updated.getIsConnected()).isTrue();
        assertThat(updated.getExternalUserId()).isEqualTo("updated-user-id");
        assertThat(updated.getExternalUsername()).isEqualTo("updated-username");
        assertThat(updated.getUpdatedAt()).isNotNull(); // updated_at이 설정되었는지만 확인
    }

    @Test
    @DisplayName("연동 해제 시 is_connected를 false로 업데이트")
    void disconnectIntegration() {
        // Given: 연동된 상태의 Credentials
        Credentials credentials = Credentials.builder()
                .userId(5L)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken("token")
                .isConnected(true)
                .externalUserId("user-123")
                .externalUsername("username")
                .build();

        Credentials saved = credentialsRepository.save(credentials);

        // When: 연동 해제
        saved.setIsConnected(false);
        saved.setExternalUserId(null);
        saved.setExternalUsername(null);
        Credentials disconnected = credentialsRepository.save(saved);

        // Then: 연동 정보 제거 확인
        assertThat(disconnected.getIsConnected()).isFalse();
        assertThat(disconnected.getExternalUserId()).isNull();
        assertThat(disconnected.getExternalUsername()).isNull();
        // 토큰은 유지 (재연동 가능)
        assertThat(disconnected.getEncryptedToken()).isNotNull();
    }
}