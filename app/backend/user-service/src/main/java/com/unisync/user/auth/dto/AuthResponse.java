package com.unisync.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String idToken;
    private Integer expiresIn;
    private String tokenType;

    // 사용자 정보
    private String cognitoSub;
    private String email;
    private String name;

    // 응답 메시지
    private String message;
}
