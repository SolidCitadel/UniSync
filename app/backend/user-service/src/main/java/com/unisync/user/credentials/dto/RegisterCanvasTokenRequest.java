package com.unisync.user.credentials.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Canvas API 토큰 등록 요청")
public class RegisterCanvasTokenRequest {

    @NotBlank(message = "Canvas token is required")
    @Schema(
        description = "Canvas LMS에서 발급받은 API 토큰. Canvas > Settings > New Access Token에서 발급 가능",
        example = "1234~ABCDefghIJKLmnopQRSTuvwxYZ0123456789abcdefghijklmnopqrstuvwxyz",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String canvasToken;
}