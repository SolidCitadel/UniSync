package com.unisync.user.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.net.URI;

@Configuration
public class AwsCognitoConfig {

    @Value("${aws.cognito.region}")
    private String region;

    @Value("${aws.cognito.endpoint:}")
    private String endpoint;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${aws.cognito.client-id}")
    private String clientId;

    @Bean
    public CognitoIdentityProviderClient cognitoClient() {
        var builder = CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")
                ));

        // LocalStack을 사용하는 경우 endpoint 설정
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    public String getUserPoolId() {
        return userPoolId;
    }

    public String getClientId() {
        return clientId;
    }
}
