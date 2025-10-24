package com.unisync.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {
    private List<String> excludePaths;
}
