package com.unisync.course.assignment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Assignment → Schedule 변환 이벤트 DTO
 * Course-Service → Schedule-Service (SQS: courseservice-to-scheduleservice-assignments)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "과제 → 일정 변환 이벤트 (SQS 메시지)")
public class AssignmentToScheduleEventDto {

    /**
     * 이벤트 타입
     */
    @Schema(description = "이벤트 타입", example = "ASSIGNMENT_CREATED", allowableValues = {"ASSIGNMENT_CREATED", "ASSIGNMENT_UPDATED", "ASSIGNMENT_DELETED"})
    private String eventType; // ASSIGNMENT_CREATED, ASSIGNMENT_UPDATED, ASSIGNMENT_DELETED

    /**
     * Course-Service의 Assignment ID
     */
    @Schema(description = "과제 ID (Course-Service)", example = "1")
    private Long assignmentId;

    /**
     * 사용자 Cognito Sub (글로벌 식별자)
     */
    @Schema(description = "사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    /**
     * Canvas Assignment ID
     */
    @Schema(description = "Canvas 과제 ID", example = "67890")
    private Long canvasAssignmentId;

    /**
     * Canvas Course ID
     */
    @Schema(description = "Canvas 과목 ID", example = "12345")
    private Long canvasCourseId;

    /**
     * 과제 제목
     */
    @Schema(description = "과제 제목", example = "중간고사 프로젝트")
    private String title;

    /**
     * 과제 설명
     */
    @Schema(description = "과제 설명", example = "데이터베이스 설계 및 구현")
    private String description;

    /**
     * 마감일시 (ISO 8601)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "제출 마감일시", example = "2025-04-15T23:59:59")
    private LocalDateTime dueAt;

    /**
     * 배점
     */
    @Schema(description = "배점", example = "100")
    private Integer pointsPossible;

    /**
     * Course-Service의 Course ID
     */
    @Schema(description = "과목 ID (Course-Service)", example = "1")
    private Long courseId;

    /**
     * 과목명 (일정 제목 생성용)
     */
    @Schema(description = "과목명", example = "데이터베이스")
    private String courseName;
}
