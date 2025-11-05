package com.unisync.user.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * AWS SQS 클라이언트 설정
 * LocalStack 또는 실제 AWS SQS에 연결
 */
@Slf4j
@Configuration
public class SqsConfig {

    @Value("${aws.sqs.endpoint}")
    private String sqsEndpoint;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.credentials.access-key}")
    private String accessKey;

    @Value("${aws.credentials.secret-key}")
    private String secretKey;

    @Bean
    public SqsClient sqsClient() {
        log.info("Initializing SQS Client");
        log.info("  - Endpoint: {}", sqsEndpoint);
        log.info("  - Region: {}", awsRegion);

        return SqsClient.builder()
                .endpointOverride(URI.create(sqsEndpoint))
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
    }
}