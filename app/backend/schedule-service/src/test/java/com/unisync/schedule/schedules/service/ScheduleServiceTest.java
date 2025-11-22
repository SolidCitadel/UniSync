package com.unisync.schedule.schedules.service;

import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.internal.service.GroupPermissionService;
import com.unisync.schedule.schedules.dto.ScheduleRequest;
import com.unisync.schedule.schedules.dto.ScheduleResponse;
import com.unisync.schedule.schedules.exception.InvalidScheduleException;
import com.unisync.schedule.schedules.exception.ScheduleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService 단위 테스트")
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GroupPermissionService groupPermissionService;

    @InjectMocks
    private ScheduleService scheduleService;

    private Schedule testSchedule;
    private ScheduleRequest testRequest;
    private String cognitoSub;
    private Long scheduleId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-user-cognito-sub";
        scheduleId = 100L;
        categoryId = 10L;

        testRequest = new ScheduleRequest();
        testRequest.setCategoryId(categoryId);
        testRequest.setTitle("중간고사 프로젝트");
        testRequest.setDescription("Spring Boot 프로젝트 제출");
        testRequest.setStartTime(LocalDateTime.of(2025, 11, 10, 9, 0));
        testRequest.setEndTime(LocalDateTime.of(2025, 11, 10, 18, 0));
        testRequest.setIsAllDay(false);
        testRequest.setLocation("온라인");
        testRequest.setSource(ScheduleSource.USER);

        testSchedule = new Schedule();
        testSchedule.setScheduleId(scheduleId);
        testSchedule.setCognitoSub(cognitoSub);
        testSchedule.setCategoryId(categoryId);
        testSchedule.setTitle("중간고사 프로젝트");
        testSchedule.setDescription("Spring Boot 프로젝트 제출");
        testSchedule.setStartTime(LocalDateTime.of(2025, 11, 10, 9, 0));
        testSchedule.setEndTime(LocalDateTime.of(2025, 11, 10, 18, 0));
        testSchedule.setIsAllDay(false);
        testSchedule.setLocation("온라인");
        testSchedule.setStatus(ScheduleStatus.TODO);
        testSchedule.setSource(ScheduleSource.USER);
    }

    @Test
    @DisplayName("일정 생성 성공")
    void createSchedule_Success() {
        // given
        Category category = new Category();
        category.setCategoryId(categoryId);
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));
        given(scheduleRepository.save(any(Schedule.class))).willReturn(testSchedule);

        // when
        ScheduleResponse response = scheduleService.createSchedule(testRequest, cognitoSub);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getScheduleId()).isEqualTo(scheduleId);
        assertThat(response.getTitle()).isEqualTo("중간고사 프로젝트");

        then(categoryRepository).should().findById(categoryId);
        then(scheduleRepository).should().save(any(Schedule.class));
    }

    @Test
    @DisplayName("일정 생성 실패 - 종료 시간이 시작 시간보다 이전")
    void createSchedule_InvalidTimeRange() {
        // given
        testRequest.setStartTime(LocalDateTime.of(2025, 11, 10, 18, 0));
        testRequest.setEndTime(LocalDateTime.of(2025, 11, 10, 9, 0));

        // when & then
        assertThatThrownBy(() -> scheduleService.createSchedule(testRequest, cognitoSub))
                .isInstanceOf(InvalidScheduleException.class)
                .hasMessageContaining("종료 시간은 시작 시간보다 늦어야 합니다");

        then(scheduleRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("일정 생성 실패 - 존재하지 않는 카테고리")
    void createSchedule_CategoryNotFound() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scheduleService.createSchedule(testRequest, cognitoSub))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");

        then(scheduleRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("일정 ID로 조회 성공")
    void getScheduleById_Success() {
        // given
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(testSchedule));

        // when
        ScheduleResponse response = scheduleService.getScheduleById(scheduleId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getScheduleId()).isEqualTo(scheduleId);
        assertThat(response.getTitle()).isEqualTo("중간고사 프로젝트");

        then(scheduleRepository).should().findById(scheduleId);
    }

    @Test
    @DisplayName("일정 ID로 조회 실패 - 존재하지 않는 일정")
    void getScheduleById_NotFound() {
        // given
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scheduleService.getScheduleById(scheduleId))
                .isInstanceOf(ScheduleNotFoundException.class)
                .hasMessageContaining("일정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("사용자 ID로 일정 목록 조회 성공")
    void getSchedulesByUserId_Success() {
        // given
        List<Schedule> schedules = List.of(testSchedule);
        given(scheduleRepository.findByCognitoSub(cognitoSub)).willReturn(schedules);

        // when
        List<ScheduleResponse> responses = scheduleService.getSchedulesByUserId(cognitoSub);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getScheduleId()).isEqualTo(scheduleId);
        assertThat(responses.get(0).getCognitoSub()).isEqualTo(cognitoSub);

        then(scheduleRepository).should().findByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("날짜 범위로 일정 조회 성공")
    void getSchedulesByDateRange_Success() {
        // given
        LocalDateTime startDate = LocalDateTime.of(2025, 11, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 11, 30, 23, 59);
        List<Schedule> schedules = List.of(testSchedule);
        given(scheduleRepository.findByCognitoSubAndDateRange(cognitoSub, startDate, endDate))
                .willReturn(schedules);

        // when
        List<ScheduleResponse> responses = scheduleService.getSchedulesByDateRange(cognitoSub, startDate, endDate);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getScheduleId()).isEqualTo(scheduleId);

        then(scheduleRepository).should().findByCognitoSubAndDateRange(cognitoSub, startDate, endDate);
    }

    @Test
    @DisplayName("일정 수정 성공")
    void updateSchedule_Success() {
        // given
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(testSchedule));
        given(scheduleRepository.save(any(Schedule.class))).willReturn(testSchedule);

        ScheduleRequest updateRequest = new ScheduleRequest();
        updateRequest.setCategoryId(categoryId); // 같은 카테고리 ID 사용 (변경 없음)
        updateRequest.setTitle("수정된 제목");
        updateRequest.setDescription("수정된 설명");
        updateRequest.setStartTime(LocalDateTime.of(2025, 11, 11, 10, 0));
        updateRequest.setEndTime(LocalDateTime.of(2025, 11, 11, 17, 0));
        updateRequest.setIsAllDay(false);
        updateRequest.setSource(ScheduleSource.USER);

        // when
        ScheduleResponse response = scheduleService.updateSchedule(scheduleId, updateRequest, cognitoSub);

        // then
        assertThat(response).isNotNull();
        then(scheduleRepository).should().findById(scheduleId);
        then(scheduleRepository).should().save(any(Schedule.class));
    }

    @Test
    @DisplayName("일정 수정 실패 - 권한 없음")
    void updateSchedule_Unauthorized() {
        // given
        String unauthorizedCognitoSub = "unauthorized-user-sub";
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(testSchedule));

        // when & then
        assertThatThrownBy(() -> scheduleService.updateSchedule(scheduleId, testRequest, unauthorizedCognitoSub))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("해당 일정에 접근할 권한이 없습니다");

        then(scheduleRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("일정 상태 변경 성공")
    void updateScheduleStatus_Success() {
        // given
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(testSchedule));
        given(scheduleRepository.save(any(Schedule.class))).willReturn(testSchedule);

        // when
        ScheduleResponse response = scheduleService.updateScheduleStatus(scheduleId, ScheduleStatus.DONE, cognitoSub);

        // then
        assertThat(response).isNotNull();
        then(scheduleRepository).should().findById(scheduleId);
        then(scheduleRepository).should().save(any(Schedule.class));
    }

    @Test
    @DisplayName("일정 삭제 성공")
    void deleteSchedule_Success() {
        // given
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(testSchedule));
        willDoNothing().given(scheduleRepository).delete(testSchedule);

        // when
        scheduleService.deleteSchedule(scheduleId, cognitoSub);

        // then
        then(scheduleRepository).should().findById(scheduleId);
        then(scheduleRepository).should().delete(testSchedule);
    }

    @Test
    @DisplayName("일정 삭제 실패 - 권한 없음")
    void deleteSchedule_Unauthorized() {
        // given
        String unauthorizedCognitoSub = "unauthorized-user-sub";
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(testSchedule));

        // when & then
        assertThatThrownBy(() -> scheduleService.deleteSchedule(scheduleId, unauthorizedCognitoSub))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("해당 일정에 접근할 권한이 없습니다");

        then(scheduleRepository).should(never()).delete(any());
    }
}
