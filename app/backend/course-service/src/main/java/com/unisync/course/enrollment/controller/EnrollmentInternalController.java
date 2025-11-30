package com.unisync.course.enrollment.controller;

import com.unisync.course.enrollment.dto.EnabledEnrollmentResponse;
import com.unisync.course.enrollment.service.EnrollmentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내부용 Enrollment 조회 컨트롤러 (Lambda 등 내부 호출 전용)
 */
@RestController
@RequestMapping("/internal/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentInternalController {

    private final EnrollmentQueryService enrollmentQueryService;

    /**
     * 동기화 활성화된 수강 목록 조회
     */
    @GetMapping("/enabled")
    @Operation(summary = "동기화 활성화된 수강 목록 조회", description = "cognitoSub의 is_sync_enabled=true 수강 목록 반환 (내부용)")
    public ResponseEntity<List<EnabledEnrollmentResponse>> getEnabledEnrollments(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        List<EnabledEnrollmentResponse> enrollments = enrollmentQueryService.getEnabledEnrollments(cognitoSub);
        return ResponseEntity.ok(enrollments);
    }
}
