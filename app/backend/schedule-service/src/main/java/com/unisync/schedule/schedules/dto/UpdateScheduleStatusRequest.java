package com.unisync.schedule.schedules.dto;

import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScheduleStatusRequest {

    @NotNull(message = "Status is required")
    private ScheduleStatus status;
}
