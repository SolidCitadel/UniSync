package com.unisync.course.assignment.listener;

import com.unisync.course.assignment.service.AssignmentService;
import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * AssignmentEventListener 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentEventListener 단위 테스트")
class AssignmentEventListenerTest {

    @Mock
    private AssignmentService assignmentService;

    @InjectMocks
    private AssignmentEventListener assignmentEventListener;

    private AssignmentEventMessage createMessage;
    private AssignmentEventMessage updateMessage;

    @BeforeEach
    void setUp() {
        // ASSIGNMENT_CREATED 메시지
        createMessage = AssignmentEventMessage.builder()
                .eventType("ASSIGNMENT_CREATED")
                .canvasAssignmentId(123456L)
                .canvasCourseId(789L)
                .title("중간고사 프로젝트")
                .description("Spring Boot로 REST API 구현")
                .dueAt(LocalDateTime.of(2025, 4, 15, 23, 59, 59))
                .pointsPossible(100)
                .submissionTypes("online_upload")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // ASSIGNMENT_UPDATED 메시지
        updateMessage = AssignmentEventMessage.builder()
                .eventType("ASSIGNMENT_UPDATED")
                .canvasAssignmentId(123456L)
                .canvasCourseId(789L)
                .title("중간고사 프로젝트 (수정됨)")
                .description("요구사항 변경")
                .dueAt(LocalDateTime.of(2025, 4, 20, 23, 59, 59))
                .pointsPossible(120)
                .submissionTypes("online_upload,online_text_entry")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========================================
    // ASSIGNMENT_CREATED 이벤트 테스트
    // ========================================

    @Test
    @DisplayName("ASSIGNMENT_CREATED 이벤트 수신 시 createAssignment 호출")
    void receiveAssignmentEvent_Created_CallsCreateAssignment() {
        // Given
        willDoNothing().given(assignmentService).createAssignment(createMessage);

        // When
        assignmentEventListener.receiveAssignmentEvent(createMessage);

        // Then
        then(assignmentService).should().createAssignment(createMessage);
        then(assignmentService).should(never()).updateAssignment(createMessage);
    }

    @Test
    @DisplayName("ASSIGNMENT_CREATED 처리 중 예외 발생 시 재전파")
    void receiveAssignmentEvent_Created_Exception_Rethrows() {
        // Given
        RuntimeException exception = new RuntimeException("DB 연결 오류");
        willThrow(exception).given(assignmentService).createAssignment(createMessage);

        // When & Then
        assertThatThrownBy(() -> assignmentEventListener.receiveAssignmentEvent(createMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 연결 오류");

        then(assignmentService).should().createAssignment(createMessage);
    }

    // ========================================
    // ASSIGNMENT_UPDATED 이벤트 테스트
    // ========================================

    @Test
    @DisplayName("ASSIGNMENT_UPDATED 이벤트 수신 시 updateAssignment 호출")
    void receiveAssignmentEvent_Updated_CallsUpdateAssignment() {
        // Given
        willDoNothing().given(assignmentService).updateAssignment(updateMessage);

        // When
        assignmentEventListener.receiveAssignmentEvent(updateMessage);

        // Then
        then(assignmentService).should().updateAssignment(updateMessage);
        then(assignmentService).should(never()).createAssignment(updateMessage);
    }

    @Test
    @DisplayName("ASSIGNMENT_UPDATED 처리 중 예외 발생 시 재전파")
    void receiveAssignmentEvent_Updated_Exception_Rethrows() {
        // Given
        RuntimeException exception = new RuntimeException("과제를 찾을 수 없습니다");
        willThrow(exception).given(assignmentService).updateAssignment(updateMessage);

        // When & Then
        assertThatThrownBy(() -> assignmentEventListener.receiveAssignmentEvent(updateMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("과제를 찾을 수 없습니다");

        then(assignmentService).should().updateAssignment(updateMessage);
    }

    // ========================================
    // 알 수 없는 이벤트 타입 테스트
    // ========================================

    @Test
    @DisplayName("알 수 없는 이벤트 타입 수신 시 아무 작업도 수행하지 않음")
    void receiveAssignmentEvent_UnknownType_NoAction() {
        // Given
        AssignmentEventMessage unknownMessage = AssignmentEventMessage.builder()
                .eventType("UNKNOWN_EVENT")
                .canvasAssignmentId(123456L)
                .canvasCourseId(789L)
                .title("테스트")
                .build();

        // When
        assignmentEventListener.receiveAssignmentEvent(unknownMessage);

        // Then - createAssignment, updateAssignment 모두 호출되지 않음
        then(assignmentService).should(never()).createAssignment(unknownMessage);
        then(assignmentService).should(never()).updateAssignment(unknownMessage);
    }

    @Test
    @DisplayName("null 이벤트 타입 수신 시 아무 작업도 수행하지 않음")
    void receiveAssignmentEvent_NullType_NoAction() {
        // Given
        AssignmentEventMessage nullTypeMessage = AssignmentEventMessage.builder()
                .eventType(null)
                .canvasAssignmentId(123456L)
                .canvasCourseId(789L)
                .title("테스트")
                .build();

        // When
        assignmentEventListener.receiveAssignmentEvent(nullTypeMessage);

        // Then
        then(assignmentService).should(never()).createAssignment(nullTypeMessage);
        then(assignmentService).should(never()).updateAssignment(nullTypeMessage);
    }

    // ========================================
    // 메시지 필드 검증 테스트
    // ========================================

    @Test
    @DisplayName("메시지의 모든 필드가 AssignmentService에 전달되는지 검증")
    void receiveAssignmentEvent_VerifyMessagePassedToService() {
        // Given
        AssignmentEventMessage message = AssignmentEventMessage.builder()
                .eventType("ASSIGNMENT_CREATED")
                .canvasAssignmentId(999L)
                .canvasCourseId(888L)
                .title("특별 과제")
                .description("상세 설명")
                .dueAt(LocalDateTime.of(2025, 5, 1, 12, 0, 0))
                .pointsPossible(50)
                .submissionTypes("online_text_entry")
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0, 0))
                .updatedAt(LocalDateTime.of(2025, 1, 2, 0, 0, 0))
                .build();

        willDoNothing().given(assignmentService).createAssignment(message);

        // When
        assignmentEventListener.receiveAssignmentEvent(message);

        // Then - 동일한 메시지 객체가 전달되었는지 검증
        then(assignmentService).should().createAssignment(message);
    }

    @Test
    @DisplayName("ASSIGNMENT_DELETED 이벤트 타입은 현재 지원하지 않음")
    void receiveAssignmentEvent_DeletedType_NotSupported() {
        // Given - Phase 1에서는 DELETE 이벤트 미지원
        AssignmentEventMessage deleteMessage = AssignmentEventMessage.builder()
                .eventType("ASSIGNMENT_DELETED")
                .canvasAssignmentId(123456L)
                .canvasCourseId(789L)
                .title("삭제될 과제")
                .build();

        // When
        assignmentEventListener.receiveAssignmentEvent(deleteMessage);

        // Then - 지원하지 않는 이벤트는 무시됨
        then(assignmentService).should(never()).createAssignment(deleteMessage);
        then(assignmentService).should(never()).updateAssignment(deleteMessage);
    }
}
