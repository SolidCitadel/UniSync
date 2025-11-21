package com.unisync.schedule.common.config;

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
                        .title("Schedule Service API")
                        .version("1.0.0")
                        .description("일정(Schedule), 할일(Todo), 카테고리 관리 API"))
                .servers(List.of(
                        new Server()
                                .url(serverUrl)
                                .description("API Gateway")
                ));
    }
}