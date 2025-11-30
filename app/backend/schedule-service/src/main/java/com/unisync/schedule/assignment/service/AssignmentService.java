package com.unisync.schedule.assignment.service;

import com.unisync.schedule.assignment.dto.UserAssignmentsBatchMessage;
import com.unisync.schedule.assignment.dto.UserAssignmentsBatchMessage.AssignmentPayload;
import com.unisync.schedule.categories.service.CategoryService;
import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import com.unisync.schedule.common.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assignment → Schedule 변환 서비스
 * Course-Service에서 발행한 Assignment 이벤트를 처리하여 Schedule 생성/수정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final ScheduleRepository scheduleRepository;
    private final CategoryService categoryService;

    @Transactional
    public void processAssignmentsBatch(UserAssignmentsBatchMessage message) {
        if (!"USER_ASSIGNMENTS_CREATED".equals(message.getEventType())) {
            log.warn("Unknown assignment batch event type: {}", message.getEventType());
            return;
        }

        String cognitoSub = message.getCognitoSub();
        List<AssignmentPayload> assignments = message.getAssignments() != null
                ? message.getAssignments()
                : Collections.emptyList();

        // 기존 Canvas 일정 조회 (사용자별)
        List<Schedule> existingSchedules = scheduleRepository.findByCognitoSubAndSource(
                cognitoSub, ScheduleSource.CANVAS);
        Map<String, Schedule> existingBySourceId = existingSchedules.stream()
                .collect(Collectors.toMap(Schedule::getSourceId, s -> s));

        Set<String> incomingSourceIds = new HashSet<>();

        for (AssignmentPayload payload : assignments) {
            if (payload.getCanvasAssignmentId() == null) {
                continue;
            }

            String sourceId = buildSourceId(payload.getCanvasAssignmentId(), cognitoSub);
            LocalDateTime dueAt = parseDateTime(payload.getDueAt());

            // dueAt이 없으면 기존 일정 삭제 후 skip
            if (dueAt == null) {
                if (existingBySourceId.containsKey(sourceId)) {
                    scheduleRepository.delete(existingBySourceId.get(sourceId));
                }
                incomingSourceIds.add(sourceId);
                continue;
            }

            incomingSourceIds.add(sourceId);
            Schedule schedule = existingBySourceId.get(sourceId);
            Long categoryId = categoryService.getOrCreateCourseCategory(
                    cognitoSub,
                    payload.getCourseId(),
                    payload.getCourseName()
            );

            if (schedule == null) {
                schedule = buildScheduleFromPayload(payload, cognitoSub, categoryId, dueAt, sourceId);
            } else {
                updateScheduleFromPayload(schedule, payload, dueAt);
            }

            scheduleRepository.save(schedule);
        }

        // 배치에 포함되지 않은 기존 Canvas 일정 삭제 (비활성 과목 등)
        for (Schedule schedule : existingSchedules) {
            if (!incomingSourceIds.contains(schedule.getSourceId())) {
                scheduleRepository.delete(schedule);
            }
        }
    }

    /**
     * Assignment 생성 시 Schedule 생성
     */
    private Schedule buildScheduleFromPayload(AssignmentPayload payload,
                                              String cognitoSub,
                                              Long categoryId,
                                              LocalDateTime dueAt,
                                              String sourceId) {
        LocalDateTime allDayTime = calculateAllDayStartTime(dueAt);

        return Schedule.builder()
                .cognitoSub(cognitoSub)
                .groupId(null)
                .categoryId(categoryId)
                .title(buildScheduleTitle(payload))
                .description(payload.getDescription())
                .location(null)
                .startTime(allDayTime)
                .endTime(allDayTime)
                .isAllDay(true)
                .status(ScheduleStatus.TODO)
                .recurrenceRule(null)
                .source(ScheduleSource.CANVAS)
                .sourceId(sourceId)
                .build();
    }

    private void updateScheduleFromPayload(Schedule schedule,
                                           AssignmentPayload payload,
                                           LocalDateTime dueAt) {
        LocalDateTime allDayTime = calculateAllDayStartTime(dueAt);
        schedule.setTitle(buildScheduleTitle(payload));
        schedule.setDescription(payload.getDescription());
        schedule.setStartTime(allDayTime);
        schedule.setEndTime(allDayTime);
        schedule.setIsAllDay(true);
    }

    private String buildSourceId(Long canvasAssignmentId, String cognitoSub) {
        return String.format("canvas-assignment-%d-%s", canvasAssignmentId, cognitoSub);
    }

    private String buildScheduleTitle(AssignmentPayload payload) {
        return String.format("[%s] %s", payload.getCourseName(), payload.getTitle());
    }

    private LocalDateTime calculateAllDayStartTime(LocalDateTime dueAt) {
        if (dueAt == null) {
            return LocalDateTime.now().toLocalDate().atStartOfDay();
        }
        return dueAt.toLocalDate().atStartOfDay();
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        String normalized = dateTimeStr.replace("Z", "").split("\\.")[0];
        return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
