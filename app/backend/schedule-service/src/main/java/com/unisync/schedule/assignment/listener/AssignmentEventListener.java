package com.unisync.schedule.assignment.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.schedule.assignment.dto.AssignmentToScheduleMessage;
import com.unisync.schedule.assignment.service.AssignmentService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Assignment 이벤트 Listener
 * SQS로부터 Assignment → Schedule 변환 이벤트를 수신하여 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentEventListener {

    private final SqsAsyncClient sqsAsyncClient;
    private final AssignmentService assignmentService;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.endpoint:}")
    private String sqsEndpoint;

    @Value("${aws.region}")
    private String region;

    @Value("${sqs.assignment-to-schedule-queue}")
    private String queueName;

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    @PostConstruct
    public void startListening() {
        String queueUrl = getQueueUrl();
        log.info("Starting Assignment Event Listener: queueUrl={}", queueUrl);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        running = true;

        // 5초마다 SQS 폴링
        scheduler.scheduleWithFixedDelay(() -> {
            if (running) {
                pollMessages(queueUrl);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stopListening() {
        log.info("Stopping Assignment Event Listener");
        running = false;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * SQS 메시지 폴링
     */
    private void pollMessages(String queueUrl) {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10) // Long polling
                .build();

        sqsAsyncClient.receiveMessage(receiveRequest)
                .thenAccept(response -> {
                    List<Message> messages = response.messages();
                    if (!messages.isEmpty()) {
                        log.info("Received {} messages from SQS", messages.size());
                        messages.forEach(message -> processMessage(queueUrl, message));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Failed to receive messages from SQS", throwable);
                    return null;
                });
    }

    /**
     * 개별 메시지 처리
     */
    private void processMessage(String queueUrl, Message message) {
        try {
            // JSON 파싱
            AssignmentToScheduleMessage event = objectMapper.readValue(
                    message.body(), AssignmentToScheduleMessage.class);

            log.info("Processing message: eventType={}, assignmentId={}, cognitoSub={}",
                    event.getEventType(), event.getAssignmentId(), event.getCognitoSub());

            // Assignment 이벤트 처리
            assignmentService.processAssignmentEvent(event);

            // 처리 완료 후 메시지 삭제
            deleteMessage(queueUrl, message.receiptHandle());

        } catch (JsonProcessingException e) {
            log.error("Failed to parse message: {}", message.body(), e);
            // 파싱 실패 시에도 메시지 삭제 (DLQ로 이동 또는 재시도 방지)
            deleteMessage(queueUrl, message.receiptHandle());
        } catch (Exception e) {
            log.error("Failed to process message: {}", message.body(), e);
            // 처리 실패 시 메시지 재처리 (visibility timeout 후 재시도)
        }
    }

    /**
     * SQS 메시지 삭제
     */
    private void deleteMessage(String queueUrl, String receiptHandle) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

        sqsAsyncClient.deleteMessage(deleteRequest)
                .thenAccept(response -> log.debug("Message deleted from SQS"))
                .exceptionally(throwable -> {
                    log.error("Failed to delete message from SQS", throwable);
                    return null;
                });
    }

    /**
     * SQS Queue URL 생성
     */
    private String getQueueUrl() {
        // LocalStack: http://localhost:4566/000000000000/queue-name
        // AWS: https://sqs.{region}.amazonaws.com/{accountId}/{queueName}
        if (sqsEndpoint != null && !sqsEndpoint.isEmpty()) {
            return String.format("%s/000000000000/%s", sqsEndpoint, queueName);
        } else {
            // AWS 실제 환경 (accountId는 별도 설정 필요)
            return String.format("https://sqs.%s.amazonaws.com/000000000000/%s", region, queueName);
        }
    }
}
