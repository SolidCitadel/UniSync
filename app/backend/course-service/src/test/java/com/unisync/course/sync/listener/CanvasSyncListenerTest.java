package com.unisync.course.sync.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.assignment.service.AssignmentService;
import com.unisync.course.assignment.publisher.AssignmentEventPublisher;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.sync.dto.CanvasSyncMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.when;

@DisplayName("CanvasSyncListener - syncMode 처리 테스트")
class CanvasSyncListenerTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private AssignmentEventPublisher assignmentEventPublisher;

    private ObjectMapper objectMapper;

@InjectMocks
    private CanvasSyncListener canvasSyncListener;

@BeforeEach
void setUp() {
    MockitoAnnotations.openMocks(this);
    objectMapper = new ObjectMapper();
    canvasSyncListener = new CanvasSyncListener(courseRepository, enrollmentRepository, assignmentService, assignmentEventPublisher, objectMapper);
}

@Test
@DisplayName("courses 모드일 때 assignments를 처리하지 않는다")
void coursesModeSkipsAssignments() throws Exception {
        // given
        Course course = Course.builder()
                .id(1L)
                .canvasCourseId(123L)
                .name("테스트 과목")
                .build();

        when(courseRepository.findByCanvasCourseId(123L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCognitoSubAndCourseId("user-1", 1L)).thenReturn(true);

        CanvasSyncMessage message = CanvasSyncMessage.builder()
                .eventType("CANVAS_COURSES_SYNCED")
                .cognitoSub("user-1")
                .syncMode("courses")
                .courses(List.of(
                        CanvasSyncMessage.CourseData.builder()
                                .canvasCourseId(123L)
                                .courseName("테스트 과목")
                                .courseCode("CS101")
                                .assignments(List.of(
                                        CanvasSyncMessage.AssignmentData.builder()
                                                .canvasAssignmentId(999L)
                                                .title("과제")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        String payload = objectMapper.writeValueAsString(message);

        // when
        canvasSyncListener.receiveCanvasSync(payload);

        // then
        then(assignmentService).should(never()).createAssignment(any());
    }

    @Test
    @DisplayName("assignments 모드일 때 assignments를 처리한다")
    void assignmentsModeProcessesAssignments() throws Exception {
        // given
        Course course = Course.builder()
                .id(1L)
                .canvasCourseId(123L)
                .name("테스트 과목")
                .build();

        when(courseRepository.findByCanvasCourseId(123L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCognitoSubAndCourseId("user-1", 1L)).thenReturn(true);

        CanvasSyncMessage message = CanvasSyncMessage.builder()
                .eventType("CANVAS_SYNC_COMPLETED")
                .cognitoSub("user-1")
                .syncMode("assignments")
                .courses(List.of(
                        CanvasSyncMessage.CourseData.builder()
                                .canvasCourseId(123L)
                                .courseName("테스트 과목")
                                .courseCode("CS101")
                                .assignments(List.of(
                                        CanvasSyncMessage.AssignmentData.builder()
                                                .canvasAssignmentId(999L)
                                                .title("과제")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        String payload = objectMapper.writeValueAsString(message);

        // when
        canvasSyncListener.receiveCanvasSync(payload);

        // then
        ArgumentCaptor<com.unisync.shared.dto.sqs.AssignmentEventMessage> captor =
                ArgumentCaptor.forClass(com.unisync.shared.dto.sqs.AssignmentEventMessage.class);

        then(assignmentService).should().createAssignment(captor.capture());
        assertThat(captor.getValue().getCanvasAssignmentId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("eventType이 CANVAS_COURSES_SYNCED이면 syncMode가 assignments여도 assignments를 건너뛴다")
    void eventTypeCoursesSyncedSkipsAssignmentsEvenIfModeAssignments() throws Exception {
        Course course = Course.builder()
                .id(1L)
                .canvasCourseId(200L)
                .name("네트워크")
                .build();

        when(courseRepository.findByCanvasCourseId(200L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCognitoSubAndCourseId("user-2", 1L)).thenReturn(true);

        CanvasSyncMessage message = CanvasSyncMessage.builder()
                .eventType("CANVAS_COURSES_SYNCED")
                .cognitoSub("user-2")
                .syncMode("assignments") // eventType이 우선
                .courses(List.of(
                        CanvasSyncMessage.CourseData.builder()
                                .canvasCourseId(200L)
                                .courseName("네트워크")
                                .courseCode("CS202")
                                .assignments(List.of(
                                        CanvasSyncMessage.AssignmentData.builder()
                                                .canvasAssignmentId(555L)
                                                .title("HW")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        String payload = objectMapper.writeValueAsString(message);

        canvasSyncListener.receiveCanvasSync(payload);

        then(assignmentService).should(never()).createAssignment(any());
    }

    @Test
    @DisplayName("과목이 비어 있으면 저장/과제 처리도 일어나지 않는다")
    void emptyCoursesNoOps() throws Exception {
        CanvasSyncMessage message = CanvasSyncMessage.builder()
                .eventType("CANVAS_COURSES_SYNCED")
                .cognitoSub("user-3")
                .syncMode("courses")
                .courses(List.of())
                .build();

        String payload = objectMapper.writeValueAsString(message);

        canvasSyncListener.receiveCanvasSync(payload);

        then(courseRepository).should(never()).save(any());
        then(enrollmentRepository).should(never()).save(any());
        then(assignmentService).should(never()).createAssignment(any());
    }

    @Test
    @DisplayName("새 과목이면 Course 저장 후 Enrollment 저장까지 수행한다")
    void newCourseCreatesCourseAndEnrollment() throws Exception {
        Course saved = Course.builder()
                .id(10L)
                .canvasCourseId(300L)
                .name("운영체제")
                .build();

        when(courseRepository.findByCanvasCourseId(300L)).thenReturn(Optional.empty());
        when(courseRepository.save(any(Course.class))).thenReturn(saved);
        when(enrollmentRepository.existsByCognitoSubAndCourseId("user-4", 10L)).thenReturn(false);

        CanvasSyncMessage message = CanvasSyncMessage.builder()
                .eventType("CANVAS_COURSES_SYNCED")
                .cognitoSub("user-4")
                .syncMode("courses")
                .courses(List.of(
                        CanvasSyncMessage.CourseData.builder()
                                .canvasCourseId(300L)
                                .courseName("운영체제")
                                .courseCode("CS303")
                                .assignments(List.of())
                                .build()
                ))
                .build();

        String payload = objectMapper.writeValueAsString(message);

        canvasSyncListener.receiveCanvasSync(payload);

        then(courseRepository).should().save(any(Course.class));
        then(enrollmentRepository).should().save(any(Enrollment.class));
    }
}
