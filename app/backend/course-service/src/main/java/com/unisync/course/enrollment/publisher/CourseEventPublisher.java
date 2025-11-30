package com.unisync.course.enrollment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.enrollment.dto.CourseDisabledEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Course 이벤트 Publisher
 * Course-Service → Schedule-Service (SQS)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventPublisher {

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.endpoint}")
    private String sqsEndpoint;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.sqs.queues.course-to-schedule}")
    private String queueName;

    /**
     * Course Disabled 이벤트를 Schedule-Service로 발행
     *
     * @param event 발행할 이벤트
     */
    public void publishCourseDisabledEvent(CourseDisabledEventDto event) {
        String queueUrl = getQueueUrl();

        try {
            String messageBody = objectMapper.writeValueAsString(event);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            sqsAsyncClient.sendMessage(request)
                    .thenAccept(response -> log.info("✅ Published course disabled event to SQS: courseId={}, cognitoSub={}",
                            event.getCourseId(), event.getCognitoSub()))
                    .exceptionally(throwable -> {
                        log.error("❌ Failed to publish course disabled event: courseId={}, cognitoSub={}",
                                event.getCourseId(), event.getCognitoSub(), throwable);
                        return null;
                    });

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize course disabled event", e);
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
