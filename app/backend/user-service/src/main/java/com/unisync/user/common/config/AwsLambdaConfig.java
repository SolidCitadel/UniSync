package com.unisync.user.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.net.URI;

/**
 * AWS Lambda Client 설정
 * Phase 1: Canvas 수동 동기화를 위한 Lambda 직접 호출
 */
@Configuration
public class AwsLambdaConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.lambda.endpoint-url:#{null}}")
    private String endpointUrl;

    @Value("${aws.access-key-id:test}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:test}")
    private String secretAccessKey;

    @Bean
    public LambdaClient lambdaClient() {
        var builder = LambdaClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                ));

        // LocalStack용 엔드포인트 설정
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }

        return builder.build();
    }
}
