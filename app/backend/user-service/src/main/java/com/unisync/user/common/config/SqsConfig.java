package com.unisync.user.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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

    @Value("${AWS_SQS_ENDPOINT:}")
    private String sqsEndpoint;

    @Value("${AWS_REGION:ap-northeast-2}")
    private String awsRegion;

    @Bean
    public SqsClient sqsClient() {
        log.info("Initializing SQS Client");
        log.info("  - Endpoint: {}", sqsEndpoint);
        log.info("  - Region: {}", awsRegion);

        var builder = SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (sqsEndpoint != null && !sqsEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        return builder.build();
    }
}
