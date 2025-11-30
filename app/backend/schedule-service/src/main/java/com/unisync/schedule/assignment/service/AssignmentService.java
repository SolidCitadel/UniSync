package com.unisync.schedule.assignment.service;

import com.unisync.schedule.assignment.dto.AssignmentToScheduleMessage;
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

    /**
     * Assignment 이벤트 처리
     * @param message SQS에서 수신한 Assignment 이벤트 메시지
     */
    @Transactional
    public void processAssignmentEvent(AssignmentToScheduleMessage message) {
        String eventType = message.getEventType();

        log.info("Processing assignment event: eventType={}, assignmentId={}, cognitoSub={}",
                eventType, message.getAssignmentId(), message.getCognitoSub());

        switch (eventType) {
            case "ASSIGNMENT_CREATED":
                createScheduleFromAssignment(message);
                break;
            case "ASSIGNMENT_UPDATED":
                updateScheduleFromAssignment(message);
                break;
            case "ASSIGNMENT_DELETED":
                deleteScheduleFromAssignment(message);
                break;
            default:
                log.warn("Unknown event type: {}", eventType);
        }
    }

    /**
     * Assignment 생성 시 Schedule 생성
     */
    private void createScheduleFromAssignment(AssignmentToScheduleMessage message) {
        // dueAt이 null인 경우 일정 생성 건너뛰기 (마감일 없는 과제는 캘린더에 표시하지 않음)
        if (message.getDueAt() == null) {
            log.info("Skipping assignment without due date: assignmentId={}, title={}",
                    message.getAssignmentId(), message.getTitle());
            return;
        }

        // sourceId로 중복 체크 (canvasAssignmentId-cognitoSub 조합)
        String sourceId = buildSourceId(message.getCanvasAssignmentId(), message.getCognitoSub());

        if (scheduleRepository.existsBySourceAndSourceId(ScheduleSource.CANVAS, sourceId)) {
            log.warn("Schedule already exists: sourceId={}", sourceId);
            return;
        }

        // Canvas 과목별 카테고리 조회 또는 생성 (Phase 1.1)
        Long categoryId = categoryService.getOrCreateCourseCategory(
                message.getCognitoSub(),
                message.getCourseId(),
                message.getCourseName()
        );

        // 하루 종일 이벤트 시간 (날짜의 00:00:00)
        LocalDateTime allDayTime = calculateAllDayStartTime(message.getDueAt());

        // Schedule 생성
        Schedule schedule = Schedule.builder()
                .cognitoSub(message.getCognitoSub())
                .groupId(null) // 과제는 개인 일정
                .categoryId(categoryId)
                .title(buildScheduleTitle(message))
                .description(message.getDescription())
                .location(null)
                .startTime(allDayTime)
                .endTime(allDayTime)  // 하루 종일 이벤트는 start와 end가 동일
                .isAllDay(true)  // Canvas 과제는 하루 종일 이벤트
                .status(ScheduleStatus.TODO)
                .recurrenceRule(null)
                .source(ScheduleSource.CANVAS)
                .sourceId(sourceId)
                .build();

        Schedule saved = scheduleRepository.save(schedule);

        log.info("✅ Created schedule from assignment: scheduleId={}, sourceId={}, title={}",
                saved.getScheduleId(), saved.getSourceId(), saved.getTitle());
    }

    /**
     * Assignment 업데이트 시 Schedule 업데이트
     */
    private void updateScheduleFromAssignment(AssignmentToScheduleMessage message) {
        String sourceId = buildSourceId(message.getCanvasAssignmentId(), message.getCognitoSub());

        // dueAt이 null로 변경되면 일정 삭제 (마감일 없는 과제는 캘린더에 표시하지 않음)
        if (message.getDueAt() == null) {
            scheduleRepository.findBySourceAndSourceId(ScheduleSource.CANVAS, sourceId)
                    .ifPresent(schedule -> {
                        scheduleRepository.delete(schedule);
                        log.info("✅ Deleted schedule due to null dueAt: scheduleId={}, sourceId={}",
                                schedule.getScheduleId(), sourceId);
                    });
            return;
        }

        Schedule schedule = scheduleRepository.findBySourceAndSourceId(ScheduleSource.CANVAS, sourceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Schedule not found for assignment: sourceId=" + sourceId));

        // 하루 종일 이벤트 시간
        LocalDateTime allDayTime = calculateAllDayStartTime(message.getDueAt());

        // 필드 업데이트
        schedule.setTitle(buildScheduleTitle(message));
        schedule.setDescription(message.getDescription());
        schedule.setStartTime(allDayTime);
        schedule.setEndTime(allDayTime);  // 하루 종일 이벤트는 start와 end가 동일

        Schedule updated = scheduleRepository.save(schedule);

        log.info("✅ Updated schedule from assignment: scheduleId={}, sourceId={}",
                updated.getScheduleId(), updated.getSourceId());
    }

    /**
     * Assignment 삭제 시 Schedule 삭제
     */
    private void deleteScheduleFromAssignment(AssignmentToScheduleMessage message) {
        String sourceId = buildSourceId(message.getCanvasAssignmentId(), message.getCognitoSub());

        scheduleRepository.findBySourceAndSourceId(ScheduleSource.CANVAS, sourceId)
                .ifPresent(schedule -> {
                    scheduleRepository.delete(schedule);
                    log.info("✅ Deleted schedule from assignment: scheduleId={}, sourceId={}",
                            schedule.getScheduleId(), sourceId);
                });
    }

    /**
     * sourceId 생성 (canvasAssignmentId-cognitoSub)
     * 동일한 과제도 사용자별로 별도 일정 생성
     */
    private String buildSourceId(Long canvasAssignmentId, String cognitoSub) {
        return String.format("canvas-assignment-%d-%s", canvasAssignmentId, cognitoSub);
    }

    /**
     * Schedule 제목 생성
     * 형식: "[과목명] 과제명"
     */
    private String buildScheduleTitle(AssignmentToScheduleMessage message) {
        return String.format("[%s] %s", message.getCourseName(), message.getTitle());
    }

    /**
     * 하루 종일 이벤트 시작 시간 계산
     * dueAt의 날짜 00:00:00으로 설정 (해당 날짜의 시작)
     */
    private LocalDateTime calculateAllDayStartTime(LocalDateTime dueAt) {
        if (dueAt == null) {
            return LocalDateTime.now().toLocalDate().atStartOfDay();
        }
        return dueAt.toLocalDate().atStartOfDay();
    }
}
