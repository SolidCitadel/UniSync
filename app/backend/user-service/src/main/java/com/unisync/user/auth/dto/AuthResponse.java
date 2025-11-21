package com.unisync.user.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "인증 응답 (회원가입/로그인)")
public class AuthResponse {

    @Schema(description = "액세스 토큰 (API 요청 시 사용)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "리프레시 토큰 (액세스 토큰 갱신용)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "ID 토큰 (사용자 정보 포함)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String idToken;

    @Schema(description = "토큰 만료 시간 (초)", example = "3600")
    private Integer expiresIn;

    @Schema(description = "토큰 타입", example = "Bearer")
    private String tokenType;

    // 사용자 정보
    @Schema(description = "Cognito 사용자 고유 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    @Schema(description = "사용자 이메일", example = "user@example.com")
    private String email;

    @Schema(description = "사용자 이름", example = "홍길동")
    private String name;

    // 응답 메시지
    @Schema(description = "응답 메시지", example = "회원가입이 완료되었습니다")
    private String message;
}
