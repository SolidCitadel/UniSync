package com.unisync.user.integration.service;

import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.integration.dto.IntegrationInfo;
import com.unisync.user.integration.dto.IntegrationStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 외부 서비스 연동 상태 조회 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationStatusService {

    private final CredentialsRepository credentialsRepository;

    /**
     * 사용자의 전체 연동 상태 조회
     *
     * @param userId 사용자 ID
     * @return 연동 상태 정보
     */
    @Transactional(readOnly = true)
    public IntegrationStatusResponse getIntegrationStatus(Long userId) {
        List<Credentials> allCredentials = credentialsRepository.findAllByUserId(userId);

        IntegrationStatusResponse response = new IntegrationStatusResponse();

        for (Credentials cred : allCredentials) {
            IntegrationInfo info = IntegrationInfo.builder()
                    .isConnected(cred.getIsConnected())
                    .externalUsername(cred.getExternalUsername())
                    .lastValidatedAt(cred.getLastValidatedAt())
                    .lastSyncedAt(cred.getLastSyncedAt())
                    .build();

            switch (cred.getProvider()) {
                case CANVAS:
                    response.setCanvas(info);
                    break;
                case GOOGLE_CALENDAR:
                    response.setGoogleCalendar(info);
                    break;
            }
        }

        log.info("Integration status retrieved for userId={}", userId);
        return response;
    }
}