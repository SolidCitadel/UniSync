package com.unisync.course.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LocalStack SQS 연결 테스트
 * 통합 테스트 실행 전 LocalStack SQS가 정상 동작하는지 검증
 */
class SqsConnectionTest {

    @Test
    @DisplayName("LocalStack SQS 연결 및 큐 생성/조회 테스트")
    void testLocalStackSqsConnection() {
        // given: LocalStack SQS Client 생성
        SqsClient sqsClient = SqsClient.builder()
            .endpointOverride(URI.create("http://localhost:4566"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")
            ))
            .build();

        // when: 테스트 큐 생성
        String queueName = "test-connection-queue-" + System.currentTimeMillis();
        CreateQueueResponse createResponse = sqsClient.createQueue(
            CreateQueueRequest.builder()
                .queueName(queueName)
                .build()
        );

        // then: 큐 URL 확인
        assertThat(createResponse.queueUrl()).isNotNull();
        assertThat(createResponse.queueUrl()).contains(queueName);
        System.out.println("✅ 큐 생성 성공: " + createResponse.queueUrl());

        // when: 큐 목록 조회
        ListQueuesResponse listResponse = sqsClient.listQueues();

        // then: 생성한 큐가 목록에 포함되어 있는지 확인
        assertThat(listResponse.queueUrls()).isNotEmpty();
        assertThat(listResponse.queueUrls()).anyMatch(url -> url.contains(queueName));
        System.out.println("✅ 큐 목록 조회 성공: " + listResponse.queueUrls().size() + "개 큐 존재");

        // when: 메시지 발송
        SendMessageResponse sendResponse = sqsClient.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(createResponse.queueUrl())
                .messageBody("{\"test\": \"message\"}")
                .build()
        );

        // then: 메시지 ID 확인
        assertThat(sendResponse.messageId()).isNotNull();
        System.out.println("✅ 메시지 발송 성공: " + sendResponse.messageId());

        // when: 메시지 수신
        ReceiveMessageResponse receiveResponse = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(createResponse.queueUrl())
                .maxNumberOfMessages(1)
                .build()
        );

        // then: 메시지 수신 확인
        assertThat(receiveResponse.messages()).hasSize(1);
        assertThat(receiveResponse.messages().get(0).body()).isEqualTo("{\"test\": \"message\"}");
        System.out.println("✅ 메시지 수신 성공: " + receiveResponse.messages().get(0).body());

        // cleanup: 테스트 큐 삭제
        sqsClient.deleteQueue(
            DeleteQueueRequest.builder()
                .queueUrl(createResponse.queueUrl())
                .build()
        );
        System.out.println("✅ 테스트 큐 삭제 완료");
    }

    @Test
    @DisplayName("assignment-events-queue 큐 존재 여부 확인")
    void testAssignmentEventsQueueExists() {
        // given: LocalStack SQS Client 생성
        SqsClient sqsClient = SqsClient.builder()
            .endpointOverride(URI.create("http://localhost:4566"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")
            ))
            .build();

        // when: 큐 목록 조회
        ListQueuesResponse listResponse = sqsClient.listQueues();

        // then: assignment-events-queue 존재 확인
        System.out.println("📋 존재하는 큐 목록:");
        listResponse.queueUrls().forEach(url -> System.out.println("  - " + url));

        boolean hasAssignmentQueue = listResponse.queueUrls().stream()
            .anyMatch(url -> url.contains("assignment-events-queue"));

        if (hasAssignmentQueue) {
            System.out.println("✅ assignment-events-queue가 이미 존재합니다.");
        } else {
            System.out.println("⚠️ assignment-events-queue가 존재하지 않습니다. 생성합니다...");

            // assignment-events-queue 생성
            CreateQueueResponse createResponse = sqsClient.createQueue(
                CreateQueueRequest.builder()
                    .queueName("assignment-events-queue")
                    .build()
            );

            System.out.println("✅ assignment-events-queue 생성 완료: " + createResponse.queueUrl());
        }
    }
}