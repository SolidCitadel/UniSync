package com.unisync.course.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.net.URI;

/**
 * SQS Publisher 설정
 * Course-Service -> Schedule-Service로 Assignment 이벤트 발행
 */
@Configuration
public class SqsPublisherConfig {

    @Value("${AWS_SQS_ENDPOINT:}")
    private String sqsEndpoint;

    @Value("${AWS_REGION:ap-northeast-2}")
    private String region;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // LocalStack 등 커스텀 엔드포인트 사용 시 override
        if (sqsEndpoint != null && !sqsEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        return builder.build();
    }
}
