package com.unisync.gateway.config;

import com.unisync.gateway.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API Gateway Routes Configuration
 *
 * Routes 설정을 Java Config로 관리하여 YAML 중복 제거
 * 환경별 차이(URI)는 프로퍼티로 주입
 */
@Configuration
public class GatewayRoutesConfig {

    @Value("${services.user-service.url}")
    private String userServiceUrl;

    @Value("${services.course-service.url}")
    private String courseServiceUrl;

    @Value("${services.schedule-service.url}")
    private String scheduleServiceUrl;

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder, JwtAuthenticationFilter jwtAuthFilter) {
        return builder.routes()
                // User Service (사용자/인증/소셜)
                .route("user-service", r -> r
                        .path(
                            "/api/v1/auth/**",
                            "/api/v1/users/**",
                            "/api/v1/friends/**",
                            "/api/v1/credentials/**",
                            "/api/v1/integrations/**"
                        )
                        .filters(f -> f
                                .filter(jwtAuthFilter.apply(new JwtAuthenticationFilter.Config()))
                                .rewritePath("/api/v1/(?<segment>.*)", "/${segment}")
                        )
                        .uri(userServiceUrl)
                )

                // Course Service (Canvas 학업 데이터: 과목/과제/Task/공지/동기화)
                .route("course-service", r -> r
                        .path(
                            "/api/v1/courses/**",
                            "/api/v1/assignments/**",
                            "/api/v1/tasks/**",
                            "/api/v1/notices/**",
                            "/api/v1/sync/**"
                        )
                        .filters(f -> f
                                .filter(jwtAuthFilter.apply(new JwtAuthenticationFilter.Config()))
                                .rewritePath("/api/v1/(?<segment>.*)", "/${segment}")
                        )
                        .uri(courseServiceUrl)
                )

                // Schedule Service (일정 + 할일)
                .route("schedule-service", r -> r
                        .path(
                            "/api/v1/schedules/**",
                            "/api/v1/todos/**",
                            "/api/v1/categories/**"
                        )
                        .filters(f -> f
                                .filter(jwtAuthFilter.apply(new JwtAuthenticationFilter.Config()))
                                .rewritePath("/api/v1/(?<segment>.*)", "/${segment}")
                        )
                        .uri(scheduleServiceUrl)
                )

                .build();
    }
}
