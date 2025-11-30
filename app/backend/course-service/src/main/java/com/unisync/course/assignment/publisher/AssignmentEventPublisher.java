package com.unisync.course.assignment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.assignment.dto.AssignmentToScheduleEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

/**
 * Assignment 이벤트 Publisher
 * Course-Service → Schedule-Service (SQS)
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
     * Assignment 이벤트를 Schedule-Service로 발행
     *
     * @param events 발행할 이벤트 리스트
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
                        .thenAccept(response -> log.info("✅ Published assignment event to SQS: eventType={}, assignmentId={}, cognitoSub={}",
                                event.getEventType(), event.getAssignmentId(), event.getCognitoSub()))
                        .exceptionally(throwable -> {
                            log.error("❌ Failed to publish assignment event: eventType={}, assignmentId={}, cognitoSub={}",
                                    event.getEventType(), event.getAssignmentId(), event.getCognitoSub(), throwable);
                            return null;
                        });

            } catch (JsonProcessingException e) {
                log.error("❌ Failed to serialize assignment event", e);
            }
        }
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
