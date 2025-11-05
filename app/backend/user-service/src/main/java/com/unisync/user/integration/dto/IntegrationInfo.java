package com.unisync.user.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 외부 서비스 연동 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationInfo {

    /**
     * 연동 여부
     */
    private Boolean isConnected;

    /**
     * 외부 서비스의 사용자명 (Canvas login_id, Google email 등)
     */
    private String externalUsername;

    /**
     * 마지막 검증 시각
     */
    private LocalDateTime lastValidatedAt;

    /**
     * 마지막 동기화 시각
     */
    private LocalDateTime lastSyncedAt;
}