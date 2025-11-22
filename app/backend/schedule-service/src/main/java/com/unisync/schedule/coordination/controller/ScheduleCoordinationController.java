package com.unisync.schedule.coordination.controller;

import com.unisync.schedule.coordination.dto.FindFreeSlotsRequest;
import com.unisync.schedule.coordination.dto.FindFreeSlotsResponse;
import com.unisync.schedule.coordination.service.ScheduleCoordinationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 일정 조율 API
 */
@Tag(name = "Schedule Coordination", description = "그룹 일정 조율 API")
@RestController
@RequestMapping("/v1/schedules")
@RequiredArgsConstructor
@Slf4j
public class ScheduleCoordinationController {

    private final ScheduleCoordinationService coordinationService;

    @Operation(
            summary = "공강 시간 찾기",
            description = "그룹 멤버들의 일정을 분석하여 모든 멤버가 비어있는 공강 시간을 찾습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공강 시간 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (날짜 형식, minDuration 등)"),
            @ApiResponse(responseCode = "403", description = "그룹 멤버가 아님"),
            @ApiResponse(responseCode = "404", description = "그룹 존재하지 않음")
    })
    @PostMapping("/find-free-slots")
    public ResponseEntity<FindFreeSlotsResponse> findFreeSlots(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody FindFreeSlotsRequest request
    ) {
        log.info("POST /v1/schedules/find-free-slots - cognitoSub: {}, groupId: {}",
                cognitoSub, request.getGroupId());

        FindFreeSlotsResponse response = coordinationService.findFreeSlots(request, cognitoSub);
        return ResponseEntity.ok(response);
    }
}
