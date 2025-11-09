package com.unisync.schedule.schedules.dto;

import com.unisync.schedule.common.entity.Schedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResponse {

    private Long scheduleId;
    private String cognitoSub;
    private Long groupId;
    private Long categoryId;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean isAllDay;
    private String status;
    private String recurrenceRule;
    private String source;
    private String sourceId;
    private LocalDateTime createdAt;
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
