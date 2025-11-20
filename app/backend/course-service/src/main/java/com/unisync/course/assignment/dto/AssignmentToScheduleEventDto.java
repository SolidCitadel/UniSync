package com.unisync.course.assignment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class AssignmentToScheduleEventDto {

    /**
     * 이벤트 타입
     */
    private String eventType; // ASSIGNMENT_CREATED, ASSIGNMENT_UPDATED, ASSIGNMENT_DELETED

    /**
     * Course-Service의 Assignment ID
     */
    private Long assignmentId;

    /**
     * 사용자 Cognito Sub (글로벌 식별자)
     */
    private String cognitoSub;

    /**
     * Canvas Assignment ID
     */
    private Long canvasAssignmentId;

    /**
     * Canvas Course ID
     */
    private Long canvasCourseId;

    /**
     * 과제 제목
     */
    private String title;

    /**
     * 과제 설명
     */
    private String description;

    /**
     * 마감일시 (ISO 8601)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueAt;

    /**
     * 배점
     */
    private Integer pointsPossible;

    /**
     * Course-Service의 Course ID
     */
    private Long courseId;

    /**
     * 과목명 (일정 제목 생성용)
     */
    private String courseName;
}
