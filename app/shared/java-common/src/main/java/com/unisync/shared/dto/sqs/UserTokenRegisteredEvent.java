package com.unisync.shared.dto.sqs;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 Canvas 토큰을 등록했을 때 발행되는 SQS 이벤트
 *
 * Queue: user-token-registered-queue
 * Publisher: User-Service
 * Consumer: Canvas-Sync-Lambda (initial_sync_handler)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTokenRegisteredEvent {

    /**
     * Cognito 사용자 ID
     */
    private String cognitoSub;

    /**
     * 연동 서비스 제공자 (예: "CANVAS", "GOOGLE_CALENDAR")
     */
    private String provider;

    /**
     * 토큰 등록 시간
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime registeredAt;

    /**
     * Canvas 사용자 ID (Canvas API에서 가져온 external user ID)
     */
    private String externalUserId;

    /**
     * Canvas 사용자명 (Canvas API에서 가져온 login_id)
     */
    private String externalUsername;
}