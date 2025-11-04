package com.unisync.user.common.config;

import com.unisync.shared.security.ServiceAuthValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    public ServiceAuthValidator serviceAuthValidator(
            @Value("${unisync.api-keys.canvas-sync-lambda}") String canvasSyncApiKey,
            @Value("${unisync.api-keys.llm-lambda}") String llmApiKey
    ) {
        Map<String, String> apiKeys = Map.of(
                canvasSyncApiKey, "canvas-sync-lambda",
                llmApiKey, "llm-lambda"
        );
        return new ServiceAuthValidator(apiKeys);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}