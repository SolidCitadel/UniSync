package com.unisync.user.credentials.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCanvasTokenResponse {

    private boolean success;
    private String message;
}