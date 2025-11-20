package com.unisync.schedule.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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

    @Value("${aws.access-key-id}")
    private String accessKeyId;

    @Value("${aws.secret-access-key}")
    private String secretAccessKey;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                ));

        // LocalStack 사용 시 endpoint 설정
        if (sqsEndpoint != null && !sqsEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        return builder.build();
    }
}
