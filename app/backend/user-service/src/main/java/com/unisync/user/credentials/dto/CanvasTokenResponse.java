package com.unisync.user.credentials.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Canvas API 토큰 조회 응답 (암호화된 토큰)")
public class CanvasTokenResponse {

    @Schema(description = "암호화된 Canvas API 토큰 (보안을 위해 마스킹됨)", example = "****************************xyz")
    private String canvasToken;

    @Schema(description = "토큰이 마지막으로 검증된 시각", example = "2025-01-15T10:30:00")
    private LocalDateTime lastValidatedAt;
}