package com.unisync.schedule.coordination.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.unisync.schedule.coordination.algorithm.TimeInterval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * 공강 시간 블록 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreeSlotDto {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    private Long durationMinutes;

    private String dayOfWeek;  // "Monday", "Tuesday", ...

    /**
     * TimeInterval을 FreeSlotDto로 변환
     */
    public static FreeSlotDto from(TimeInterval interval) {
        DayOfWeek dayOfWeek = interval.getStart().getDayOfWeek();

        return FreeSlotDto.builder()
                .startTime(interval.getStart())
                .endTime(interval.getEnd())
                .durationMinutes(interval.getDurationMinutes())
                .dayOfWeek(dayOfWeek.toString())  // "MONDAY" -> "Monday"로 변환 가능
                .build();
    }
}
