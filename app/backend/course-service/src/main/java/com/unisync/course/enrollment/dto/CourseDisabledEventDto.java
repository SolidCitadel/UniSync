package com.unisync.course.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 과목 비활성화 이벤트 DTO (Course-Service → Schedule-Service).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "과목 비활성화 이벤트 (SQS 메시지)")
public class CourseDisabledEventDto {

    @Schema(description = "이벤트 타입", example = "COURSE_DISABLED")
    private String eventType;

    @Schema(description = "사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    @Schema(description = "과목 ID (Course-Service)", example = "1")
    private Long courseId;

    @Schema(description = "Canvas 과목 ID", example = "12345")
    private Long canvasCourseId;

    @Schema(description = "과목명", example = "데이터베이스")
    private String courseName;
}
