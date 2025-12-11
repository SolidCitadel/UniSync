package com.unisync.course.enrollment.controller;

import com.unisync.course.enrollment.dto.EnrollmentResponse;
import com.unisync.course.enrollment.dto.EnrollmentToggleRequest;
import com.unisync.course.enrollment.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Enrollment REST API Controller
 */
@Tag(name = "Enrollment", description = "수강 관리 API")
@RestController
@RequestMapping("/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /**
     * 사용자의 모든 수강 목록 조회
     */
    @Operation(summary = "사용자 수강 목록 조회", description = "사용자가 수강 중인 모든 과목 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<List<EnrollmentResponse>> getUserEnrollments(
            @Parameter(hidden = true) @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        List<EnrollmentResponse> enrollments = enrollmentService.getUserEnrollments(cognitoSub);
        return ResponseEntity.ok(enrollments);
    }

    /**
     * 특정 수강의 동기화 활성화/비활성화 토글
     */
    @Operation(
        summary = "수강 동기화 토글",
        description = "특정 과목의 동기화를 활성화하거나 비활성화합니다. 비활성화 시 해당 과목의 모든 일정이 삭제됩니다."
    )
    @PutMapping("/{enrollmentId}/sync")
    public ResponseEntity<EnrollmentResponse> toggleSyncEnabled(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody EnrollmentToggleRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        EnrollmentResponse response = enrollmentService.toggleSyncEnabled(enrollmentId, request, cognitoSub);
        return ResponseEntity.ok(response);
    }
}
