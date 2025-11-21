package com.unisync.schedule.schedules.dto;

import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "일정 생성/수정 요청")
public class ScheduleRequest {

    @NotBlank(message = "Title is required")
    @Schema(description = "일정 제목", example = "데이터베이스 중간고사", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "일정 설명", example = "1~5장 범위")
    private String description;

    @Schema(description = "장소", example = "공학관 101호")
    private String location;

    @NotNull(message = "Start time is required")
    @Schema(description = "시작 일시", example = "2025-04-15T14:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    @Schema(description = "종료 일시", example = "2025-04-15T16:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;

    @Schema(description = "종일 일정 여부", example = "false")
    private Boolean isAllDay = false;

    @NotNull(message = "Category ID is required")
    @Schema(description = "카테고리 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @Schema(description = "그룹 ID (개인 일정인 경우 null)", example = "null")
    private Long groupId;

    @Schema(description = "반복 규칙 (iCal RRULE 형식)", example = "FREQ=WEEKLY;BYDAY=MO,WE,FR")
    private String recurrenceRule;

    // 내부 사용 (Canvas 동기화 등)
    @Schema(description = "일정 소스 (내부 사용)", example = "USER", allowableValues = {"USER", "CANVAS", "GOOGLE_CALENDAR", "TODOIST"})
    private ScheduleSource source;

    @Schema(description = "외부 시스템 ID (내부 사용)", example = "canvas-assignment-12345")
    private String sourceId;
}
