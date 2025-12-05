package com.unisync.schedule.schedules.service;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.service.GroupPermissionService;
import com.unisync.schedule.schedules.dto.ScheduleRequest;
import com.unisync.schedule.schedules.dto.ScheduleResponse;
import com.unisync.schedule.schedules.exception.InvalidScheduleException;
import com.unisync.schedule.schedules.exception.ScheduleNotFoundException;
import com.unisync.schedule.todos.dto.TodoWithSubtasksResponse;
import com.unisync.schedule.todos.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.unisync.schedule.categories.exception.CategoryNotFoundException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final CategoryRepository categoryRepository;
    private final GroupPermissionService groupPermissionService;
    private final UserServiceClient userServiceClient;
    private final TodoService todoService;

    /**
     * 일정 생성
     */
    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest request, String cognitoSub) {
        log.info("일정 생성 요청 - cognitoSub: {}, title: {}, groupId: {}", cognitoSub, request.getTitle(), request.getGroupId());

        // 그룹 일정인 경우 쓰기 권한 검증
        groupPermissionService.validateWritePermission(request.getGroupId(), cognitoSub);

        // 날짜 유효성 검증
        validateScheduleDates(request.getStartTime(), request.getEndTime());

        // 카테고리 존재 여부 확인
        validateCategoryAccess(request.getCategoryId(), cognitoSub);

        // Schedule 엔티티 생성
        Schedule schedule = Schedule.builder()
                .cognitoSub(cognitoSub)
                .groupId(request.getGroupId())
                .categoryId(request.getCategoryId())
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isAllDay(request.getIsAllDay() != null ? request.getIsAllDay() : false)
                .status(ScheduleStatus.TODO)
                .recurrenceRule(request.getRecurrenceRule())
                .source(request.getSource() != null ? request.getSource() : ScheduleSource.USER)
                .sourceId(request.getSourceId())
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);
        log.info("일정 생성 완료 - scheduleId: {}", savedSchedule.getScheduleId());

        return ScheduleResponse.from(savedSchedule);
    }

    /**
     * 일정 ID로 조회
     */
    @Transactional(readOnly = true)
    public ScheduleResponse getScheduleById(Long scheduleId, String cognitoSub) {
        log.info("일정 조회 - scheduleId: {}, cognitoSub: {}", scheduleId, cognitoSub);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        validateScheduleReadPermission(schedule, cognitoSub);

        List<TodoWithSubtasksResponse> todos = todoService.getTodosByScheduleIdWithSubtasks(scheduleId);

        return ScheduleResponse.from(schedule, todos);
    }

    /**
     * 사용자의 모든 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByUserId(String cognitoSub) {
        return getSchedulesByUserId(cognitoSub, null);
    }

    /**
     * 사용자의 모든 일정 조회 (상태 필터 포함)
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByUserId(String cognitoSub, ScheduleStatus status) {
        log.info("사용자 일정 전체 조회 - cognitoSub: {}, status: {}", cognitoSub, status);

        List<Schedule> schedules = scheduleRepository.findByCognitoSub(cognitoSub);

        return schedules.stream()
                .filter(schedule -> status == null || status.equals(schedule.getStatus()))
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 개인 + 사용자가 속한 모든 그룹 일정 통합 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesIncludingGroups(String cognitoSub, ScheduleStatus status) {
        log.info("개인 + 그룹 일정 통합 조회 - cognitoSub: {}, status: {}", cognitoSub, status);
        List<Schedule> personal = scheduleRepository.findByCognitoSub(cognitoSub);
        List<Long> groupIds = userServiceClient.getUserGroupIds(cognitoSub);

        List<Schedule> groups = groupIds.isEmpty()
                ? List.of()
                : scheduleRepository.findByGroupIdIn(groupIds);

        return mergeAndFilter(personal, groups, status);
    }

    /**
     * 개인 + 사용자가 속한 모든 그룹 일정 통합 조회 (기간 필터)
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesIncludingGroups(String cognitoSub, LocalDateTime start, LocalDateTime end, ScheduleStatus status) {
        log.info("개인 + 그룹 일정 통합 기간별 조회 - cognitoSub: {}, start: {}, end: {}, status: {}", cognitoSub, start, end, status);
        validateScheduleDates(start, end);

        List<Schedule> personal = scheduleRepository.findByCognitoSubAndDateRange(cognitoSub, start, end);
        List<Long> groupIds = userServiceClient.getUserGroupIds(cognitoSub);

        List<Schedule> groups = groupIds.isEmpty()
                ? List.of()
                : scheduleRepository.findByGroupIdsAndDateRange(groupIds, start, end);

        return mergeAndFilter(personal, groups, status);
    }

    /**
     * 특정 기간의 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByDateRange(String cognitoSub, LocalDateTime start, LocalDateTime end) {
        return getSchedulesByDateRange(cognitoSub, start, end, null);
    }

    /**
     * 특정 기간의 일정 조회 (상태 필터 포함)
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByDateRange(String cognitoSub, LocalDateTime start, LocalDateTime end, ScheduleStatus status) {
        log.info("기간별 일정 조회 - cognitoSub: {}, start: {}, end: {}", cognitoSub, start, end);

        // 날짜 유효성 검증
        validateScheduleDates(start, end);

        List<Schedule> schedules = scheduleRepository.findByCognitoSubAndDateRange(cognitoSub, start, end);

        return schedules.stream()
                .filter(schedule -> status == null || status.equals(schedule.getStatus()))
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 그룹 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByGroupId(Long groupId, String cognitoSub) {
        return getSchedulesByGroupId(groupId, cognitoSub, null);
    }

    /**
     * 그룹 일정 조회 (상태 필터 포함)
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByGroupId(Long groupId, String cognitoSub, ScheduleStatus status) {
        log.info("그룹 일정 전체 조회 - groupId: {}, cognitoSub: {}, status: {}", groupId, cognitoSub, status);

        // 그룹 읽기 권한 검증
        groupPermissionService.validateReadPermission(groupId, cognitoSub);

        List<Schedule> schedules = scheduleRepository.findByGroupId(groupId);

        return schedules.stream()
                .filter(schedule -> status == null || status.equals(schedule.getStatus()))
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 그룹의 특정 기간 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByGroupIdAndDateRange(Long groupId, String cognitoSub, LocalDateTime start, LocalDateTime end) {
        return getSchedulesByGroupIdAndDateRange(groupId, cognitoSub, start, end, null);
    }

    /**
     * 그룹의 특정 기간 일정 조회 (상태 필터 포함)
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByGroupIdAndDateRange(Long groupId, String cognitoSub, LocalDateTime start, LocalDateTime end, ScheduleStatus status) {
        log.info("그룹 기간별 일정 조회 - groupId: {}, cognitoSub: {}, start: {}, end: {}", groupId, cognitoSub, start, end);

        // 그룹 읽기 권한 검증
        groupPermissionService.validateReadPermission(groupId, cognitoSub);

        // 날짜 유효성 검증
        validateScheduleDates(start, end);

        List<Schedule> schedules = scheduleRepository.findByGroupIdAndDateRange(groupId, start, end);

        return schedules.stream()
                .filter(schedule -> status == null || status.equals(schedule.getStatus()))
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 일정 수정
     */
    @Transactional
    public ScheduleResponse updateSchedule(Long scheduleId, ScheduleRequest request, String cognitoSub) {
        log.info("일정 수정 요청 - scheduleId: {}, cognitoSub: {}", scheduleId, cognitoSub);

        // 일정 조회 및 권한 확인
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        validateScheduleOwnership(schedule, cognitoSub);

        // 날짜 유효성 검증
        validateScheduleDates(request.getStartTime(), request.getEndTime());

        // 카테고리 변경 시 존재 여부 확인
        if (!schedule.getCategoryId().equals(request.getCategoryId())) {
            validateCategoryAccess(request.getCategoryId(), cognitoSub);
        }

        // 일정 정보 업데이트
        schedule.setTitle(request.getTitle());
        schedule.setDescription(request.getDescription());
        schedule.setLocation(request.getLocation());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setIsAllDay(request.getIsAllDay() != null ? request.getIsAllDay() : false);
        schedule.setCategoryId(request.getCategoryId());
        schedule.setGroupId(request.getGroupId());
        schedule.setRecurrenceRule(request.getRecurrenceRule());

        Schedule updatedSchedule = scheduleRepository.save(schedule);
        log.info("일정 수정 완료 - scheduleId: {}", scheduleId);

        return ScheduleResponse.from(updatedSchedule);
    }

    /**
     * 일정 상태 변경
     */
    @Transactional
    public ScheduleResponse updateScheduleStatus(Long scheduleId, ScheduleStatus status, String cognitoSub) {
        log.info("일정 상태 변경 요청 - scheduleId: {}, status: {}, cognitoSub: {}", scheduleId, status, cognitoSub);

        // 일정 조회 및 권한 확인
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        validateScheduleOwnership(schedule, cognitoSub);

        // 상태 업데이트
        schedule.setStatus(status);

        Schedule updatedSchedule = scheduleRepository.save(schedule);
        log.info("일정 상태 변경 완료 - scheduleId: {}, status: {}", scheduleId, status);

        return ScheduleResponse.from(updatedSchedule);
    }

    /**
     * 일정 삭제
     */
    @Transactional
    public void deleteSchedule(Long scheduleId, String cognitoSub) {
        log.info("일정 삭제 요청 - scheduleId: {}, cognitoSub: {}", scheduleId, cognitoSub);

        // 일정 조회 및 권한 확인
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        validateScheduleOwnership(schedule, cognitoSub);

        scheduleRepository.delete(schedule);
        log.info("일정 삭제 완료 - scheduleId: {}", scheduleId);
    }

    /**
     * 날짜 유효성 검증
     */
    private void validateScheduleDates(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new InvalidScheduleException("시작 시간과 종료 시간은 필수입니다.");
        }

        if (endTime.isBefore(startTime)) {
            throw new InvalidScheduleException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }

    /**
     * 일정 소유권/권한 검증
     */
    private void validateScheduleOwnership(Schedule schedule, String cognitoSub) {
        if (schedule.getGroupId() != null) {
            // 그룹 일정: User-Service에서 권한 확인
            groupPermissionService.validateWritePermission(schedule.getGroupId(), cognitoSub);
        } else if (!schedule.getCognitoSub().equals(cognitoSub)) {
            // 개인 일정: cognitoSub 일치 확인
            throw new UnauthorizedAccessException("해당 일정에 접근할 권한이 없습니다.");
        }
    }

    /**
     * 카테고리 접근 권한 검증
     */
    private void validateCategoryAccess(Long categoryId, String cognitoSub) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("카테고리를 찾을 수 없습니다. ID: " + categoryId));

        // 추가적으로 카테고리가 해당 사용자 또는 그룹에 속하는지 검증 가능
        // 현재는 존재 여부만 확인
    }

    private void validateScheduleReadPermission(Schedule schedule, String cognitoSub) {
        if (schedule.getGroupId() != null) {
            groupPermissionService.validateReadPermission(schedule.getGroupId(), cognitoSub);
        } else if (!schedule.getCognitoSub().equals(cognitoSub)) {
            throw new UnauthorizedAccessException("해당 일정에 접근할 권한이 없습니다.");
        }
    }

    private List<ScheduleResponse> mergeAndFilter(List<Schedule> personal, List<Schedule> groups, ScheduleStatus status) {
        return Stream.concat(personal.stream(), groups.stream())
                .filter(schedule -> status == null || status.equals(schedule.getStatus()))
                .sorted(Comparator.comparing(Schedule::getStartTime))
                .collect(Collectors.toMap(
                        Schedule::getScheduleId,
                        Function.identity(),
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }
}
