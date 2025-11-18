package com.unisync.user.credentials.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 내부 API용 Canvas 토큰 응답 DTO
 * Lambda 및 다른 마이크로서비스에서 Canvas API 호출 시 사용
 *
 * 외부 API(CanvasTokenResponse)와 달리 복호화된 토큰을 포함합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalCanvasTokenResponse {

    /**
     * 복호화된 Canvas API 토큰
     * Lambda가 Canvas API를 호출할 때 사용
     */
    private String canvasToken;

    /**
     * 토큰이 마지막으로 검증된 시각
     */
    private LocalDateTime lastValidatedAt;

    /**
     * Canvas 사용자 ID (외부 시스템 ID)
     */
    private String externalUserId;

    /**
     * Canvas 로그인 ID (학번 등)
     */
    private String externalUsername;
}
