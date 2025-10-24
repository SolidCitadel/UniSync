package com.unisync.gateway.filter;

import com.unisync.gateway.config.JwtConfig;
import com.unisync.gateway.service.CognitoJwtVerifier;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final CognitoJwtVerifier jwtVerifier;
    private final JwtConfig jwtConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(CognitoJwtVerifier jwtVerifier, JwtConfig jwtConfig) {
        super(Config.class);
        this.jwtVerifier = jwtVerifier;
        this.jwtConfig = jwtConfig;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            log.debug("JWT 인증 필터 실행: {}", path);

            // JWT 검증 제외 경로 확인
            if (isExcludedPath(path)) {
                log.debug("JWT 검증 제외 경로: {}", path);
                return chain.filter(exchange);
            }

            // Authorization 헤더 추출
            String authHeader = request.getHeaders().getFirst("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Authorization 헤더가 없거나 형식이 올바르지 않습니다: {}", path);
                return onError(exchange, "Authorization 헤더가 필요합니다", HttpStatus.UNAUTHORIZED);
            }

            try {
                // Bearer 제거하고 토큰 추출
                String token = authHeader.substring(7);

                // JWT 검증
                if (!jwtVerifier.isValid(token)) {
                    return onError(exchange, "유효하지 않은 토큰 형식입니다", HttpStatus.UNAUTHORIZED);
                }

                Map<String, Object> claims = jwtVerifier.verify(token);

                // Claims에서 사용자 정보 추출
                String cognitoSub = jwtVerifier.extractUserId(claims);  // 실제로는 Cognito Sub
                String email = jwtVerifier.extractEmail(claims);
                String name = jwtVerifier.extractName(claims);

                log.debug("JWT 검증 성공: cognitoSub={}, email={}", cognitoSub, email);

                // 헤더에 사용자 정보 추가 (백엔드 서비스에서 사용)
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-Cognito-Sub", cognitoSub)  // Cognito User Pool의 sub (UUID)
                        .header("X-User-Email", email)
                        .header("X-User-Name", name)
                        .build();

                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(modifiedRequest)
                        .build();

                return chain.filter(modifiedExchange);

            } catch (Exception e) {
                log.error("JWT 검증 실패: {}", e.getMessage());
                return onError(exchange, "토큰 검증에 실패했습니다: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
            }
        };
    }

    /**
     * JWT 검증 제외 경로 확인
     */
    private boolean isExcludedPath(String path) {
        if (jwtConfig.getExcludePaths() == null) {
            return false;
        }

        return jwtConfig.getExcludePaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 에러 응답 반환
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");

        String errorBody = String.format("{\"error\": \"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorBody.getBytes())));
    }

    public static class Config {
        // 필요 시 필터별 설정 추가
    }
}
