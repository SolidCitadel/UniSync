package com.unisync.user.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.net.URI;
import java.time.Duration;

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
        // HTTP 클라이언트 설정: 타임아웃 증가
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .socketTimeout(Duration.ofSeconds(150))  // Lambda timeout(120초) + 여유
                .connectionTimeout(Duration.ofSeconds(30))  // NAT Gateway 경유 지연 대응
                .build();

        // 클라이언트 오버라이드 설정
        ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.none())  // 재시도 비활성화
                .apiCallTimeout(Duration.ofSeconds(150))  // API 호출 전체 타임아웃
                .build();

        var builder = LambdaClient.builder()
                .region(Region.of(region))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create())
                .httpClient(httpClient)
                .overrideConfiguration(clientConfig);

        // LocalStack용 엔드포인트 설정
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }

        return builder.build();
    }
}
