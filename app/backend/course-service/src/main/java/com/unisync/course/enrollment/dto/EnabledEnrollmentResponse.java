package com.unisync.course.enrollment.dto;

import com.unisync.course.common.entity.Enrollment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 동기화 활성화된 수강 정보 응답 DTO (내부용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "동기화 활성화 수강 정보 응답")
public class EnabledEnrollmentResponse {

    @Schema(description = "과목 ID (Course-Service)", example = "10")
    private Long courseId;

    @Schema(description = "Canvas Course ID", example = "12345")
    private Long canvasCourseId;

    @Schema(description = "과목명", example = "데이터베이스")
    private String courseName;

    public static EnabledEnrollmentResponse from(Enrollment enrollment) {
        return EnabledEnrollmentResponse.builder()
                .courseId(enrollment.getCourse().getId())
                .canvasCourseId(enrollment.getCourse().getCanvasCourseId())
                .courseName(enrollment.getCourse().getName())
                .build();
    }
}
