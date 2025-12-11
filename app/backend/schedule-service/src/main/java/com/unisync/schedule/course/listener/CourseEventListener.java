package com.unisync.schedule.course.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.schedule.course.dto.CourseDisabledMessage;
import com.unisync.schedule.course.service.CourseService;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Course 이벤트 Listener
 * SQS로부터 Course 이벤트(COURSE_DISABLED 등)를 수신하여 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventListener {

    private final SqsAsyncClient sqsAsyncClient;
    private final CourseService courseService;
    private final ObjectMapper objectMapper;

    @Value("${sqs.course-to-schedule-queue}")
    private String queueName;

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    @PostConstruct
    public void startListening() {
        String queueUrl = getQueueUrl();
        log.info("Starting Course Event Listener: queueUrl={}", queueUrl);

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
        log.info("Stopping Course Event Listener");
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
                        log.info("Received {} course event messages from SQS", messages.size());
                        messages.forEach(message -> processMessage(queueUrl, message));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Failed to receive course event messages from SQS", throwable);
                    return null;
                });
    }

    /**
     * 개별 메시지 처리
     */
    private void processMessage(String queueUrl, Message message) {
        try {
            // JSON 파싱
            CourseDisabledMessage event = objectMapper.readValue(
                    message.body(), CourseDisabledMessage.class);

            log.info("Processing course event message: eventType={}, courseId={}, cognitoSub={}",
                    event.getEventType(), event.getCourseId(), event.getCognitoSub());

            // Course 이벤트 처리
            courseService.processCourseEvent(event);

            // 처리 완료 후 메시지 삭제
            deleteMessage(queueUrl, message.receiptHandle());

        } catch (JsonProcessingException e) {
            log.error("Failed to parse course event message: {}", message.body(), e);
            // 파싱 실패 시에도 메시지 삭제 (DLQ로 이동 또는 재시도 방지)
            deleteMessage(queueUrl, message.receiptHandle());
        } catch (Exception e) {
            log.error("Failed to process course event message: {}", message.body(), e);
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
                .thenAccept(response -> log.debug("Course event message deleted from SQS"))
                .exceptionally(throwable -> {
                    log.error("Failed to delete course event message from SQS", throwable);
                    return null;
                });
    }

    /**
     * SQS Queue URL 생성
     */
    private String getQueueUrl() {
        return queueName;
    }
}
