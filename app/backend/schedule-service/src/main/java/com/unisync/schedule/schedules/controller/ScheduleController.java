package com.unisync.schedule.schedules.controller;

import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import com.unisync.schedule.schedules.dto.ScheduleRequest;
import com.unisync.schedule.schedules.dto.ScheduleResponse;
import com.unisync.schedule.schedules.dto.UpdateScheduleStatusRequest;
import com.unisync.schedule.schedules.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedule", description = "일정 관리 API")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    @Operation(summary = "일정 목록 조회", description = "사용자의 일정 목록을 조회합니다.")
    public ResponseEntity<List<ScheduleResponse>> getSchedules(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        List<ScheduleResponse> schedules;
        if (startDate != null && endDate != null) {
            schedules = scheduleService.getSchedulesByDateRange(cognitoSub, startDate, endDate);
        } else {
            schedules = scheduleService.getSchedulesByUserId(cognitoSub);
        }
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "일정 상세 조회")
    public ResponseEntity<ScheduleResponse> getScheduleById(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(scheduleService.getScheduleById(scheduleId));
    }

    @PostMapping
    @Operation(summary = "일정 생성")
    public ResponseEntity<ScheduleResponse> createSchedule(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createSchedule(request, cognitoSub));
    }

    @PutMapping("/{scheduleId}")
    @Operation(summary = "일정 수정")
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @PathVariable Long scheduleId,
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return ResponseEntity.ok(scheduleService.updateSchedule(scheduleId, request, cognitoSub));
    }

    @PatchMapping("/{scheduleId}/status")
    @Operation(summary = "일정 상태 변경")
    public ResponseEntity<ScheduleResponse> updateScheduleStatus(
            @PathVariable Long scheduleId,
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody UpdateScheduleStatusRequest request
    ) {
        return ResponseEntity.ok(scheduleService.updateScheduleStatus(scheduleId, request.getStatus(), cognitoSub));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "일정 삭제")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long scheduleId,
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        scheduleService.deleteSchedule(scheduleId, cognitoSub);
        return ResponseEntity.noContent().build();
    }
}
