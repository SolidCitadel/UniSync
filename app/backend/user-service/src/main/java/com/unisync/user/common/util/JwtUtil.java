package com.unisync.user.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * JWT 유틸리티 (cognitoSub 추출용)
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    /**
     * Authorization 헤더에서 cognitoSub 추출
     *
     * @param authorizationHeader "Bearer {token}" 형식
     * @return Cognito Sub
     */
    public String extractCognitoSub(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        return extractCognitoSubFromToken(token);
    }

    /**
     * "Bearer " prefix 제거
     */
    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Authorization header format");
        }
        return authorizationHeader.substring(7);
    }

    /**
     * JWT 토큰에서 cognitoSub claim 추출
     */
    private String extractCognitoSubFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Cognito JWT는 "sub" claim에 cognitoSub 포함
            String cognitoSub = claims.getSubject();

            if (cognitoSub == null) {
                throw new IllegalArgumentException("No 'sub' claim found in JWT");
            }

            return cognitoSub;

        } catch (Exception e) {
            log.error("Failed to parse JWT token", e);
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }
}
