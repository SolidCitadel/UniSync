package com.unisync.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.integration.dto.IntegrationStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Status API 통합 테스트
 * H2 인메모리 DB 사용
 */
@SpringBootTest(properties = {
    "spring.cloud.aws.sqs.enabled=false",
    "canvas.api.base-url=https://khcanvas.khu.ac.kr/api/v1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        credentialsRepository.deleteAll();
    }

    @Test
    @DisplayName("Canvas 연동 상태 조회 성공")
    void testGetIntegrationStatus_WithCanvas() throws Exception {
        // given: Canvas 연동 정보 저장
        String cognitoSub = "test-cognito-sub-1";
        Credentials canvasCredentials = Credentials.builder()
            .cognitoSub(cognitoSub)
            .provider(CredentialProvider.CANVAS)
            .encryptedToken("encrypted_token")
            .isConnected(true)
            .externalUserId("12345")
            .externalUsername("2021105636")
            .lastValidatedAt(LocalDateTime.now())
            .build();
        credentialsRepository.save(canvasCredentials);

        // when: GET /integrations/status
        String response = mockMvc.perform(get("/v1/integrations/status")
                .header("X-Cognito-Sub", cognitoSub))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: Canvas 연동 정보 반환
        IntegrationStatusResponse statusResponse = objectMapper.readValue(response, IntegrationStatusResponse.class);
        assertThat(statusResponse.getCanvas()).isNotNull();
        assertThat(statusResponse.getCanvas().getIsConnected()).isTrue();
        assertThat(statusResponse.getCanvas().getExternalUsername()).isEqualTo("2021105636");
        assertThat(statusResponse.getCanvas().getLastValidatedAt()).isNotNull();

        // Google Calendar는 연동되지 않음
        assertThat(statusResponse.getGoogleCalendar()).isNull();
    }

    @Test
    @DisplayName("Canvas + Google Calendar 모두 연동된 경우")
    void testGetIntegrationStatus_WithMultipleProviders() throws Exception {
        // given: Canvas와 Google Calendar 모두 연동
        String cognitoSub = "test-cognito-sub-2";

        Credentials canvasCredentials = Credentials.builder()
            .cognitoSub(cognitoSub)
            .provider(CredentialProvider.CANVAS)
            .encryptedToken("canvas_token")
            .isConnected(true)
            .externalUsername("2021105636")
            .lastValidatedAt(LocalDateTime.now().minusDays(1))
            .build();
        credentialsRepository.save(canvasCredentials);

        Credentials googleCredentials = Credentials.builder()
            .cognitoSub(cognitoSub)
            .provider(CredentialProvider.GOOGLE_CALENDAR)
            .encryptedToken("google_token")
            .isConnected(true)
            .externalUsername("user@gmail.com")
            .lastValidatedAt(LocalDateTime.now().minusHours(2))
            .build();
        credentialsRepository.save(googleCredentials);

        // when: GET /integrations/status
        String response = mockMvc.perform(get("/v1/integrations/status")
                .header("X-Cognito-Sub", cognitoSub))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: 두 연동 정보 모두 반환
        IntegrationStatusResponse statusResponse = objectMapper.readValue(response, IntegrationStatusResponse.class);

        assertThat(statusResponse.getCanvas()).isNotNull();
        assertThat(statusResponse.getCanvas().getIsConnected()).isTrue();
        assertThat(statusResponse.getCanvas().getExternalUsername()).isEqualTo("2021105636");

        assertThat(statusResponse.getGoogleCalendar()).isNotNull();
        assertThat(statusResponse.getGoogleCalendar().getIsConnected()).isTrue();
        assertThat(statusResponse.getGoogleCalendar().getExternalUsername()).isEqualTo("user@gmail.com");
    }

    @Test
    @DisplayName("연동되지 않은 사용자의 경우 모든 필드가 null")
    void testGetIntegrationStatus_NoIntegrations() throws Exception {
        // given: 연동 정보 없음
        String cognitoSub = "test-cognito-sub-999";

        // when: GET /integrations/status
        String response = mockMvc.perform(get("/v1/integrations/status")
                .header("X-Cognito-Sub", cognitoSub))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: 모든 연동 정보가 null
        IntegrationStatusResponse statusResponse = objectMapper.readValue(response, IntegrationStatusResponse.class);
        assertThat(statusResponse.getCanvas()).isNull();
        assertThat(statusResponse.getGoogleCalendar()).isNull();
        assertThat(statusResponse.getOutlook()).isNull();
    }

    @Test
    @DisplayName("Canvas 연동되었으나 is_connected=false인 경우")
    void testGetIntegrationStatus_DisconnectedCanvas() throws Exception {
        // given: Canvas 연동되었으나 비활성화 상태
        String cognitoSub = "test-cognito-sub-3";
        Credentials canvasCredentials = Credentials.builder()
            .cognitoSub(cognitoSub)
            .provider(CredentialProvider.CANVAS)
            .encryptedToken("encrypted_token")
            .isConnected(false)  // 비활성화
            .externalUsername("2021105636")
            .lastValidatedAt(LocalDateTime.now().minusDays(10))
            .build();
        credentialsRepository.save(canvasCredentials);

        // when: GET /integrations/status
        String response = mockMvc.perform(get("/v1/integrations/status")
                .header("X-Cognito-Sub", cognitoSub))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: isConnected=false로 반환
        IntegrationStatusResponse statusResponse = objectMapper.readValue(response, IntegrationStatusResponse.class);
        assertThat(statusResponse.getCanvas()).isNotNull();
        assertThat(statusResponse.getCanvas().getIsConnected()).isFalse();
        assertThat(statusResponse.getCanvas().getExternalUsername()).isEqualTo("2021105636");
    }
}