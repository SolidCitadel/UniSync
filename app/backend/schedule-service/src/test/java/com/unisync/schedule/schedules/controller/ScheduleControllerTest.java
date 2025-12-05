package com.unisync.schedule.schedules.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import com.unisync.schedule.schedules.dto.ScheduleRequest;
import com.unisync.schedule.schedules.dto.ScheduleResponse;
import com.unisync.schedule.schedules.dto.UpdateScheduleStatusRequest;
import com.unisync.schedule.schedules.exception.ScheduleNotFoundException;
import com.unisync.schedule.schedules.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ScheduleController 단위 테스트
 */
@WebMvcTest(ScheduleController.class)
@DisplayName("ScheduleController 단위 테스트")
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleService scheduleService;

    private ObjectMapper objectMapper;
    private static final String COGNITO_SUB = "test-user-cognito-sub";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ========================================
    // GET /v1/schedules 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/schedules - 일정 목록 조회 성공")
    void getSchedules_Success() throws Exception {
        // Given
        List<ScheduleResponse> schedules = Arrays.asList(
                ScheduleResponse.builder()
                        .scheduleId(1L)
                        .cognitoSub(COGNITO_SUB)
                        .title("중간고사 프로젝트")
                        .status(ScheduleStatus.TODO)
                        .source(ScheduleSource.USER)
                        .build(),
                ScheduleResponse.builder()
                        .scheduleId(2L)
                        .cognitoSub(COGNITO_SUB)
                        .title("기말 발표")
                        .status(ScheduleStatus.TODO)
                        .source(ScheduleSource.CANVAS)
                        .build()
        );

        given(scheduleService.getSchedulesByUserId(COGNITO_SUB, null))
                .willReturn(schedules);

        // When & Then
        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("중간고사 프로젝트"))
                        .andExpect(jsonPath("$[1].title").value("기말 발표"));

        then(scheduleService).should().getSchedulesByUserId(COGNITO_SUB, null);
    }

    @Test
    @DisplayName("GET /v1/schedules - 날짜 범위로 조회")
    void getSchedules_WithDateRange() throws Exception {
        // Given
        List<ScheduleResponse> schedules = Collections.singletonList(
                ScheduleResponse.builder()
                        .scheduleId(1L)
                        .title("11월 일정")
                        .build()
        );

        given(scheduleService.getSchedulesByDateRange(eq(COGNITO_SUB), any(LocalDateTime.class), any(LocalDateTime.class), eq(null)))
                .willReturn(schedules);

        // When & Then
        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("startDate", "2025-11-01T00:00:00")
                        .param("endDate", "2025-11-30T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        then(scheduleService).should().getSchedulesByDateRange(eq(COGNITO_SUB), any(LocalDateTime.class), any(LocalDateTime.class), eq(null));
    }

    @Test
    @DisplayName("GET /v1/schedules?groupId=123 - 그룹 일정 목록 조회 성공")
    void getSchedules_ByGroupId_Success() throws Exception {
        // Given
        Long groupId = 123L;
        List<ScheduleResponse> schedules = Arrays.asList(
                ScheduleResponse.builder()
                        .scheduleId(1L)
                        .groupId(groupId)
                        .title("그룹 회의")
                        .status(ScheduleStatus.TODO)
                        .source(ScheduleSource.USER)
                        .build(),
                ScheduleResponse.builder()
                        .scheduleId(2L)
                        .groupId(groupId)
                        .title("그룹 스터디")
                        .status(ScheduleStatus.TODO)
                        .source(ScheduleSource.USER)
                        .build()
        );

        given(scheduleService.getSchedulesByGroupId(groupId, COGNITO_SUB, null))
                .willReturn(schedules);

        // When & Then
        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("groupId", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("그룹 회의"))
                .andExpect(jsonPath("$[1].title").value("그룹 스터디"));

        then(scheduleService).should().getSchedulesByGroupId(groupId, COGNITO_SUB, null);
    }

    @Test
    @DisplayName("GET /v1/schedules?includeGroups=true - 개인+그룹 일정 통합 조회")
    void getSchedules_IncludeGroups() throws Exception {
        given(scheduleService.getSchedulesIncludingGroups(COGNITO_SUB, null))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("includeGroups", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        then(scheduleService).should().getSchedulesIncludingGroups(COGNITO_SUB, null);
    }

    @Test
    @DisplayName("GET /v1/schedules?includeGroups=true&startDate=...&status=TODO - 기간/상태 필터 포함 통합 조회")
    void getSchedules_IncludeGroups_WithDateAndStatus() throws Exception {
        given(scheduleService.getSchedulesIncludingGroups(eq(COGNITO_SUB), any(LocalDateTime.class), any(LocalDateTime.class), eq(ScheduleStatus.TODO)))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("includeGroups", "true")
                        .param("startDate", "2025-12-01T00:00:00")
                        .param("endDate", "2025-12-31T23:59:59")
                        .param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        then(scheduleService).should().getSchedulesIncludingGroups(eq(COGNITO_SUB), any(LocalDateTime.class), any(LocalDateTime.class), eq(ScheduleStatus.TODO));
    }

    @Test
    @DisplayName("GET /v1/schedules?groupId=123&includeGroups=true - groupId가 우선 적용")
    void getSchedules_GroupIdOverridesIncludeGroups() throws Exception {
        Long groupId = 123L;
        given(scheduleService.getSchedulesByGroupId(eq(groupId), eq(COGNITO_SUB), eq(null)))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("groupId", groupId.toString())
                        .param("includeGroups", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        then(scheduleService).should().getSchedulesByGroupId(groupId, COGNITO_SUB, null);
        then(scheduleService).should(never()).getSchedulesIncludingGroups(anyString(), any());
    }

    @Test
    @DisplayName("GET /v1/schedules?groupId=123&startDate=xxx - 그룹 일정 날짜 범위 조회")
    void getSchedules_ByGroupIdAndDateRange() throws Exception {
        // Given
        Long groupId = 123L;
        List<ScheduleResponse> schedules = Collections.singletonList(
                ScheduleResponse.builder()
                        .scheduleId(1L)
                        .groupId(groupId)
                        .title("11월 그룹 일정")
                        .build()
        );

        given(scheduleService.getSchedulesByGroupIdAndDateRange(
                eq(groupId), eq(COGNITO_SUB), any(LocalDateTime.class), any(LocalDateTime.class), eq(null)))
                .willReturn(schedules);

        // When & Then
        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("groupId", "123")
                        .param("startDate", "2025-11-01T00:00:00")
                        .param("endDate", "2025-11-30T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("11월 그룹 일정"));

        then(scheduleService).should().getSchedulesByGroupIdAndDateRange(
                eq(groupId), eq(COGNITO_SUB), any(LocalDateTime.class), any(LocalDateTime.class), eq(null));
    }

    @Test
    @DisplayName("GET /v1/schedules?groupId=123&status=DONE - 그룹 일정 상태 필터 조회")
    void getSchedules_ByGroupIdWithStatus() throws Exception {
        Long groupId = 123L;
        given(scheduleService.getSchedulesByGroupId(groupId, COGNITO_SUB, ScheduleStatus.DONE))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("groupId", groupId.toString())
                        .param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        then(scheduleService).should().getSchedulesByGroupId(groupId, COGNITO_SUB, ScheduleStatus.DONE);
    }

    @Test
    @DisplayName("GET /v1/schedules?groupId=123 - 그룹 멤버가 아니면 403")
    void getSchedules_ByGroupId_Unauthorized() throws Exception {
        Long groupId = 123L;
        willThrow(new com.unisync.schedule.common.exception.UnauthorizedAccessException("권한 없음"))
                .given(scheduleService).getSchedulesByGroupId(groupId, COGNITO_SUB, null);

        mockMvc.perform(get("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("groupId", "123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));

        then(scheduleService).should().getSchedulesByGroupId(groupId, COGNITO_SUB, null);
    }

    // ========================================
    // GET /v1/schedules/{scheduleId} 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/schedules/{scheduleId} - 일정 상세 조회 성공")
    void getScheduleById_Success() throws Exception {
        // Given
        ScheduleResponse schedule = ScheduleResponse.builder()
                .scheduleId(1L)
                .cognitoSub(COGNITO_SUB)
                .categoryId(10L)
                .title("중간고사 프로젝트")
                .description("Spring Boot 프로젝트 제출")
                .startTime(LocalDateTime.of(2025, 11, 10, 9, 0))
                .endTime(LocalDateTime.of(2025, 11, 10, 18, 0))
                .isAllDay(false)
                .status(ScheduleStatus.TODO)
                .source(ScheduleSource.USER)
                .build();

        given(scheduleService.getScheduleById(1L, COGNITO_SUB))
                .willReturn(schedule);

        // When & Then
        mockMvc.perform(get("/v1/schedules/1")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(1))
                .andExpect(jsonPath("$.title").value("중간고사 프로젝트"))
                .andExpect(jsonPath("$.description").value("Spring Boot 프로젝트 제출"));

        then(scheduleService).should().getScheduleById(1L, COGNITO_SUB);
    }

    @Test
    @DisplayName("GET /v1/schedules/{scheduleId} - 존재하지 않는 일정 404")
    void getScheduleById_NotFound() throws Exception {
        // Given
        given(scheduleService.getScheduleById(999L, COGNITO_SUB))
                .willThrow(new ScheduleNotFoundException("일정을 찾을 수 없습니다: 999"));

        // When & Then
        mockMvc.perform(get("/v1/schedules/999")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // POST /v1/schedules 테스트
    // ========================================

    @Test
    @DisplayName("POST /v1/schedules - 일정 생성 성공")
    void createSchedule_Success() throws Exception {
        // Given
        ScheduleRequest request = new ScheduleRequest();
        request.setCategoryId(10L);
        request.setTitle("새 일정");
        request.setDescription("테스트 일정");
        request.setStartTime(LocalDateTime.of(2025, 11, 15, 10, 0));
        request.setEndTime(LocalDateTime.of(2025, 11, 15, 12, 0));
        request.setIsAllDay(false);

        ScheduleResponse response = ScheduleResponse.builder()
                .scheduleId(1L)
                .cognitoSub(COGNITO_SUB)
                .categoryId(10L)
                .title("새 일정")
                .description("테스트 일정")
                .status(ScheduleStatus.TODO)
                .source(ScheduleSource.USER)
                .build();

        given(scheduleService.createSchedule(any(ScheduleRequest.class), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/schedules")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").value(1))
                .andExpect(jsonPath("$.title").value("새 일정"));

        then(scheduleService).should().createSchedule(any(ScheduleRequest.class), eq(COGNITO_SUB));
    }

    // ========================================
    // PUT /v1/schedules/{scheduleId} 테스트
    // ========================================

    @Test
    @DisplayName("PUT /v1/schedules/{scheduleId} - 일정 수정 성공")
    void updateSchedule_Success() throws Exception {
        // Given
        ScheduleRequest request = new ScheduleRequest();
        request.setCategoryId(10L);
        request.setTitle("수정된 일정");
        request.setDescription("수정된 설명");
        request.setStartTime(LocalDateTime.of(2025, 11, 16, 10, 0));
        request.setEndTime(LocalDateTime.of(2025, 11, 16, 12, 0));
        request.setIsAllDay(false);

        ScheduleResponse response = ScheduleResponse.builder()
                .scheduleId(1L)
                .title("수정된 일정")
                .description("수정된 설명")
                .build();

        given(scheduleService.updateSchedule(eq(1L), any(ScheduleRequest.class), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(put("/v1/schedules/1")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 일정"));

        then(scheduleService).should().updateSchedule(eq(1L), any(ScheduleRequest.class), eq(COGNITO_SUB));
    }

    // ========================================
    // PATCH /v1/schedules/{scheduleId}/status 테스트
    // ========================================

    @Test
    @DisplayName("PATCH /v1/schedules/{scheduleId}/status - 상태 변경 성공")
    void updateScheduleStatus_Success() throws Exception {
        // Given
        UpdateScheduleStatusRequest request = new UpdateScheduleStatusRequest();
        request.setStatus(ScheduleStatus.DONE);

        ScheduleResponse response = ScheduleResponse.builder()
                .scheduleId(1L)
                .title("완료된 일정")
                .status(ScheduleStatus.DONE)
                .build();

        given(scheduleService.updateScheduleStatus(eq(1L), eq(ScheduleStatus.DONE), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(patch("/v1/schedules/1/status")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        then(scheduleService).should().updateScheduleStatus(eq(1L), eq(ScheduleStatus.DONE), eq(COGNITO_SUB));
    }

    // ========================================
    // DELETE /v1/schedules/{scheduleId} 테스트
    // ========================================

    @Test
    @DisplayName("DELETE /v1/schedules/{scheduleId} - 일정 삭제 성공")
    void deleteSchedule_Success() throws Exception {
        // Given
        willDoNothing().given(scheduleService).deleteSchedule(1L, COGNITO_SUB);

        // When & Then
        mockMvc.perform(delete("/v1/schedules/1")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNoContent());

        then(scheduleService).should().deleteSchedule(1L, COGNITO_SUB);
    }

    @Test
    @DisplayName("DELETE /v1/schedules/{scheduleId} - 존재하지 않는 일정 삭제 시 404")
    void deleteSchedule_NotFound() throws Exception {
        // Given
        willThrow(new ScheduleNotFoundException("일정을 찾을 수 없습니다: 999"))
                .given(scheduleService).deleteSchedule(999L, COGNITO_SUB);

        // When & Then
        mockMvc.perform(delete("/v1/schedules/999")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNotFound());
    }
}
