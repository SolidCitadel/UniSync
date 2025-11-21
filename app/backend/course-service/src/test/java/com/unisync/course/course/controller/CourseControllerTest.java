package com.unisync.course.course.controller;

import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.course.dto.CourseResponse;
import com.unisync.course.course.service.CourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CourseController 단위 테스트
 */
@WebMvcTest(CourseController.class)
@DisplayName("CourseController 단위 테스트")
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    private static final String COGNITO_SUB = "test-cognito-sub-123";

    // ========================================
    // GET /v1/courses 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/courses - 사용자의 수강 과목 목록 조회 성공")
    void getUserCourses_Success() throws Exception {
        // Given
        List<CourseResponse> courses = Arrays.asList(
                CourseResponse.builder()
                        .id(1L)
                        .canvasCourseId(100L)
                        .name("데이터베이스")
                        .courseCode("CS101")
                        .build(),
                CourseResponse.builder()
                        .id(2L)
                        .canvasCourseId(200L)
                        .name("알고리즘")
                        .courseCode("CS201")
                        .build()
        );

        given(courseService.getUserCourses(COGNITO_SUB))
                .willReturn(courses);

        // When & Then
        mockMvc.perform(get("/v1/courses")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("데이터베이스"))
                .andExpect(jsonPath("$[0].courseCode").value("CS101"))
                .andExpect(jsonPath("$[1].name").value("알고리즘"))
                .andExpect(jsonPath("$[1].courseCode").value("CS201"));

        then(courseService).should().getUserCourses(COGNITO_SUB);
    }

    @Test
    @DisplayName("GET /v1/courses - 수강 과목 없음, 빈 배열 반환")
    void getUserCourses_Empty() throws Exception {
        // Given
        given(courseService.getUserCourses(COGNITO_SUB))
                .willReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/v1/courses")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        then(courseService).should().getUserCourses(COGNITO_SUB);
    }

    // ========================================
    // GET /v1/courses/{courseId} 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/courses/{courseId} - 특정 과목 조회 성공")
    void getCourse_Success() throws Exception {
        // Given
        CourseResponse course = CourseResponse.builder()
                .id(1L)
                .canvasCourseId(100L)
                .name("데이터베이스")
                .courseCode("CS101")
                .description("데이터베이스 설계 및 SQL 기초")
                .startAt(LocalDateTime.of(2025, 3, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 6, 30, 23, 59))
                .build();

        given(courseService.getCourse(1L))
                .willReturn(Optional.of(course));

        // When & Then
        mockMvc.perform(get("/v1/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.canvasCourseId").value(100))
                .andExpect(jsonPath("$.name").value("데이터베이스"))
                .andExpect(jsonPath("$.courseCode").value("CS101"))
                .andExpect(jsonPath("$.description").value("데이터베이스 설계 및 SQL 기초"));

        then(courseService).should().getCourse(1L);
    }

    @Test
    @DisplayName("GET /v1/courses/{courseId} - 존재하지 않는 과목 404")
    void getCourse_NotFound() throws Exception {
        // Given
        given(courseService.getCourse(999L))
                .willReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/v1/courses/999"))
                .andExpect(status().isNotFound());

        then(courseService).should().getCourse(999L);
    }

    // ========================================
    // GET /v1/courses/{courseId}/assignments 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/courses/{courseId}/assignments - 과제 목록 조회 성공")
    void getCourseAssignments_Success() throws Exception {
        // Given
        List<AssignmentResponse> assignments = Arrays.asList(
                AssignmentResponse.builder()
                        .id(1L)
                        .canvasAssignmentId(1001L)
                        .courseId(1L)
                        .title("중간고사 프로젝트")
                        .description("ER 다이어그램 설계")
                        .dueAt(LocalDateTime.of(2025, 4, 15, 23, 59))
                        .pointsPossible(100)
                        .submissionTypes("online_upload")
                        .build(),
                AssignmentResponse.builder()
                        .id(2L)
                        .canvasAssignmentId(1002L)
                        .courseId(1L)
                        .title("기말 프로젝트")
                        .description("데이터베이스 구현")
                        .dueAt(LocalDateTime.of(2025, 6, 15, 23, 59))
                        .pointsPossible(150)
                        .submissionTypes("online_upload")
                        .build()
        );

        given(courseService.getCourseAssignments(1L))
                .willReturn(assignments);

        // When & Then
        mockMvc.perform(get("/v1/courses/1/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("중간고사 프로젝트"))
                .andExpect(jsonPath("$[0].pointsPossible").value(100))
                .andExpect(jsonPath("$[1].title").value("기말 프로젝트"))
                .andExpect(jsonPath("$[1].pointsPossible").value(150));

        then(courseService).should().getCourseAssignments(1L);
    }

    @Test
    @DisplayName("GET /v1/courses/{courseId}/assignments - 과제 없음, 빈 배열 반환")
    void getCourseAssignments_Empty() throws Exception {
        // Given
        given(courseService.getCourseAssignments(2L))
                .willReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/v1/courses/2/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        then(courseService).should().getCourseAssignments(2L);
    }
}
