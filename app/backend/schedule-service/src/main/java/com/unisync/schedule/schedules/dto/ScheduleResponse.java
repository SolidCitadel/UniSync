package com.unisync.schedule.schedules.dto;

import com.unisync.schedule.common.entity.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "일정 정보 응답")
public class ScheduleResponse {

    @Schema(description = "일정 ID", example = "1")
    private Long scheduleId;

    @Schema(description = "사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    @Schema(description = "그룹 ID (개인 일정인 경우 null)", example = "null")
    private Long groupId;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "일정 제목", example = "데이터베이스 중간고사")
    private String title;

    @Schema(description = "일정 설명", example = "1~5장 범위")
    private String description;

    @Schema(description = "장소", example = "공학관 101호")
    private String location;

    @Schema(description = "시작 일시", example = "2025-04-15T14:00:00")
    private LocalDateTime startTime;

    @Schema(description = "종료 일시", example = "2025-04-15T16:00:00")
    private LocalDateTime endTime;

    @Schema(description = "종일 일정 여부", example = "false")
    private Boolean isAllDay;

    @Schema(description = "일정 상태", example = "TODO", allowableValues = {"TODO", "IN_PROGRESS", "DONE"})
    private String status;

    @Schema(description = "반복 규칙 (iCal RRULE 형식)", example = "FREQ=WEEKLY;BYDAY=MO,WE,FR")
    private String recurrenceRule;

    @Schema(description = "일정 소스", example = "USER", allowableValues = {"USER", "CANVAS", "GOOGLE_CALENDAR", "TODOIST"})
    private String source;

    @Schema(description = "외부 시스템 ID", example = "canvas-assignment-12345")
    private String sourceId;

    @Schema(description = "생성 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime updatedAt;

    public static ScheduleResponse from(Schedule schedule) {
        return ScheduleResponse.builder()
                .scheduleId(schedule.getScheduleId())
                .cognitoSub(schedule.getCognitoSub())
                .groupId(schedule.getGroupId())
                .categoryId(schedule.getCategoryId())
                .title(schedule.getTitle())
                .description(schedule.getDescription())
                .location(schedule.getLocation())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .isAllDay(schedule.getIsAllDay())
                .status(schedule.getStatus().name())
                .recurrenceRule(schedule.getRecurrenceRule())
                .source(schedule.getSource().name())
                .sourceId(schedule.getSourceId())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
