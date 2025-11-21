package com.unisync.course.course.service;

import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.course.dto.CourseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * CourseService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService 단위 테스트")
class CourseServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @InjectMocks
    private CourseService courseService;

    private Course mockCourse1;
    private Course mockCourse2;
    private Assignment mockAssignment1;
    private Assignment mockAssignment2;
    private List<Enrollment> mockEnrollments;
    private String cognitoSub;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-cognito-sub-123";

        // Mock Courses 생성
        mockCourse1 = Course.builder()
                .id(1L)
                .canvasCourseId(100L)
                .name("데이터베이스")
                .courseCode("CS101")
                .description("데이터베이스 설계 및 SQL 기초")
                .startAt(LocalDateTime.of(2025, 3, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 6, 30, 23, 59))
                .build();

        mockCourse2 = Course.builder()
                .id(2L)
                .canvasCourseId(200L)
                .name("알고리즘")
                .courseCode("CS201")
                .description("알고리즘 분석 및 설계")
                .startAt(LocalDateTime.of(2025, 3, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 6, 30, 23, 59))
                .build();

        // Mock Enrollments 생성
        mockEnrollments = Arrays.asList(
                Enrollment.builder()
                        .id(1L)
                        .cognitoSub(cognitoSub)
                        .course(mockCourse1)
                        .isSyncLeader(true)
                        .build(),
                Enrollment.builder()
                        .id(2L)
                        .cognitoSub(cognitoSub)
                        .course(mockCourse2)
                        .isSyncLeader(false)
                        .build()
        );

        // Mock Assignments 생성
        mockAssignment1 = Assignment.builder()
                .id(1L)
                .canvasAssignmentId(1001L)
                .course(mockCourse1)
                .title("중간고사 프로젝트")
                .description("ER 다이어그램 설계")
                .dueAt(LocalDateTime.of(2025, 4, 15, 23, 59))
                .pointsPossible(100)
                .submissionTypes("online_upload")
                .build();

        mockAssignment2 = Assignment.builder()
                .id(2L)
                .canvasAssignmentId(1002L)
                .course(mockCourse1)
                .title("기말 프로젝트")
                .description("데이터베이스 구현")
                .dueAt(LocalDateTime.of(2025, 6, 15, 23, 59))
                .pointsPossible(150)
                .submissionTypes("online_upload")
                .build();
    }

    // ========================================
    // getUserCourses 테스트
    // ========================================

    @Test
    @DisplayName("사용자의 수강 과목 목록 조회 성공")
    void getUserCourses_Success() {
        // Given
        given(enrollmentRepository.findAllByCognitoSub(cognitoSub))
                .willReturn(mockEnrollments);

        // When
        List<CourseResponse> result = courseService.getUserCourses(cognitoSub);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CourseResponse::getName)
                .containsExactlyInAnyOrder("데이터베이스", "알고리즘");
        assertThat(result).extracting(CourseResponse::getCourseCode)
                .containsExactlyInAnyOrder("CS101", "CS201");

        then(enrollmentRepository).should().findAllByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("수강 과목이 없는 사용자 조회 시 빈 목록 반환")
    void getUserCourses_NoEnrollments_ReturnsEmptyList() {
        // Given
        given(enrollmentRepository.findAllByCognitoSub(cognitoSub))
                .willReturn(Collections.emptyList());

        // When
        List<CourseResponse> result = courseService.getUserCourses(cognitoSub);

        // Then
        assertThat(result).isEmpty();

        then(enrollmentRepository).should().findAllByCognitoSub(cognitoSub);
    }

    // ========================================
    // getCourse 테스트
    // ========================================

    @Test
    @DisplayName("특정 과목 조회 성공")
    void getCourse_Success() {
        // Given
        given(courseRepository.findById(1L))
                .willReturn(Optional.of(mockCourse1));

        // When
        Optional<CourseResponse> result = courseService.getCourse(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("데이터베이스");
        assertThat(result.get().getCourseCode()).isEqualTo("CS101");
        assertThat(result.get().getCanvasCourseId()).isEqualTo(100L);

        then(courseRepository).should().findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 과목 조회 시 빈 Optional 반환")
    void getCourse_NotFound_ReturnsEmpty() {
        // Given
        given(courseRepository.findById(999L))
                .willReturn(Optional.empty());

        // When
        Optional<CourseResponse> result = courseService.getCourse(999L);

        // Then
        assertThat(result).isEmpty();

        then(courseRepository).should().findById(999L);
    }

    // ========================================
    // getCourseAssignments 테스트
    // ========================================

    @Test
    @DisplayName("과목의 과제 목록 조회 성공")
    void getCourseAssignments_Success() {
        // Given
        List<Assignment> mockAssignments = Arrays.asList(mockAssignment1, mockAssignment2);
        given(assignmentRepository.findAllByCourseId(1L))
                .willReturn(mockAssignments);

        // When
        List<AssignmentResponse> result = courseService.getCourseAssignments(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(AssignmentResponse::getTitle)
                .containsExactlyInAnyOrder("중간고사 프로젝트", "기말 프로젝트");
        assertThat(result).extracting(AssignmentResponse::getPointsPossible)
                .containsExactlyInAnyOrder(100, 150);

        then(assignmentRepository).should().findAllByCourseId(1L);
    }

    @Test
    @DisplayName("과제가 없는 과목 조회 시 빈 목록 반환")
    void getCourseAssignments_NoAssignments_ReturnsEmptyList() {
        // Given
        given(assignmentRepository.findAllByCourseId(2L))
                .willReturn(Collections.emptyList());

        // When
        List<AssignmentResponse> result = courseService.getCourseAssignments(2L);

        // Then
        assertThat(result).isEmpty();

        then(assignmentRepository).should().findAllByCourseId(2L);
    }

    @Test
    @DisplayName("과제 응답 DTO 변환 검증")
    void getCourseAssignments_VerifyDtoMapping() {
        // Given
        given(assignmentRepository.findAllByCourseId(1L))
                .willReturn(Collections.singletonList(mockAssignment1));

        // When
        List<AssignmentResponse> result = courseService.getCourseAssignments(1L);

        // Then
        assertThat(result).hasSize(1);
        AssignmentResponse response = result.get(0);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCanvasAssignmentId()).isEqualTo(1001L);
        assertThat(response.getCourseId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("중간고사 프로젝트");
        assertThat(response.getDescription()).isEqualTo("ER 다이어그램 설계");
        assertThat(response.getDueAt()).isEqualTo(LocalDateTime.of(2025, 4, 15, 23, 59));
        assertThat(response.getPointsPossible()).isEqualTo(100);
        assertThat(response.getSubmissionTypes()).isEqualTo("online_upload");
    }
}
