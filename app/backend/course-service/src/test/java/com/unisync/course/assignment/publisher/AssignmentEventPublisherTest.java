package com.unisync.course.assignment.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.assignment.dto.AssignmentToScheduleEventDto;
import com.unisync.course.assignment.dto.UserAssignmentsBatchEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssignmentEventPublisherTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private ObjectMapper objectMapper;
    private AssignmentEventPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new AssignmentEventPublisher(sqsAsyncClient, objectMapper);

        Field queueNameField = AssignmentEventPublisher.class.getDeclaredField("queueName");
        queueNameField.setAccessible(true);
        queueNameField.set(publisher, "http://localhost:4566/000000000000/courseservice-to-scheduleservice-assignments");

        given(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .willReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("id").build()
                ));
    }

    @Test
    @DisplayName("Batch publish: description은 4KB로 트렁케이트된다")
    void publishAssignmentBatchEvents_TruncatesDescriptionTo4KiB() throws Exception {
        String longDescription = "x".repeat(10_000);
        UserAssignmentsBatchEvent event = UserAssignmentsBatchEvent.builder()
                .eventType("USER_ASSIGNMENTS_CREATED")
                .cognitoSub("user-sub")
                .syncedAt("2025-11-30T12:00:00Z")
                .assignments(List.of(UserAssignmentsBatchEvent.AssignmentPayload.builder()
                        .assignmentId(1L)
                        .canvasAssignmentId(100L)
                        .canvasCourseId(200L)
                        .courseId(300L)
                        .courseName("course")
                        .title("title")
                        .description(longDescription)
                        .dueAt("2025-11-20T23:59:59Z")
                        .pointsPossible(100.0)
                        .build()))
                .build();

        publisher.publishAssignmentBatchEvents(List.of(event));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        then(sqsAsyncClient).should().sendMessage(captor.capture());

        UserAssignmentsBatchEvent parsed = objectMapper.readValue(
                captor.getValue().messageBody(), UserAssignmentsBatchEvent.class);

        String truncated = parsed.getAssignments().get(0).getDescription();
        assertThat(truncated.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(AssignmentEventPublisher.DESCRIPTION_MAX_BYTES);
    }

    @Test
    @DisplayName("Batch publish: 4KB 트렁케이트 후에도 크면 2KB로 한 번 더 줄인다")
    void publishAssignmentBatchEvents_FallbackTo2KiBWhenMessageLarge() throws Exception {
        int assignmentCount = 200;
        String longDescription = "x".repeat(10_000);
        String longTitle = "t".repeat(600);

        List<UserAssignmentsBatchEvent.AssignmentPayload> assignments = IntStream.range(0, assignmentCount)
                .mapToObj(i -> UserAssignmentsBatchEvent.AssignmentPayload.builder()
                        .assignmentId((long) i + 1)
                        .canvasAssignmentId(100_000L + i)
                        .canvasCourseId(2_000L + (i % 10))
                        .courseId(3_000L + (i % 10))
                        .courseName("course")
                        .title(longTitle)
                        .description(longDescription)
                        .dueAt("2025-11-20T23:59:59Z")
                        .pointsPossible(100.0)
                        .build())
                .toList();

        UserAssignmentsBatchEvent event = UserAssignmentsBatchEvent.builder()
                .eventType("USER_ASSIGNMENTS_CREATED")
                .cognitoSub("user-sub")
                .syncedAt("2025-11-30T12:00:00Z")
                .assignments(assignments)
                .build();

        byte[] bodyAt4KiB = objectMapper.writeValueAsBytes(
                AssignmentEventPublisher.sanitizeBatchEvent(event, AssignmentEventPublisher.DESCRIPTION_MAX_BYTES));
        assertThat(bodyAt4KiB.length).isGreaterThan(AssignmentEventPublisher.SOFT_SQS_MESSAGE_BYTES);
        assertThat(bodyAt4KiB.length).isLessThanOrEqualTo(AssignmentEventPublisher.MAX_SQS_MESSAGE_BYTES);

        publisher.publishAssignmentBatchEvents(List.of(event));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        then(sqsAsyncClient).should().sendMessage(captor.capture());

        UserAssignmentsBatchEvent parsed = objectMapper.readValue(
                captor.getValue().messageBody(), UserAssignmentsBatchEvent.class);

        String truncated = parsed.getAssignments().get(0).getDescription();
        assertThat(truncated.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(AssignmentEventPublisher.DESCRIPTION_FALLBACK_BYTES);
    }

    @Test
    @DisplayName("Single assignment publish: description은 4KB로 트렁케이트된다")
    void publishAssignmentEvents_TruncatesDescriptionTo4KiB() throws Exception {
        String longDescription = "x".repeat(10_000);
        AssignmentToScheduleEventDto event = AssignmentToScheduleEventDto.builder()
                .eventType("ASSIGNMENT_CREATED")
                .assignmentId(1L)
                .cognitoSub("user-sub")
                .canvasAssignmentId(100L)
                .canvasCourseId(200L)
                .title("title")
                .description(longDescription)
                .dueAt(LocalDateTime.of(2025, 11, 20, 23, 59, 59))
                .pointsPossible(100)
                .courseId(300L)
                .courseName("course")
                .build();

        publisher.publishAssignmentEvents(List.of(event));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        then(sqsAsyncClient).should().sendMessage(captor.capture());

        AssignmentToScheduleEventDto parsed = objectMapper.readValue(
                captor.getValue().messageBody(), AssignmentToScheduleEventDto.class);

        assertThat(parsed.getDescription().getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(AssignmentEventPublisher.DESCRIPTION_MAX_BYTES);
    }
}
