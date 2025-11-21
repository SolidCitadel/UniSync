package com.unisync.user.credentials.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Canvas API 토큰 조회 응답 (내부 API 전용, 복호화된 토큰 포함)")
public class InternalCanvasTokenResponse {

    /**
     * 복호화된 Canvas API 토큰
     * Lambda가 Canvas API를 호출할 때 사용
     */
    @Schema(description = "복호화된 Canvas API 토큰 (Lambda가 Canvas API 호출 시 사용)", example = "1234~ABCDefghIJKLmnopQRSTuvwxYZ0123456789abcdefghijklmnopqrstuvwxyz")
    private String canvasToken;

    /**
     * 토큰이 마지막으로 검증된 시각
     */
    @Schema(description = "토큰이 마지막으로 검증된 시각", example = "2025-01-15T10:30:00")
    private LocalDateTime lastValidatedAt;

    /**
     * Canvas 사용자 ID (외부 시스템 ID)
     */
    @Schema(description = "Canvas 사용자 ID", example = "12345")
    private String externalUserId;

    /**
     * Canvas 로그인 ID (학번 등)
     */
    @Schema(description = "Canvas 로그인 ID (학번 등)", example = "2024123456")
    private String externalUsername;
}
