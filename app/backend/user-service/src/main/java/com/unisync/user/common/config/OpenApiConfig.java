package com.unisync.user.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
                        .title("User Service API")
                        .version("1.0.0")
                        .description("사용자, 인증, 자격 증명 관리 API"))
                .servers(List.of(
                        new Server()
                                .url(serverUrl)
                                .description("API Gateway")
                ));
    }
}