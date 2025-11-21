package com.unisync.user.integration.service;

import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.integration.dto.IntegrationStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrationStatusService 단위 테스트")
class IntegrationStatusServiceTest {

    @Mock
    private CredentialsRepository credentialsRepository;

    @InjectMocks
    private IntegrationStatusService integrationStatusService;

    private String cognitoSub;
    private Credentials canvasCredentials;
    private Credentials googleCredentials;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-cognito-sub-123";

        canvasCredentials = Credentials.builder()
                .cognitoSub(cognitoSub)
                .provider(CredentialProvider.CANVAS)
                .encryptedToken("encrypted-canvas-token")
                .isConnected(true)
                .externalUsername("2021101234")
                .lastValidatedAt(LocalDateTime.now().minusDays(1))
                .lastSyncedAt(LocalDateTime.now().minusHours(2))
                .build();

        googleCredentials = Credentials.builder()
                .cognitoSub(cognitoSub)
                .provider(CredentialProvider.GOOGLE_CALENDAR)
                .encryptedToken("encrypted-google-token")
                .isConnected(true)
                .externalUsername("user@gmail.com")
                .lastValidatedAt(LocalDateTime.now().minusDays(3))
                .lastSyncedAt(LocalDateTime.now().minusHours(5))
                .build();
    }

    @Test
    @DisplayName("연동 상태 조회 성공 - Canvas만 연동된 경우")
    void getIntegrationStatus_OnlyCanvas() {
        // given
        List<Credentials> credentials = Collections.singletonList(canvasCredentials);
        given(credentialsRepository.findAllByCognitoSub(cognitoSub))
                .willReturn(credentials);

        // when
        IntegrationStatusResponse response = integrationStatusService.getIntegrationStatus(cognitoSub);

        // then
        assertThat(response.getCanvas()).isNotNull();
        assertThat(response.getCanvas().getIsConnected()).isTrue();
        assertThat(response.getCanvas().getExternalUsername()).isEqualTo("2021101234");
        assertThat(response.getCanvas().getLastValidatedAt()).isNotNull();
        assertThat(response.getCanvas().getLastSyncedAt()).isNotNull();

        assertThat(response.getGoogleCalendar()).isNull();
        assertThat(response.getOutlook()).isNull();

        then(credentialsRepository).should().findAllByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("연동 상태 조회 성공 - 여러 서비스 연동된 경우")
    void getIntegrationStatus_MultipleIntegrations() {
        // given
        List<Credentials> credentials = Arrays.asList(canvasCredentials, googleCredentials);
        given(credentialsRepository.findAllByCognitoSub(cognitoSub))
                .willReturn(credentials);

        // when
        IntegrationStatusResponse response = integrationStatusService.getIntegrationStatus(cognitoSub);

        // then
        assertThat(response.getCanvas()).isNotNull();
        assertThat(response.getCanvas().getIsConnected()).isTrue();
        assertThat(response.getCanvas().getExternalUsername()).isEqualTo("2021101234");

        assertThat(response.getGoogleCalendar()).isNotNull();
        assertThat(response.getGoogleCalendar().getIsConnected()).isTrue();
        assertThat(response.getGoogleCalendar().getExternalUsername()).isEqualTo("user@gmail.com");

        then(credentialsRepository).should().findAllByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("연동 상태 조회 성공 - 연동된 서비스 없음")
    void getIntegrationStatus_NoIntegrations() {
        // given
        given(credentialsRepository.findAllByCognitoSub(cognitoSub))
                .willReturn(Collections.emptyList());

        // when
        IntegrationStatusResponse response = integrationStatusService.getIntegrationStatus(cognitoSub);

        // then
        assertThat(response.getCanvas()).isNull();
        assertThat(response.getGoogleCalendar()).isNull();
        assertThat(response.getOutlook()).isNull();

        then(credentialsRepository).should().findAllByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("연동 상태 조회 성공 - 연동 비활성화된 경우")
    void getIntegrationStatus_DisconnectedIntegration() {
        // given
        canvasCredentials.setIsConnected(false);
        List<Credentials> credentials = Collections.singletonList(canvasCredentials);
        given(credentialsRepository.findAllByCognitoSub(cognitoSub))
                .willReturn(credentials);

        // when
        IntegrationStatusResponse response = integrationStatusService.getIntegrationStatus(cognitoSub);

        // then
        assertThat(response.getCanvas()).isNotNull();
        assertThat(response.getCanvas().getIsConnected()).isFalse();
        assertThat(response.getCanvas().getExternalUsername()).isEqualTo("2021101234");

        then(credentialsRepository).should().findAllByCognitoSub(cognitoSub);
    }
}
