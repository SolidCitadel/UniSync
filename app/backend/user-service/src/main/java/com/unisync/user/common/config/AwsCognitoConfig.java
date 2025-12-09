package com.unisync.user.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
@RequiredArgsConstructor
public class AwsCognitoConfig {

    private final CognitoProperties cognitoProperties;

    @Bean
    public CognitoIdentityProviderClient cognitoClient() {
        var builder = CognitoIdentityProviderClient.builder()
                .region(Region.of(cognitoProperties.getRegion()))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create());

        // LocalStack을 사용하는 경우 endpoint 설정
        String endpoint = cognitoProperties.getEndpoint();
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    public String getUserPoolId() {
        return cognitoProperties.getUserPoolId();
    }

    public String getClientId() {
        return cognitoProperties.getClientId();
    }
}
