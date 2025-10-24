package com.unisync.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.gateway.config.CognitoConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoJwtVerifier {

    private final CognitoConfig cognitoConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * JWT 토큰 검증 및 Claims 추출
     */
    public Map<String, Object> verify(String token) {
        try {
            // LocalStack 환경에서는 서명 검증 스킵 (개발 편의성)
            if (isLocalStackEnvironment()) {
                log.debug("LocalStack 환경: JWT 서명 검증 스킵");
                return parseWithoutVerification(token);
            }

            // 실제 AWS Cognito 환경에서는 Public Key로 검증
            // TODO: JWKS 엔드포인트에서 Public Key 가져와서 검증
            return parseWithoutVerification(token);

        } catch (Exception e) {
            log.error("JWT 검증 실패: {}", e.getMessage());
            throw new RuntimeException("유효하지 않은 토큰입니다");
        }
    }

    /**
     * JWT 토큰에서 사용자 ID (sub) 추출
     */
    public String extractUserId(Map<String, Object> claims) {
        return (String) claims.get("sub");
    }

    /**
     * JWT 토큰에서 이메일 추출
     */
    public String extractEmail(Map<String, Object> claims) {
        return (String) claims.get("email");
    }

    /**
     * JWT 토큰에서 이름 추출
     */
    public String extractName(Map<String, Object> claims) {
        return (String) claims.get("name");
    }

    /**
     * 서명 검증 없이 Claims 파싱 (LocalStack 개발 환경용)
     */
    private Map<String, Object> parseWithoutVerification(String token) {
        try {
            // JWT를 . 으로 분리
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("JWT 형식이 올바르지 않습니다");
            }

            // Payload(두 번째 부분) 디코딩
            byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(decodedBytes);

            log.debug("JWT Payload: {}", payload);

            // JSON을 Map으로 파싱
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            return claims;

        } catch (Exception e) {
            log.error("JWT 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("토큰 파싱에 실패했습니다");
        }
    }

    /**
     * LocalStack 환경 여부 확인
     */
    private boolean isLocalStackEnvironment() {
        return cognitoConfig.getEndpoint() != null &&
               cognitoConfig.getEndpoint().contains("localhost");
    }

    /**
     * 토큰 유효성 기본 검사
     */
    public boolean isValid(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        String[] parts = token.split("\\.");
        return parts.length == 3;
    }
}
