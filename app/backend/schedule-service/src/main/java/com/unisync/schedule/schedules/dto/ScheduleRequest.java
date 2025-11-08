package com.unisync.schedule.schedules.dto;

import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
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
public class ScheduleRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String location;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    private Boolean isAllDay = false;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private Long groupId;

    private String recurrenceRule;

    // 내부 사용 (Canvas 동기화 등)
    private ScheduleSource source;
    private String sourceId;
}
