package com.unisync.course.enrollment.dto;

import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.course.dto.CourseResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Enrollment 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "수강 정보 응답")
public class EnrollmentResponse {

    @Schema(description = "수강 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    @Schema(description = "과목 정보")
    private CourseResponse course;

    @Schema(description = "동기화 리더 여부", example = "true")
    private Boolean isSyncLeader;

    @Schema(description = "동기화 활성화 여부", example = "true")
    private Boolean isSyncEnabled;

    @Schema(description = "수강 등록 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime enrolledAt;

    /**
     * Entity를 Response DTO로 변환
     */
    public static EnrollmentResponse from(Enrollment enrollment) {
        return EnrollmentResponse.builder()
                .id(enrollment.getId())
                .cognitoSub(enrollment.getCognitoSub())
                .course(CourseResponse.from(enrollment.getCourse()))
                .isSyncLeader(enrollment.getIsSyncLeader())
                .isSyncEnabled(enrollment.getIsSyncEnabled())
                .enrolledAt(enrollment.getEnrolledAt())
                .build();
    }
}
