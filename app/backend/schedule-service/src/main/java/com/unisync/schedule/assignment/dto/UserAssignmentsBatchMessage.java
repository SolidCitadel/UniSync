package com.unisync.schedule.assignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 사용자별 assignments 배치 메시지 (Course-Service → Schedule-Service)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAssignmentsBatchMessage {

    @Schema(description = "이벤트 타입", example = "USER_ASSIGNMENTS_CREATED")
    private String eventType; // USER_ASSIGNMENTS_CREATED

    @Schema(description = "사용자 Cognito Sub")
    private String cognitoSub;

    @Schema(description = "동기화 완료 시각", example = "2025-11-30T12:00:00Z")
    private String syncedAt;

    @Schema(description = "사용자의 assignments 배치")
    private List<AssignmentPayload> assignments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentPayload {
        private Long assignmentId;
        private Long canvasAssignmentId;
        private Long canvasCourseId;
        private Long courseId;
        private String courseName;
        private String title;
        private String description;
        private String dueAt; // ISO 8601 string
        private Double pointsPossible;
    }
}
