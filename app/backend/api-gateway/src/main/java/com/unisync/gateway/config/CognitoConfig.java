package com.unisync.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aws.cognito")
@Data
public class CognitoConfig {
    private String userPoolId;
    private String region;
    private String endpoint;

    public String getIssuer() {
        // LocalStack 환경
        if (endpoint != null && endpoint.contains("localhost")) {
            return endpoint + "/" + userPoolId;
        }
        // 실제 AWS Cognito
        return "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
    }
}
