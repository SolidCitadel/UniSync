package com.unisync.course.course.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.shared.dto.sqs.CourseEnrollmentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * CourseEnrollmentListener 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseEnrollmentListener 단위 테스트")
class CourseEnrollmentListenerTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    private CourseEnrollmentListener courseEnrollmentListener;

    private ObjectMapper objectMapper;
    private CourseEnrollmentEvent validEvent;
    private Course mockCourse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 수동으로 Listener 생성 (ObjectMapper 주입)
        courseEnrollmentListener = new CourseEnrollmentListener(
                courseRepository,
                enrollmentRepository,
                objectMapper
        );

        // Valid CourseEnrollmentEvent 생성
        validEvent = CourseEnrollmentEvent.builder()
                .cognitoSub("test-cognito-sub-123")
                .canvasCourseId(12345L)
                .courseName("데이터베이스")
                .courseCode("CS101")
                .startAt(LocalDateTime.of(2025, 3, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 6, 30, 23, 59))
                .publishedAt(LocalDateTime.now())
                .build();

        // Mock Course 생성
        mockCourse = Course.builder()
                .id(1L)
                .canvasCourseId(12345L)
                .name("데이터베이스")
                .courseCode("CS101")
                .startAt(LocalDateTime.of(2025, 3, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 6, 30, 23, 59))
                .build();
    }

    // ========================================
    // 새 Course 및 Enrollment 생성 테스트
    // ========================================

    @Test
    @DisplayName("새로운 Course와 Enrollment 생성 성공 (첫 번째 등록자 = Leader)")
    void receiveCourseEnrollment_NewCourse_Success() throws Exception {
        // Given
        String messageBody = objectMapper.writeValueAsString(validEvent);

        given(courseRepository.findByCanvasCourseId(validEvent.getCanvasCourseId()))
                .willReturn(Optional.empty()); // Course 없음
        given(courseRepository.save(any(Course.class)))
                .willReturn(mockCourse);
        given(enrollmentRepository.existsByCognitoSubAndCourseId(anyString(), anyLong()))
                .willReturn(false); // Enrollment 없음
        given(enrollmentRepository.save(any(Enrollment.class)))
                .willAnswer(invocation -> {
                    Enrollment arg = invocation.getArgument(0);
                    return Enrollment.builder()
                            .id(1L)
                            .cognitoSub(arg.getCognitoSub())
                            .course(arg.getCourse())
                            .isSyncLeader(arg.getIsSyncLeader())
                            .build();
                });

        // When
        courseEnrollmentListener.receiveCourseEnrollment(messageBody);

        // Then - Course 생성 검증
        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        then(courseRepository).should().save(courseCaptor.capture());
        Course savedCourse = courseCaptor.getValue();
        assertThat(savedCourse.getCanvasCourseId()).isEqualTo(12345L);
        assertThat(savedCourse.getName()).isEqualTo("데이터베이스");
        assertThat(savedCourse.getCourseCode()).isEqualTo("CS101");

        // Then - Enrollment 생성 검증 (첫 등록자 = Leader)
        ArgumentCaptor<Enrollment> enrollmentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        then(enrollmentRepository).should().save(enrollmentCaptor.capture());
        Enrollment savedEnrollment = enrollmentCaptor.getValue();
        assertThat(savedEnrollment.getCognitoSub()).isEqualTo("test-cognito-sub-123");
        assertThat(savedEnrollment.getIsSyncLeader()).isTrue(); // 첫 등록자는 Leader
    }

    @Test
    @DisplayName("기존 Course에 새 Enrollment 생성 (Leader 아님)")
    void receiveCourseEnrollment_ExistingCourse_NotLeader() throws Exception {
        // Given
        String messageBody = objectMapper.writeValueAsString(validEvent);

        given(courseRepository.findByCanvasCourseId(validEvent.getCanvasCourseId()))
                .willReturn(Optional.of(mockCourse)); // Course 이미 존재
        given(enrollmentRepository.existsByCognitoSubAndCourseId(anyString(), anyLong()))
                .willReturn(false); // Enrollment 없음
        given(enrollmentRepository.save(any(Enrollment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When
        courseEnrollmentListener.receiveCourseEnrollment(messageBody);

        // Then - Course 생성 안 함
        then(courseRepository).should(never()).save(any(Course.class));

        // Then - Enrollment 생성 검증 (Leader 아님)
        ArgumentCaptor<Enrollment> enrollmentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        then(enrollmentRepository).should().save(enrollmentCaptor.capture());
        Enrollment savedEnrollment = enrollmentCaptor.getValue();
        assertThat(savedEnrollment.getCognitoSub()).isEqualTo("test-cognito-sub-123");
        assertThat(savedEnrollment.getIsSyncLeader()).isFalse(); // 기존 Course에 추가 → Leader 아님
    }

    // ========================================
    // 중복 Enrollment 처리 테스트
    // ========================================

    @Test
    @DisplayName("이미 등록된 Enrollment는 생성하지 않음")
    void receiveCourseEnrollment_DuplicateEnrollment_Skip() throws Exception {
        // Given
        String messageBody = objectMapper.writeValueAsString(validEvent);

        given(courseRepository.findByCanvasCourseId(validEvent.getCanvasCourseId()))
                .willReturn(Optional.of(mockCourse));
        given(enrollmentRepository.existsByCognitoSubAndCourseId(
                validEvent.getCognitoSub(), mockCourse.getId()))
                .willReturn(true); // 이미 등록됨

        // When
        courseEnrollmentListener.receiveCourseEnrollment(messageBody);

        // Then - Enrollment 저장 안 함
        then(enrollmentRepository).should(never()).save(any(Enrollment.class));
    }

    // ========================================
    // 에러 처리 테스트
    // ========================================

    @Test
    @DisplayName("잘못된 JSON 형식 메시지 처리 시 예외 발생")
    void receiveCourseEnrollment_InvalidJson_ThrowsException() {
        // Given
        String invalidJson = "{ invalid json }";

        // When & Then
        assertThatThrownBy(() -> courseEnrollmentListener.receiveCourseEnrollment(invalidJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse course-enrollment event");

        // Course, Enrollment 저장 안 함
        then(courseRepository).should(never()).save(any(Course.class));
        then(enrollmentRepository).should(never()).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("빈 JSON 메시지 처리 시 예외 발생")
    void receiveCourseEnrollment_EmptyJson_ThrowsException() {
        // Given
        String emptyJson = "{}";

        // When & Then - null 필드로 인해 예외 발생 가능
        // courseRepository.findByCanvasCourseId(null) 호출 시 예외
        assertThatThrownBy(() -> courseEnrollmentListener.receiveCourseEnrollment(emptyJson))
                .isInstanceOf(Exception.class);
    }

    // ========================================
    // 메시지 파싱 검증 테스트
    // ========================================

    @Test
    @DisplayName("CourseEnrollmentEvent 파싱 검증")
    void receiveCourseEnrollment_VerifyEventParsing() throws Exception {
        // Given
        CourseEnrollmentEvent event = CourseEnrollmentEvent.builder()
                .cognitoSub("user-abc-123")
                .canvasCourseId(99999L)
                .courseName("알고리즘 분석")
                .courseCode("CS301")
                .startAt(LocalDateTime.of(2025, 9, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 12, 20, 23, 59))
                .publishedAt(LocalDateTime.now())
                .build();

        String messageBody = objectMapper.writeValueAsString(event);

        given(courseRepository.findByCanvasCourseId(99999L))
                .willReturn(Optional.empty());
        given(courseRepository.save(any(Course.class)))
                .willAnswer(invocation -> {
                    Course arg = invocation.getArgument(0);
                    return Course.builder()
                            .id(10L)
                            .canvasCourseId(arg.getCanvasCourseId())
                            .name(arg.getName())
                            .courseCode(arg.getCourseCode())
                            .startAt(arg.getStartAt())
                            .endAt(arg.getEndAt())
                            .build();
                });
        given(enrollmentRepository.existsByCognitoSubAndCourseId(anyString(), anyLong()))
                .willReturn(false);

        // When
        courseEnrollmentListener.receiveCourseEnrollment(messageBody);

        // Then - 올바른 값으로 Course 생성되었는지 검증
        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        then(courseRepository).should().save(courseCaptor.capture());
        Course savedCourse = courseCaptor.getValue();

        assertThat(savedCourse.getCanvasCourseId()).isEqualTo(99999L);
        assertThat(savedCourse.getName()).isEqualTo("알고리즘 분석");
        assertThat(savedCourse.getCourseCode()).isEqualTo("CS301");
        assertThat(savedCourse.getStartAt()).isEqualTo(LocalDateTime.of(2025, 9, 1, 0, 0));
        assertThat(savedCourse.getEndAt()).isEqualTo(LocalDateTime.of(2025, 12, 20, 23, 59));
    }

    @Test
    @DisplayName("Course 및 Enrollment 생성 후 Repository 호출 횟수 검증")
    void receiveCourseEnrollment_VerifyRepositoryCalls() throws Exception {
        // Given
        String messageBody = objectMapper.writeValueAsString(validEvent);

        given(courseRepository.findByCanvasCourseId(anyLong()))
                .willReturn(Optional.empty());
        given(courseRepository.save(any(Course.class)))
                .willReturn(mockCourse);
        given(enrollmentRepository.existsByCognitoSubAndCourseId(anyString(), anyLong()))
                .willReturn(false);

        // When
        courseEnrollmentListener.receiveCourseEnrollment(messageBody);

        // Then
        then(courseRepository).should(times(1)).findByCanvasCourseId(anyLong());
        then(courseRepository).should(times(1)).save(any(Course.class));
        then(enrollmentRepository).should(times(1)).existsByCognitoSubAndCourseId(anyString(), anyLong());
        then(enrollmentRepository).should(times(1)).save(any(Enrollment.class));
    }
}
