package com.unisync.schedule.coordination.service;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.coordination.algorithm.FreeSlotFinder;
import com.unisync.schedule.coordination.dto.FindFreeSlotsRequest;
import com.unisync.schedule.coordination.dto.FindFreeSlotsResponse;
import com.unisync.schedule.coordination.dto.FreeSlotDto;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.service.GroupPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleCoordinationService 단위 테스트")
class ScheduleCoordinationServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private GroupPermissionService groupPermissionService;

    @Mock
    private FreeSlotFinder freeSlotFinder;

    @InjectMocks
    private ScheduleCoordinationService coordinationService;

    private String cognitoSub;
    private Long groupId;
    private FindFreeSlotsRequest request;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-user-cognito-sub";
        groupId = 1L;

        request = FindFreeSlotsRequest.builder()
                .groupId(groupId)
                .startDate("2025-11-25")
                .endDate("2025-11-30")
                .minDurationMinutes(120)
                .build();
    }

    @Test
    @DisplayName("공강 시간 찾기 성공 - 전체 그룹 멤버")
    void findFreeSlots_success_allMembers() {
        // given
        List<String> allMembers = Arrays.asList("user-a", "user-b", "user-c");
        List<Schedule> schedules = createTestSchedules();
        List<FreeSlotDto> mockFreeSlots = createMockFreeSlots();

        // 권한 검증 통과
        willDoNothing().given(groupPermissionService).validateReadPermission(groupId, cognitoSub);

        // 전체 멤버 조회
        given(userServiceClient.getGroupMemberCognitoSubs(groupId)).willReturn(allMembers);

        // 일정 조회
        given(scheduleRepository.findByUsersOrGroupAndDateRange(
                anyList(), eq(groupId), any(LocalDateTime.class), any(LocalDateTime.class)
        )).willReturn(schedules);

        // 알고리즘 실행
        given(freeSlotFinder.findFreeSlots(
                eq(schedules),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(120),
                isNull(),
                isNull(),
                isNull()
        )).willReturn(mockFreeSlots);

        // when
        FindFreeSlotsResponse response = coordinationService.findFreeSlots(request, cognitoSub);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.getMemberCount()).isEqualTo(3);
        assertThat(response.getFreeSlots()).hasSize(2);
        assertThat(response.getTotalFreeSlotsFound()).isEqualTo(2);
        assertThat(response.getSearchPeriod().getStartDate()).isEqualTo("2025-11-25");
        assertThat(response.getSearchPeriod().getEndDate()).isEqualTo("2025-11-30");

        then(groupPermissionService).should().validateReadPermission(groupId, cognitoSub);
        then(userServiceClient).should().getGroupMemberCognitoSubs(groupId);
        then(scheduleRepository).should().findByUsersOrGroupAndDateRange(
                eq(allMembers), eq(groupId), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        then(freeSlotFinder).should().findFreeSlots(
                eq(schedules), any(LocalDate.class), any(LocalDate.class),
                eq(120), isNull(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("공강 시간 찾기 성공 - 선택된 멤버만")
    void findFreeSlots_success_selectedMembers() {
        // given
        List<String> selectedMembers = Arrays.asList("user-a", "user-b");
        request.setUserIds(selectedMembers);

        List<Schedule> schedules = createTestSchedules();
        List<FreeSlotDto> mockFreeSlots = createMockFreeSlots();

        willDoNothing().given(groupPermissionService).validateReadPermission(groupId, cognitoSub);

        given(scheduleRepository.findByUsersOrGroupAndDateRange(
                eq(selectedMembers), eq(groupId), any(LocalDateTime.class), any(LocalDateTime.class)
        )).willReturn(schedules);

        given(freeSlotFinder.findFreeSlots(
                any(), any(), any(), anyInt(), any(), any(), any()
        )).willReturn(mockFreeSlots);

        // when
        FindFreeSlotsResponse response = coordinationService.findFreeSlots(request, cognitoSub);

        // then
        assertThat(response.getMemberCount()).isEqualTo(2);
        then(userServiceClient).should(never()).getGroupMemberCognitoSubs(any());
        then(scheduleRepository).should().findByUsersOrGroupAndDateRange(
                eq(selectedMembers), eq(groupId), any(LocalDateTime.class), any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("공강 시간 찾기 성공 - 근무 시간 및 요일 필터 적용")
    void findFreeSlots_success_withFilters() {
        // given
        request.setWorkingHoursStart(LocalTime.of(9, 0));
        request.setWorkingHoursEnd(LocalTime.of(18, 0));
        request.setDaysOfWeek(Arrays.asList(1, 3, 5));  // 월, 수, 금

        List<String> allMembers = Arrays.asList("user-a", "user-b");
        List<Schedule> schedules = createTestSchedules();
        List<FreeSlotDto> mockFreeSlots = createMockFreeSlots();

        willDoNothing().given(groupPermissionService).validateReadPermission(groupId, cognitoSub);
        given(userServiceClient.getGroupMemberCognitoSubs(groupId)).willReturn(allMembers);
        given(scheduleRepository.findByUsersOrGroupAndDateRange(
                anyList(), anyLong(), any(), any()
        )).willReturn(schedules);
        given(freeSlotFinder.findFreeSlots(
                any(), any(), any(), anyInt(), any(), any(), anyList()
        )).willReturn(mockFreeSlots);

        // when
        FindFreeSlotsResponse response = coordinationService.findFreeSlots(request, cognitoSub);

        // then
        assertThat(response).isNotNull();
        then(freeSlotFinder).should().findFreeSlots(
                eq(schedules),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(120),
                eq(LocalTime.of(9, 0)),
                eq(LocalTime.of(18, 0)),
                eq(Arrays.asList(1, 3, 5))
        );
    }

    @Test
    @DisplayName("공강 시간 찾기 성공 - 공강 없음")
    void findFreeSlots_success_noFreeSlots() {
        // given
        List<String> allMembers = Arrays.asList("user-a");
        List<Schedule> schedules = createTestSchedules();
        List<FreeSlotDto> emptyFreeSlots = new ArrayList<>();

        willDoNothing().given(groupPermissionService).validateReadPermission(groupId, cognitoSub);
        given(userServiceClient.getGroupMemberCognitoSubs(groupId)).willReturn(allMembers);
        given(scheduleRepository.findByUsersOrGroupAndDateRange(
                anyList(), anyLong(), any(), any()
        )).willReturn(schedules);
        given(freeSlotFinder.findFreeSlots(
                any(), any(), any(), anyInt(), any(), any(), any()
        )).willReturn(emptyFreeSlots);

        // when
        FindFreeSlotsResponse response = coordinationService.findFreeSlots(request, cognitoSub);

        // then
        assertThat(response.getFreeSlots()).isEmpty();
        assertThat(response.getTotalFreeSlotsFound()).isEqualTo(0);
    }

    @Test
    @DisplayName("공강 시간 찾기 실패 - 그룹 멤버 아님")
    void findFreeSlots_fail_notGroupMember() {
        // given
        willThrow(new com.unisync.schedule.common.exception.UnauthorizedAccessException("권한 없음"))
                .given(groupPermissionService).validateReadPermission(groupId, cognitoSub);

        // when & then
        assertThatThrownBy(() -> coordinationService.findFreeSlots(request, cognitoSub))
                .isInstanceOf(com.unisync.schedule.common.exception.UnauthorizedAccessException.class)
                .hasMessageContaining("권한");

        then(userServiceClient).should(never()).getGroupMemberCognitoSubs(any());
        then(scheduleRepository).should(never()).findByUsersOrGroupAndDateRange(
                anyList(), anyLong(), any(), any()
        );
    }

    // =======================================================================
    // Helper methods
    // =======================================================================

    private List<Schedule> createTestSchedules() {
        List<Schedule> schedules = new ArrayList<>();

        Schedule s1 = new Schedule();
        s1.setScheduleId(1L);
        s1.setCognitoSub("user-a");
        s1.setStartTime(LocalDateTime.of(2025, 11, 25, 9, 0));
        s1.setEndTime(LocalDateTime.of(2025, 11, 25, 11, 0));
        schedules.add(s1);

        Schedule s2 = new Schedule();
        s2.setScheduleId(2L);
        s2.setCognitoSub("user-b");
        s2.setStartTime(LocalDateTime.of(2025, 11, 25, 14, 0));
        s2.setEndTime(LocalDateTime.of(2025, 11, 25, 16, 0));
        schedules.add(s2);

        return schedules;
    }

    private List<FreeSlotDto> createMockFreeSlots() {
        List<FreeSlotDto> freeSlots = new ArrayList<>();

        FreeSlotDto slot1 = FreeSlotDto.builder()
                .startTime(LocalDateTime.of(2025, 11, 25, 12, 0))
                .endTime(LocalDateTime.of(2025, 11, 25, 14, 0))
                .durationMinutes(120L)
                .dayOfWeek("TUESDAY")
                .build();

        FreeSlotDto slot2 = FreeSlotDto.builder()
                .startTime(LocalDateTime.of(2025, 11, 25, 16, 0))
                .endTime(LocalDateTime.of(2025, 11, 25, 18, 0))
                .durationMinutes(120L)
                .dayOfWeek("TUESDAY")
                .build();

        freeSlots.add(slot1);
        freeSlots.add(slot2);

        return freeSlots;
    }
}
