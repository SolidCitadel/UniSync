package com.unisync.schedule.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

/**
 * SQS Consumer 설정
 * Course-Service -> Schedule-Service로부터 Assignment 이벤트 수신
 */
@Configuration
public class SqsConsumerConfig {

    @Value("${aws.sqs.endpoint:}")
    private String sqsEndpoint;

    @Value("${aws.region}")
    private String region;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // LocalStack 사용 시 endpoint 설정 (AWS 실제 엔드포인트가 아닐 때만)
        if (sqsEndpoint != null && !sqsEndpoint.isBlank() && !sqsEndpoint.contains("sqs.ap-northeast-2.amazonaws.com")) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        return builder.build();
    }
}
