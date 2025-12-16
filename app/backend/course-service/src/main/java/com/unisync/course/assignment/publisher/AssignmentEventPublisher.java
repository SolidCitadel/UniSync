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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

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

    @Value("${sqs.assignment-to-schedule-queue}")
    private String queueName;

    static final int MAX_SQS_MESSAGE_BYTES = 1_048_576;
    static final int SOFT_SQS_MESSAGE_BYTES = 950 * 1024;
    static final int DESCRIPTION_MAX_BYTES = 4 * 1024;
    static final int DESCRIPTION_FALLBACK_BYTES = 2 * 1024;

    /**
     * 사용자별 assignments 배치 이벤트를 Schedule-Service로 발행
     *
     * @param events 사용자당 1개 배치 이벤트 리스트
     */
    public void publishAssignmentBatchEvents(List<UserAssignmentsBatchEvent> events) {
        String queueUrl = getQueueUrl();

        for (UserAssignmentsBatchEvent event : events) {
            try {
                UserAssignmentsBatchEvent sanitized = sanitizeBatchEvent(event, DESCRIPTION_MAX_BYTES);
                byte[] bodyBytes = objectMapper.writeValueAsBytes(sanitized);

                if (bodyBytes.length > SOFT_SQS_MESSAGE_BYTES) {
                    sanitized = sanitizeBatchEvent(event, DESCRIPTION_FALLBACK_BYTES);
                    bodyBytes = objectMapper.writeValueAsBytes(sanitized);
                }

                if (bodyBytes.length > MAX_SQS_MESSAGE_BYTES) {
                    sanitized = dropBatchDescriptions(event);
                    bodyBytes = objectMapper.writeValueAsBytes(sanitized);
                }

                if (bodyBytes.length > MAX_SQS_MESSAGE_BYTES) {
                    log.error("Skipping assignment batch publish: message too large ({} bytes) cognitoSub={}",
                            bodyBytes.length, event.getCognitoSub());
                    continue;
                }

                String messageBody = new String(bodyBytes, StandardCharsets.UTF_8);

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
                AssignmentToScheduleEventDto sanitized = sanitizeAssignmentEvent(event);
                String messageBody = objectMapper.writeValueAsString(sanitized);

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
     * SQS Queue URL 생성 (절대 URL 주입 전제)
     */
    private String getQueueUrl() {
        return queueName;
    }

    static UserAssignmentsBatchEvent sanitizeBatchEvent(UserAssignmentsBatchEvent event, int maxDescriptionBytes) {
        if (event == null) {
            return null;
        }
        List<UserAssignmentsBatchEvent.AssignmentPayload> assignments = event.getAssignments();
        if (assignments == null || assignments.isEmpty()) {
            return event;
        }

        List<UserAssignmentsBatchEvent.AssignmentPayload> sanitizedAssignments = assignments.stream()
                .map(payload -> sanitizeBatchPayload(payload, maxDescriptionBytes))
                .toList();

        if (sanitizedAssignments.equals(assignments)) {
            return event;
        }

        return UserAssignmentsBatchEvent.builder()
                .eventType(event.getEventType())
                .cognitoSub(event.getCognitoSub())
                .syncedAt(event.getSyncedAt())
                .assignments(sanitizedAssignments)
                .build();
    }

    static UserAssignmentsBatchEvent dropBatchDescriptions(UserAssignmentsBatchEvent event) {
        if (event == null) {
            return null;
        }
        List<UserAssignmentsBatchEvent.AssignmentPayload> assignments = event.getAssignments();
        if (assignments == null || assignments.isEmpty()) {
            return event;
        }

        List<UserAssignmentsBatchEvent.AssignmentPayload> sanitizedAssignments = assignments.stream()
                .map(AssignmentEventPublisher::dropBatchPayloadDescription)
                .toList();

        if (sanitizedAssignments.equals(assignments)) {
            return event;
        }

        return UserAssignmentsBatchEvent.builder()
                .eventType(event.getEventType())
                .cognitoSub(event.getCognitoSub())
                .syncedAt(event.getSyncedAt())
                .assignments(sanitizedAssignments)
                .build();
    }

    private static UserAssignmentsBatchEvent.AssignmentPayload sanitizeBatchPayload(
            UserAssignmentsBatchEvent.AssignmentPayload payload,
            int maxDescriptionBytes
    ) {
        if (payload == null) {
            return null;
        }

        String description = payload.getDescription();
        String sanitizedDescription = truncateUtf8(description, maxDescriptionBytes);
        if (Objects.equals(description, sanitizedDescription)) {
            return payload;
        }

        return UserAssignmentsBatchEvent.AssignmentPayload.builder()
                .assignmentId(payload.getAssignmentId())
                .canvasAssignmentId(payload.getCanvasAssignmentId())
                .canvasCourseId(payload.getCanvasCourseId())
                .courseId(payload.getCourseId())
                .courseName(payload.getCourseName())
                .title(payload.getTitle())
                .description(sanitizedDescription)
                .dueAt(payload.getDueAt())
                .pointsPossible(payload.getPointsPossible())
                .build();
    }

    private static UserAssignmentsBatchEvent.AssignmentPayload dropBatchPayloadDescription(
            UserAssignmentsBatchEvent.AssignmentPayload payload
    ) {
        if (payload == null) {
            return null;
        }
        if (payload.getDescription() == null) {
            return payload;
        }

        return UserAssignmentsBatchEvent.AssignmentPayload.builder()
                .assignmentId(payload.getAssignmentId())
                .canvasAssignmentId(payload.getCanvasAssignmentId())
                .canvasCourseId(payload.getCanvasCourseId())
                .courseId(payload.getCourseId())
                .courseName(payload.getCourseName())
                .title(payload.getTitle())
                .description(null)
                .dueAt(payload.getDueAt())
                .pointsPossible(payload.getPointsPossible())
                .build();
    }

    private static AssignmentToScheduleEventDto sanitizeAssignmentEvent(AssignmentToScheduleEventDto event) {
        if (event == null) {
            return null;
        }
        String description = event.getDescription();
        String sanitizedDescription = truncateUtf8(description, DESCRIPTION_MAX_BYTES);
        if (Objects.equals(description, sanitizedDescription)) {
            return event;
        }

        return AssignmentToScheduleEventDto.builder()
                .eventType(event.getEventType())
                .assignmentId(event.getAssignmentId())
                .cognitoSub(event.getCognitoSub())
                .canvasAssignmentId(event.getCanvasAssignmentId())
                .canvasCourseId(event.getCanvasCourseId())
                .title(event.getTitle())
                .description(sanitizedDescription)
                .dueAt(event.getDueAt())
                .pointsPossible(event.getPointsPossible())
                .courseId(event.getCourseId())
                .courseName(event.getCourseName())
                .build();
    }

    static String truncateUtf8(String value, int maxBytes) {
        if (value == null || value.isEmpty() || maxBytes <= 0) {
            return value;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }

        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            int midBytes = value.substring(0, mid).getBytes(StandardCharsets.UTF_8).length;
            if (midBytes <= maxBytes) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        if (low <= 0) {
            return "";
        }
        return value.substring(0, low);
    }
}
