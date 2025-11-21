package com.unisync.schedule.todos.dto;

import com.unisync.schedule.common.entity.Todo;
import com.unisync.schedule.common.entity.Todo.TodoPriority;
import com.unisync.schedule.common.entity.Todo.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "할일 정보 응답")
public class TodoResponse {

    @Schema(description = "할일 ID", example = "1")
    private Long todoId;

    @Schema(description = "사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    @Schema(description = "그룹 ID (개인 할일인 경우 null)", example = "null")
    private Long groupId;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "할일 제목", example = "알고리즘 과제 제출")
    private String title;

    @Schema(description = "할일 설명", example = "정렬 알고리즘 구현 및 분석 보고서 작성")
    private String description;

    @Schema(description = "시작 날짜", example = "2025-04-01")
    private LocalDate startDate;

    @Schema(description = "마감 날짜", example = "2025-04-15")
    private LocalDate dueDate;

    @Schema(description = "할일 상태", example = "TODO")
    private TodoStatus status;

    @Schema(description = "우선순위", example = "MEDIUM")
    private TodoPriority priority;

    @Schema(description = "진행률 (0-100)", example = "50")
    private Integer progressPercentage;

    @Schema(description = "상위 할일 ID (서브태스크인 경우)", example = "null")
    private Long parentTodoId;

    @Schema(description = "일정 ID (일정 기반 할일인 경우)", example = "null")
    private Long scheduleId;

    @Schema(description = "AI 생성 여부", example = "false")
    private Boolean isAiGenerated;

    @Schema(description = "생성 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime updatedAt;

    public static TodoResponse from(Todo todo) {
        return TodoResponse.builder()
                .todoId(todo.getTodoId())
                .cognitoSub(todo.getCognitoSub())
                .groupId(todo.getGroupId())
                .categoryId(todo.getCategoryId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .startDate(todo.getStartDate())
                .dueDate(todo.getDueDate())
                .status(todo.getStatus())
                .priority(todo.getPriority())
                .progressPercentage(todo.getProgressPercentage())
                .parentTodoId(todo.getParentTodoId())
                .scheduleId(todo.getScheduleId())
                .isAiGenerated(todo.getIsAiGenerated())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .build();
    }
}
