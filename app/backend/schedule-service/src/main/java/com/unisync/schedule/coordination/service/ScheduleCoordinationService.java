package com.unisync.schedule.coordination.service;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.coordination.algorithm.FreeSlotFinder;
import com.unisync.schedule.coordination.dto.FindFreeSlotsRequest;
import com.unisync.schedule.coordination.dto.FindFreeSlotsResponse;
import com.unisync.schedule.coordination.dto.FreeSlotDto;
import com.unisync.schedule.coordination.dto.SearchPeriodDto;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.service.GroupPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 일정 조율 서비스
 *
 * 그룹 멤버들의 공강 시간 찾기 기능 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleCoordinationService {

    private final ScheduleRepository scheduleRepository;
    private final UserServiceClient userServiceClient;
    private final GroupPermissionService groupPermissionService;
    private final FreeSlotFinder freeSlotFinder;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 공강 시간 찾기
     *
     * @param request    요청 파라미터
     * @param cognitoSub 요청자 cognitoSub
     * @return 공강 시간 목록
     */
    @Transactional(readOnly = true)
    public FindFreeSlotsResponse findFreeSlots(FindFreeSlotsRequest request, String cognitoSub) {
        log.info("공강 시간 조회 요청 - groupId: {}, cognitoSub: {}, period: {} ~ {}",
                request.getGroupId(), cognitoSub, request.getStartDate(), request.getEndDate());

        // 1. 권한 확인: 요청자가 그룹 멤버인지 검증
        groupPermissionService.validateReadPermission(request.getGroupId(), cognitoSub);

        // 2. 대상 멤버 결정
        List<String> targetCognitoSubs;
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            // 특정 멤버들만
            targetCognitoSubs = request.getUserIds();
            log.debug("선택된 멤버 기준 조회: {} 명", targetCognitoSubs.size());
        } else {
            // 전체 그룹 멤버
            targetCognitoSubs = userServiceClient.getGroupMemberCognitoSubs(request.getGroupId());
            log.debug("전체 그룹 멤버 기준 조회: {} 명", targetCognitoSubs.size());
        }

        // 3. 일정 조회 (개인 일정 + 그룹 일정)
        LocalDate startDate = LocalDate.parse(request.getStartDate(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(request.getEndDate(), DATE_FORMATTER);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Schedule> schedules = scheduleRepository.findByUsersOrGroupAndDateRange(
                targetCognitoSubs,
                request.getGroupId(),
                startDateTime,
                endDateTime
        );

        log.debug("조회된 일정 개수: {}", schedules.size());

        // 4. 알고리즘 실행: 공강 시간 찾기
        List<FreeSlotDto> freeSlots = freeSlotFinder.findFreeSlots(
                schedules,
                startDate,
                endDate,
                request.getMinDurationMinutes(),
                request.getWorkingHoursStart(),
                request.getWorkingHoursEnd(),
                request.getDaysOfWeek()
        );

        // 5. 응답 생성
        return FindFreeSlotsResponse.builder()
                .groupId(request.getGroupId())
                .groupName(null)  // 향후 User-Service에서 조회 가능
                .memberCount(targetCognitoSubs.size())
                .searchPeriod(SearchPeriodDto.builder()
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .build())
                .freeSlots(freeSlots)
                .build();
    }
}
