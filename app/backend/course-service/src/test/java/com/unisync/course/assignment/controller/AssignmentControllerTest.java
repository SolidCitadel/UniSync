package com.unisync.course.assignment.controller;

import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.assignment.service.AssignmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AssignmentController 단위 테스트
 */
@WebMvcTest(AssignmentController.class)
@DisplayName("AssignmentController 단위 테스트")
class AssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssignmentService assignmentService;

    // ========================================
    // GET /v1/assignments/canvas/{canvasAssignmentId} 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/assignments/canvas/{canvasAssignmentId} - Canvas ID로 과제 조회 성공")
    void getAssignmentByCanvasId_Success() throws Exception {
        // Given
        Long canvasAssignmentId = 123456L;
        AssignmentResponse assignment = AssignmentResponse.builder()
                .id(1L)
                .canvasAssignmentId(canvasAssignmentId)
                .courseId(10L)
                .title("중간고사 프로젝트")
                .description("Spring Boot로 REST API 구현")
                .dueAt(LocalDateTime.of(2025, 4, 15, 23, 59))
                .pointsPossible(100)
                .submissionTypes("online_upload")
                .createdAt(LocalDateTime.of(2025, 1, 15, 10, 30))
                .updatedAt(LocalDateTime.of(2025, 1, 15, 10, 30))
                .build();

        given(assignmentService.findByCanvasAssignmentId(canvasAssignmentId))
                .willReturn(Optional.of(assignment));

        // When & Then
        mockMvc.perform(get("/v1/assignments/canvas/" + canvasAssignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.canvasAssignmentId").value(canvasAssignmentId))
                .andExpect(jsonPath("$.courseId").value(10))
                .andExpect(jsonPath("$.title").value("중간고사 프로젝트"))
                .andExpect(jsonPath("$.description").value("Spring Boot로 REST API 구현"))
                .andExpect(jsonPath("$.pointsPossible").value(100))
                .andExpect(jsonPath("$.submissionTypes").value("online_upload"));

        then(assignmentService).should().findByCanvasAssignmentId(canvasAssignmentId);
    }

    @Test
    @DisplayName("GET /v1/assignments/canvas/{canvasAssignmentId} - 존재하지 않는 과제 404")
    void getAssignmentByCanvasId_NotFound() throws Exception {
        // Given
        Long canvasAssignmentId = 999999L;
        given(assignmentService.findByCanvasAssignmentId(canvasAssignmentId))
                .willReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/v1/assignments/canvas/" + canvasAssignmentId))
                .andExpect(status().isNotFound());

        then(assignmentService).should().findByCanvasAssignmentId(canvasAssignmentId);
    }

    @Test
    @DisplayName("GET /v1/assignments/canvas/{canvasAssignmentId} - 응답 필드 전체 검증")
    void getAssignmentByCanvasId_VerifyAllFields() throws Exception {
        // Given
        Long canvasAssignmentId = 123456L;
        LocalDateTime dueAt = LocalDateTime.of(2025, 4, 15, 23, 59);
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 15, 10, 30);
        LocalDateTime updatedAt = LocalDateTime.of(2025, 1, 20, 14, 45);

        AssignmentResponse assignment = AssignmentResponse.builder()
                .id(1L)
                .canvasAssignmentId(canvasAssignmentId)
                .courseId(10L)
                .title("기말 프로젝트")
                .description("전체 시스템 구현")
                .dueAt(dueAt)
                .pointsPossible(200)
                .submissionTypes("online_upload,online_text_entry")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        given(assignmentService.findByCanvasAssignmentId(canvasAssignmentId))
                .willReturn(Optional.of(assignment));

        // When & Then
        mockMvc.perform(get("/v1/assignments/canvas/" + canvasAssignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.canvasAssignmentId").value(canvasAssignmentId))
                .andExpect(jsonPath("$.courseId").value(10))
                .andExpect(jsonPath("$.title").value("기말 프로젝트"))
                .andExpect(jsonPath("$.description").value("전체 시스템 구현"))
                .andExpect(jsonPath("$.dueAt").exists())
                .andExpect(jsonPath("$.pointsPossible").value(200))
                .andExpect(jsonPath("$.submissionTypes").value("online_upload,online_text_entry"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }
}
