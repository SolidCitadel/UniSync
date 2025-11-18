package com.unisync.schedule.schedules.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.schedules.dto.ScheduleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private String testCognitoSub = "test-cognito-sub";
    private Category testCategory;

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트용 카테고리 생성
        testCategory = Category.builder()
                .cognitoSub(testCognitoSub)
                .name("테스트 카테고리")
                .color("#FF0000")
                .isDefault(false)
                .build();
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    @DisplayName("존재하지 않는 엔드포인트 호출 시 404 반환")
    void testNotFoundEndpoint() throws Exception {
        mockMvc.perform(get("/invalid-path-that-does-not-exist")
                        .header("X-Cognito-Sub", testCognitoSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 일정 ID 조회 시 404 반환")
    void testNotFoundScheduleById() throws Exception {
        Long nonExistentId = 99999L;

        mockMvc.perform(get("/v1/schedules/{scheduleId}", nonExistentId)
                        .header("X-Cognito-Sub", testCognitoSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("일정을 찾을 수 없습니다")));
    }

    @Test
    @DisplayName("잘못된 날짜 범위로 일정 생성 시 400 반환")
    void testInvalidDateRange() throws Exception {
        LocalDateTime startTime = LocalDateTime.of(2025, 11, 20, 10, 0);
        LocalDateTime endTime = LocalDateTime.of(2025, 11, 19, 10, 0); // 시작보다 이전

        ScheduleRequest request = ScheduleRequest.builder()
                .title("잘못된 일정")
                .startTime(startTime)
                .endTime(endTime)
                .categoryId(testCategory.getCategoryId())
                .build();

        mockMvc.perform(post("/v1/schedules")
                        .header("X-Cognito-Sub", testCognitoSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SCHEDULE"))
                .andExpect(jsonPath("$.message").value(containsString("종료 시간은 시작 시간보다 늦어야 합니다")));
    }

    @Test
    @DisplayName("필수 필드 누락 시 400 반환")
    void testMissingRequiredFields() throws Exception {
        ScheduleRequest request = ScheduleRequest.builder()
                // title 누락
                .startTime(LocalDateTime.of(2025, 11, 20, 10, 0))
                .endTime(LocalDateTime.of(2025, 11, 20, 11, 0))
                .categoryId(testCategory.getCategoryId())
                .build();

        mockMvc.perform(post("/v1/schedules")
                        .header("X-Cognito-Sub", testCognitoSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("입력값 검증에 실패했습니다"));
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 일정 생성 시 404 반환")
    void testNotFoundCategory() throws Exception {
        Long nonExistentCategoryId = 99999L;

        ScheduleRequest request = ScheduleRequest.builder()
                .title("테스트 일정")
                .startTime(LocalDateTime.of(2025, 11, 20, 10, 0))
                .endTime(LocalDateTime.of(2025, 11, 20, 11, 0))
                .categoryId(nonExistentCategoryId)
                .build();

        mockMvc.perform(post("/v1/schedules")
                        .header("X-Cognito-Sub", testCognitoSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("카테고리를 찾을 수 없습니다")));
    }

    @Test
    @DisplayName("정상적인 일정 생성 시 201 반환")
    void testCreateScheduleSuccess() throws Exception {
        ScheduleRequest request = ScheduleRequest.builder()
                .title("정상 일정")
                .description("테스트 설명")
                .startTime(LocalDateTime.of(2025, 11, 20, 10, 0))
                .endTime(LocalDateTime.of(2025, 11, 20, 11, 0))
                .categoryId(testCategory.getCategoryId())
                .build();

        mockMvc.perform(post("/v1/schedules")
                        .header("X-Cognito-Sub", testCognitoSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("정상 일정"))
                .andExpect(jsonPath("$.description").value("테스트 설명"));
    }

    @Test
    @DisplayName("다른 사용자의 일정 수정 시 403 반환")
    void testUnauthorizedScheduleUpdate() throws Exception {
        // 테스트 일정 생성
        Schedule schedule = Schedule.builder()
                .cognitoSub("other-user")
                .categoryId(testCategory.getCategoryId())
                .title("다른 사용자의 일정")
                .startTime(LocalDateTime.of(2025, 11, 20, 10, 0))
                .endTime(LocalDateTime.of(2025, 11, 20, 11, 0))
                .isAllDay(false)
                .status(Schedule.ScheduleStatus.TODO)
                .source(Schedule.ScheduleSource.USER)
                .build();
        schedule = scheduleRepository.save(schedule);

        ScheduleRequest request = ScheduleRequest.builder()
                .title("수정된 일정")
                .startTime(LocalDateTime.of(2025, 11, 20, 10, 0))
                .endTime(LocalDateTime.of(2025, 11, 20, 11, 0))
                .categoryId(testCategory.getCategoryId())
                .build();

        mockMvc.perform(put("/v1/schedules/{scheduleId}", schedule.getScheduleId())
                        .header("X-Cognito-Sub", testCognitoSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"))
                .andExpect(jsonPath("$.message").value(containsString("해당 일정에 접근할 권한이 없습니다")));
    }
}
