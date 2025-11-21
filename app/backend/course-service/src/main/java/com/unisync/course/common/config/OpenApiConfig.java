package com.unisync.course.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 설정
 * API Gateway를 통한 접근 시 올바른 경로가 표시되도록 서버 URL 설정
 */
@Configuration
public class OpenApiConfig {

    @Value("${springdoc.server.url:/api}")
    private String serverUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Course Service API")
                        .version("1.0.0")
                        .description("Canvas LMS 과목, 과제, 수강 정보 관리 API\n\n"
                                + "**인증**: API Gateway에 JWT Bearer token을 전송하면, "
                                + "Gateway가 JWT를 검증하고 백엔드로 전달합니다.\n\n"
                                + "**로그인**: User Service의 `/api/v1/auth/signin`에서 JWT 토큰을 발급받을 수 있습니다."))
                .servers(List.of(
                        new Server()
                                .url(serverUrl)
                                .description("API Gateway")
                ))
                .addSecurityItem(new SecurityRequirement().addList("JWT Bearer"))
                .components(new Components()
                        .addSecuritySchemes("JWT Bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("API Gateway로 전송할 JWT 토큰")));
    }
}