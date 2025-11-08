package com.unisync.schedule.schedules.service;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.schedules.dto.ScheduleRequest;
import com.unisync.schedule.schedules.dto.ScheduleResponse;
import com.unisync.schedule.schedules.exception.InvalidScheduleException;
import com.unisync.schedule.schedules.exception.ScheduleNotFoundException;
import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 일정 생성
     */
    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest request, Long userId) {
        log.info("일정 생성 요청 - userId: {}, title: {}", userId, request.getTitle());

        // 날짜 유효성 검증
        validateScheduleDates(request.getStartTime(), request.getEndTime());

        // 카테고리 존재 여부 확인
        validateCategoryAccess(request.getCategoryId(), userId);

        // Schedule 엔티티 생성
        Schedule schedule = Schedule.builder()
                .userId(userId)
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
    public ScheduleResponse getScheduleById(Long scheduleId) {
        log.info("일정 조회 - scheduleId: {}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        return ScheduleResponse.from(schedule);
    }

    /**
     * 사용자의 모든 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByUserId(Long userId) {
        log.info("사용자 일정 전체 조회 - userId: {}", userId);

        List<Schedule> schedules = scheduleRepository.findByUserId(userId);

        return schedules.stream()
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 기간의 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        log.info("기간별 일정 조회 - userId: {}, start: {}, end: {}", userId, start, end);

        // 날짜 유효성 검증
        validateScheduleDates(start, end);

        List<Schedule> schedules = scheduleRepository.findByUserIdAndDateRange(userId, start, end);

        return schedules.stream()
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 일정 수정
     */
    @Transactional
    public ScheduleResponse updateSchedule(Long scheduleId, ScheduleRequest request, Long userId) {
        log.info("일정 수정 요청 - scheduleId: {}, userId: {}", scheduleId, userId);

        // 일정 조회 및 권한 확인
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        validateScheduleOwnership(schedule, userId);

        // 날짜 유효성 검증
        validateScheduleDates(request.getStartTime(), request.getEndTime());

        // 카테고리 변경 시 존재 여부 확인
        if (!schedule.getCategoryId().equals(request.getCategoryId())) {
            validateCategoryAccess(request.getCategoryId(), userId);
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
    public ScheduleResponse updateScheduleStatus(Long scheduleId, ScheduleStatus status, Long userId) {
        log.info("일정 상태 변경 요청 - scheduleId: {}, status: {}, userId: {}", scheduleId, status, userId);

        // 일정 조회 및 권한 확인
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        validateScheduleOwnership(schedule, userId);

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
    public void deleteSchedule(Long scheduleId, Long userId) {
        log.info("일정 삭제 요청 - scheduleId: {}, userId: {}", scheduleId, userId);

        // 일정 조회 및 권한 확인
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        validateScheduleOwnership(schedule, userId);

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
     * 일정 소유권 검증
     */
    private void validateScheduleOwnership(Schedule schedule, Long userId) {
        // 그룹 일정이 아니고, userId가 일치하지 않으면 권한 없음
        if (schedule.getGroupId() == null && !schedule.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("해당 일정에 접근할 권한이 없습니다.");
        }
    }

    /**
     * 카테고리 접근 권한 검증
     */
    private void validateCategoryAccess(Long categoryId, Long userId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("카테고리를 찾을 수 없습니다. ID: " + categoryId));

        // 추가적으로 카테고리가 해당 사용자 또는 그룹에 속하는지 검증 가능
        // 현재는 존재 여부만 확인
    }
}
