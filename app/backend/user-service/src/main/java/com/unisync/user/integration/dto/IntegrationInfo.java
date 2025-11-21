package com.unisync.user.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "외부 서비스 연동 정보")
public class IntegrationInfo {

    /**
     * 연동 여부
     */
    @Schema(description = "연동 여부", example = "true")
    private Boolean isConnected;

    /**
     * 외부 서비스의 사용자명 (Canvas login_id, Google email 등)
     */
    @Schema(description = "외부 서비스의 사용자명 (Canvas 학번, Google 이메일 등)", example = "2024123456")
    private String externalUsername;

    /**
     * 마지막 검증 시각
     */
    @Schema(description = "토큰이 마지막으로 검증된 시각", example = "2025-01-15T10:30:00")
    private LocalDateTime lastValidatedAt;

    /**
     * 마지막 동기화 시각
     */
    @Schema(description = "데이터가 마지막으로 동기화된 시각", example = "2025-01-15T10:35:00")
    private LocalDateTime lastSyncedAt;
}