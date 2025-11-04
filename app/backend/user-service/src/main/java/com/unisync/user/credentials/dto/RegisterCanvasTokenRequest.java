package com.unisync.user.credentials.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCanvasTokenRequest {

    @NotBlank(message = "Canvas token is required")
    private String canvasToken;
}