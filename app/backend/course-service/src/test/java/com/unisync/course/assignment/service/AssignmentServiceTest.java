package com.unisync.course.assignment.service;

import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import com.unisync.course.assignment.dto.AssignmentToScheduleEventDto;
import com.unisync.course.assignment.exception.AssignmentNotFoundException;
import com.unisync.course.assignment.publisher.AssignmentEventPublisher;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.course.exception.CourseNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * AssignmentService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private AssignmentEventPublisher assignmentEventPublisher;

    @InjectMocks
    private AssignmentService assignmentService;

    @Captor
    private ArgumentCaptor<List<AssignmentToScheduleEventDto>> eventListCaptor;

    private Course mockCourse;
    private AssignmentEventMessage validMessage;
    private List<Enrollment> mockEnrollments;

    @BeforeEach
    void setUp() {
        // Mock Course 생성
        mockCourse = Course.builder()
            .id(1L)
            .canvasCourseId(789L)
            .name("Spring Boot 고급")
            .courseCode("CS301")
            .build();

        // Valid AssignmentEventMessage 생성
        validMessage = AssignmentEventMessage.builder()
            .eventType("ASSIGNMENT_CREATED")
            .canvasAssignmentId(123456L)
            .canvasCourseId(789L)
            .title("중간고사 프로젝트")
            .description("Spring Boot로 REST API 구현")
            .dueAt(LocalDateTime.of(2025, 11, 15, 23, 59, 59))
            .pointsPossible(100)
            .submissionTypes("online_upload")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Mock Enrollments 생성 (3명의 수강생)
        mockEnrollments = Arrays.asList(
            Enrollment.builder()
                .id(1L)
                .cognitoSub("user-1")
                .course(mockCourse)
                .isSyncLeader(true)
                .build(),
            Enrollment.builder()
                .id(2L)
                .cognitoSub("user-2")
                .course(mockCourse)
                .isSyncLeader(false)
                .build(),
            Enrollment.builder()
                .id(3L)
                .cognitoSub("user-3")
                .course(mockCourse)
                .isSyncLeader(false)
                .build()
        );
    }

    @Test
    @DisplayName("새로운 Assignment 생성 성공 및 이벤트 발행")
    void createAssignment_Success() {
        // given
        given(assignmentRepository.existsByCanvasAssignmentId(validMessage.getCanvasAssignmentId()))
            .willReturn(false);
        given(courseRepository.findByCanvasCourseId(validMessage.getCanvasCourseId()))
            .willReturn(Optional.of(mockCourse));
        given(assignmentRepository.save(any(Assignment.class)))
            .willAnswer(invocation -> {
                Assignment arg = invocation.getArgument(0);
                return Assignment.builder()
                    .id(1L)
                    .canvasAssignmentId(arg.getCanvasAssignmentId())
                    .course(arg.getCourse())
                    .title(arg.getTitle())
                    .description(arg.getDescription())
                    .dueAt(arg.getDueAt())
                    .pointsPossible(arg.getPointsPossible())
                    .submissionTypes(arg.getSubmissionTypes())
                    .build();
            });
        given(enrollmentRepository.findAllByCourseId(mockCourse.getId()))
            .willReturn(mockEnrollments);

        // when
        assignmentService.createAssignment(validMessage);

        // then
        then(assignmentRepository).should(times(1)).existsByCanvasAssignmentId(anyLong());
        then(courseRepository).should(times(1)).findByCanvasCourseId(anyLong());
        then(assignmentRepository).should(times(1)).save(any(Assignment.class));
        then(enrollmentRepository).should(times(1)).findAllByCourseId(anyLong());
        then(assignmentEventPublisher).should(times(1)).publishAssignmentEvents(anyList());
    }

    @Test
    @DisplayName("Assignment 생성 시 모든 수강생에게 이벤트 발행")
    void createAssignment_PublishEventsToAllEnrollments() {
        // given
        given(assignmentRepository.existsByCanvasAssignmentId(validMessage.getCanvasAssignmentId()))
            .willReturn(false);
        given(courseRepository.findByCanvasCourseId(validMessage.getCanvasCourseId()))
            .willReturn(Optional.of(mockCourse));
        given(assignmentRepository.save(any(Assignment.class)))
            .willAnswer(invocation -> {
                Assignment arg = invocation.getArgument(0);
                return Assignment.builder()
                    .id(1L)
                    .canvasAssignmentId(arg.getCanvasAssignmentId())
                    .course(arg.getCourse())
                    .title(arg.getTitle())
                    .description(arg.getDescription())
                    .dueAt(arg.getDueAt())
                    .pointsPossible(arg.getPointsPossible())
                    .submissionTypes(arg.getSubmissionTypes())
                    .build();
            });
        given(enrollmentRepository.findAllByCourseId(mockCourse.getId()))
            .willReturn(mockEnrollments);

        // when
        assignmentService.createAssignment(validMessage);

        // then
        then(assignmentEventPublisher).should(times(1)).publishAssignmentEvents(eventListCaptor.capture());

        List<AssignmentToScheduleEventDto> publishedEvents = eventListCaptor.getValue();
        assertThat(publishedEvents).hasSize(3); // 3명의 수강생
        assertThat(publishedEvents)
            .extracting(AssignmentToScheduleEventDto::getCognitoSub)
            .containsExactlyInAnyOrder("user-1", "user-2", "user-3");
        assertThat(publishedEvents)
            .allMatch(event -> event.getEventType().equals("ASSIGNMENT_CREATED"))
            .allMatch(event -> event.getTitle().equals(validMessage.getTitle()))
            .allMatch(event -> event.getCourseName().equals(mockCourse.getName()));
    }

    @Test
    @DisplayName("이미 존재하는 Assignment는 생성하지 않음 및 이벤트 발행 안함")
    void createAssignment_AlreadyExists_ShouldNotSave() {
        // given
        given(assignmentRepository.existsByCanvasAssignmentId(validMessage.getCanvasAssignmentId()))
            .willReturn(true);

        // when
        assignmentService.createAssignment(validMessage);

        // then
        then(assignmentRepository).should(times(1)).existsByCanvasAssignmentId(anyLong());
        then(courseRepository).should(never()).findByCanvasCourseId(anyLong());
        then(assignmentRepository).should(never()).save(any(Assignment.class));
        then(enrollmentRepository).should(never()).findAllByCourseId(anyLong());
        then(assignmentEventPublisher).should(never()).publishAssignmentEvents(anyList());
    }

    @Test
    @DisplayName("Course가 존재하지 않으면 예외 발생")
    void createAssignment_CourseNotFound_ShouldThrowException() {
        // given
        given(assignmentRepository.existsByCanvasAssignmentId(validMessage.getCanvasAssignmentId()))
            .willReturn(false);
        given(courseRepository.findByCanvasCourseId(validMessage.getCanvasCourseId()))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> assignmentService.createAssignment(validMessage))
            .isInstanceOf(CourseNotFoundException.class)
            .hasMessageContaining("과목을 찾을 수 없습니다");

        then(assignmentRepository).should(never()).save(any(Assignment.class));
    }

    @Test
    @DisplayName("Assignment 업데이트 성공 및 이벤트 발행")
    void updateAssignment_Success() {
        // given
        Assignment existingAssignment = Assignment.builder()
            .id(1L)
            .canvasAssignmentId(123456L)
            .course(mockCourse)
            .title("기존 제목")
            .description("기존 설명")
            .dueAt(LocalDateTime.of(2025, 11, 10, 23, 59, 59))
            .pointsPossible(80)
            .submissionTypes("online_text_entry")
            .build();

        given(assignmentRepository.findByCanvasAssignmentId(validMessage.getCanvasAssignmentId()))
            .willReturn(Optional.of(existingAssignment));
        given(assignmentRepository.save(any(Assignment.class)))
            .willReturn(existingAssignment);
        given(enrollmentRepository.findAllByCourseId(mockCourse.getId()))
            .willReturn(mockEnrollments);

        // when
        assignmentService.updateAssignment(validMessage);

        // then
        then(assignmentRepository).should(times(1)).findByCanvasAssignmentId(anyLong());
        then(assignmentRepository).should(times(1)).save(any(Assignment.class));
        then(enrollmentRepository).should(times(1)).findAllByCourseId(anyLong());
        then(assignmentEventPublisher).should(times(1)).publishAssignmentEvents(anyList());
    }

    @Test
    @DisplayName("Assignment 업데이트 시 ASSIGNMENT_UPDATED 이벤트 발행")
    void updateAssignment_PublishUpdateEvent() {
        // given
        Assignment existingAssignment = Assignment.builder()
            .id(1L)
            .canvasAssignmentId(123456L)
            .course(mockCourse)
            .title("기존 제목")
            .description("기존 설명")
            .dueAt(LocalDateTime.of(2025, 11, 10, 23, 59, 59))
            .pointsPossible(80)
            .submissionTypes("online_text_entry")
            .build();

        given(assignmentRepository.findByCanvasAssignmentId(validMessage.getCanvasAssignmentId()))
            .willReturn(Optional.of(existingAssignment));
        given(assignmentRepository.save(any(Assignment.class)))
            .willReturn(existingAssignment);
        given(enrollmentRepository.findAllByCourseId(mockCourse.getId()))
            .willReturn(mockEnrollments);

        // when
        assignmentService.updateAssignment(validMessage);

        // then
        then(assignmentEventPublisher).should(times(1)).publishAssignmentEvents(eventListCaptor.capture());

        List<AssignmentToScheduleEventDto> publishedEvents = eventListCaptor.getValue();
        assertThat(publishedEvents).hasSize(3);
        assertThat(publishedEvents)
            .allMatch(event -> event.getEventType().equals("ASSIGNMENT_UPDATED"));
    }

    @Test
    @DisplayName("존재하지 않는 Assignment 업데이트 시 예외 발생")
    void updateAssignment_NotFound_ShouldThrowException() {
        // given
        given(assignmentRepository.findByCanvasAssignmentId(validMessage.getCanvasAssignmentId()))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> assignmentService.updateAssignment(validMessage))
            .isInstanceOf(AssignmentNotFoundException.class)
            .hasMessageContaining("과제를 찾을 수 없습니다");

        then(assignmentRepository).should(never()).save(any(Assignment.class));
        then(assignmentEventPublisher).should(never()).publishAssignmentEvents(anyList());
    }
}