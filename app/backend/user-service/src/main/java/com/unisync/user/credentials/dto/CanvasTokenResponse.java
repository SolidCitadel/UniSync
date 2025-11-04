package com.unisync.user.credentials.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasTokenResponse {

    private String canvasToken;
    private LocalDateTime lastValidatedAt;
}