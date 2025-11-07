package com.unisync.course.integration;

import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.course.dto.CourseResponse;
import com.unisync.course.assignment.dto.AssignmentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Course 조회 API 통합 테스트
 * SQS 관련 기능은 제외하고 순수 API 로직만 테스트
 */
@SpringBootTest(properties = {
    "spring.cloud.aws.sqs.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class CourseApiIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("course_service_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자가 수강 중인 Course 목록 조회")
    void testGetUserCourses() throws Exception {
        // given: 3개의 Course와 사용자의 Enrollment
        Course course1 = createCourse(100L, "Spring Boot 기초", "CS101");
        Course course2 = createCourse(200L, "Spring Boot 고급", "CS201");
        Course course3 = createCourse(300L, "Java 프로그래밍", "CS301");

        String cognitoSub = "test-cognito-sub-1";
        createEnrollment(cognitoSub, course1, true);
        createEnrollment(cognitoSub, course2, false);
        // course3은 다른 사용자만 수강 중
        createEnrollment("test-cognito-sub-999", course3, true);

        // when: GET /api/v1/courses with X-Cognito-Sub header
        String response = mockMvc.perform(get("/api/v1/courses")
                .header("X-Cognito-Sub", cognitoSub))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: userId=1이 수강 중인 2개의 Course만 반환
        CourseResponse[] courses = objectMapper.readValue(response, CourseResponse[].class);
        assertThat(courses).hasSize(2);
        assertThat(courses).extracting(CourseResponse::getCanvasCourseId)
            .containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    @DisplayName("특정 Course 조회")
    void testGetCourse() throws Exception {
        // given: Course 생성
        Course course = createCourse(789L, "Spring Boot 고급", "CS301");

        // when: GET /api/v1/courses/{courseId}
        String response = mockMvc.perform(get("/api/v1/courses/{courseId}", course.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: Course 정보 반환
        CourseResponse courseResponse = objectMapper.readValue(response, CourseResponse.class);
        assertThat(courseResponse.getId()).isEqualTo(course.getId());
        assertThat(courseResponse.getCanvasCourseId()).isEqualTo(789L);
        assertThat(courseResponse.getName()).isEqualTo("Spring Boot 고급");
        assertThat(courseResponse.getCourseCode()).isEqualTo("CS301");
    }

    @Test
    @DisplayName("존재하지 않는 Course 조회 시 404 반환")
    void testGetCourse_NotFound() throws Exception {
        // when: GET /api/v1/courses/999999 (존재하지 않는 ID)
        mockMvc.perform(get("/api/v1/courses/{courseId}", 999999L))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("특정 Course의 Assignment 목록 조회")
    void testGetCourseAssignments() throws Exception {
        // given: Course와 3개의 Assignment 생성
        Course course = createCourse(789L, "Spring Boot 고급", "CS301");
        createAssignment(course, 1001L, "과제 1", LocalDateTime.of(2025, 11, 15, 23, 59));
        createAssignment(course, 1002L, "과제 2", LocalDateTime.of(2025, 11, 20, 23, 59));
        createAssignment(course, 1003L, "과제 3", LocalDateTime.of(2025, 11, 25, 23, 59));

        // 다른 Course의 Assignment
        Course otherCourse = createCourse(888L, "Other Course", "CS999");
        createAssignment(otherCourse, 2001L, "다른 과제", LocalDateTime.now());

        // when: GET /api/v1/courses/{courseId}/assignments
        String response = mockMvc.perform(get("/api/v1/courses/{courseId}/assignments", course.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: 해당 Course의 3개 Assignment만 반환
        AssignmentResponse[] assignments = objectMapper.readValue(response, AssignmentResponse[].class);
        assertThat(assignments).hasSize(3);
        assertThat(assignments).extracting(AssignmentResponse::getCanvasAssignmentId)
            .containsExactlyInAnyOrder(1001L, 1002L, 1003L);
    }

    @Test
    @DisplayName("Assignment가 없는 Course 조회 시 빈 배열 반환")
    void testGetCourseAssignments_EmptyList() throws Exception {
        // given: Assignment가 없는 Course
        Course course = createCourse(789L, "Spring Boot 고급", "CS301");

        // when: GET /api/v1/courses/{courseId}/assignments
        String response = mockMvc.perform(get("/api/v1/courses/{courseId}/assignments", course.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then: 빈 배열 반환
        AssignmentResponse[] assignments = objectMapper.readValue(response, AssignmentResponse[].class);
        assertThat(assignments).isEmpty();
    }

    // Helper methods
    private Course createCourse(Long canvasCourseId, String name, String courseCode) {
        Course course = Course.builder()
            .canvasCourseId(canvasCourseId)
            .name(name)
            .courseCode(courseCode)
            .startAt(LocalDateTime.of(2025, 9, 1, 0, 0))
            .endAt(LocalDateTime.of(2025, 12, 31, 23, 59))
            .build();
        return courseRepository.save(course);
    }

    private Enrollment createEnrollment(String cognitoSub, Course course, boolean isLeader) {
        Enrollment enrollment = Enrollment.builder()
            .cognitoSub(cognitoSub)
            .course(course)
            .isSyncLeader(isLeader)
            .build();
        return enrollmentRepository.save(enrollment);
    }

    private Assignment createAssignment(Course course, Long canvasAssignmentId, String title, LocalDateTime dueAt) {
        Assignment assignment = Assignment.builder()
            .canvasAssignmentId(canvasAssignmentId)
            .course(course)
            .title(title)
            .dueAt(dueAt)
            .pointsPossible(100)
            .submissionTypes("online_upload")
            .build();
        return assignmentRepository.save(assignment);
    }

    /**
     * 테스트용 Configuration
     * SQS가 비활성화된 환경에서 필요한 Bean 제공
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        public SqsTemplate sqsTemplate() {
            return mock(SqsTemplate.class);
        }
    }
}