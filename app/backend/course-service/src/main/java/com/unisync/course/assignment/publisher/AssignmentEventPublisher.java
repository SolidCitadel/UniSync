package com.unisync.course.assignment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.assignment.dto.AssignmentToScheduleEventDto;
import com.unisync.course.assignment.dto.UserAssignmentsBatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

/**
 * Assignment 이벤트 Publisher
 * Course-Service -> Schedule-Service (SQS)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentEventPublisher {

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.endpoint}")
    private String sqsEndpoint;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.sqs.queues.assignment-to-schedule}")
    private String queueName;

    /**
     * 사용자별 assignments 배치 이벤트를 Schedule-Service로 발행
     *
     * @param events 사용자당 1개 배치 이벤트 리스트
     */
    public void publishAssignmentBatchEvents(List<UserAssignmentsBatchEvent> events) {
        String queueUrl = getQueueUrl();

        for (UserAssignmentsBatchEvent event : events) {
            try {
                String messageBody = objectMapper.writeValueAsString(event);

                SendMessageRequest request = SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(messageBody)
                        .build();

                sqsAsyncClient.sendMessage(request)
                        .thenAccept(response -> log.info("Published assignment batch to SQS: eventType={}, cognitoSub={}, assignments={}",
                                event.getEventType(), event.getCognitoSub(),
                                event.getAssignments() != null ? event.getAssignments().size() : 0))
                        .exceptionally(throwable -> {
                            log.error("Failed to publish assignment batch: cognitoSub={}",
                                    event.getCognitoSub(), throwable);
                            return null;
                        });

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize assignment batch event", e);
            }
        }
    }

    /**
     * 개별 Assignment 이벤트를 Schedule-Service로 발행
     * (새 사용자에게 기존 Assignment들을 전달할 때 사용)
     *
     * @param events Assignment 이벤트 리스트
     */
    public void publishAssignmentEvents(List<AssignmentToScheduleEventDto> events) {
        String queueUrl = getQueueUrl();

        for (AssignmentToScheduleEventDto event : events) {
            try {
                String messageBody = objectMapper.writeValueAsString(event);

                SendMessageRequest request = SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(messageBody)
                        .build();

                sqsAsyncClient.sendMessage(request)
                        .thenAccept(response -> log.info("Published assignment event to SQS: eventType={}, assignmentId={}, cognitoSub={}",
                                event.getEventType(), event.getAssignmentId(), event.getCognitoSub()))
                        .exceptionally(throwable -> {
                            log.error("Failed to publish assignment event: assignmentId={}",
                                    event.getAssignmentId(), throwable);
                            return null;
                        });

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize assignment event", e);
            }
        }
    }

    /**
     * SQS Queue URL 생성
     */
    private String getQueueUrl() {
        // queueName이 이미 전체 URL인 경우 그대로 사용
        if (queueName.startsWith("https://")) {
            return queueName;
        }

        // LocalStack: http://localhost:4566/000000000000/queue-name
        if (sqsEndpoint != null && !sqsEndpoint.isEmpty() && !sqsEndpoint.contains("sqs.ap-northeast-2.amazonaws.com")) {
            return String.format("%s/000000000000/%s", sqsEndpoint, queueName);
        }

        // AWS 실제 환경 - queueName만 있으면 전체 URL 구성 불가 (accountId 필요)
        // 환경변수로 전체 URL을 주입해야 함
        return queueName;
    }
}
