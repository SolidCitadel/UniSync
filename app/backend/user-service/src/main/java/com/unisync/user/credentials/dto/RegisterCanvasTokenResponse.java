package com.unisync.user.credentials.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Canvas API 토큰 등록 응답")
public class RegisterCanvasTokenResponse {

    @Schema(description = "등록 성공 여부", example = "true")
    private boolean success;

    @Schema(description = "응답 메시지", example = "Canvas 토큰이 성공적으로 등록되었습니다")
    private String message;
}