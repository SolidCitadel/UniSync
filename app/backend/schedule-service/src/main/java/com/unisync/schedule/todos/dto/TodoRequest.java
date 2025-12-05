package com.unisync.schedule.todos.dto;

import com.unisync.schedule.common.entity.Todo.TodoPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "할일 생성/수정 요청")
public class TodoRequest {

    @NotBlank(message = "Title is required")
    @Schema(description = "할일 제목", example = "알고리즘 과제 제출", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "할일 설명", example = "정렬 알고리즘 구현 및 분석 보고서 작성")
    private String description;

    @NotNull(message = "Start date is required")
    @Schema(description = "시작 날짜", example = "2025-04-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate startDate;

    @NotNull(message = "Due date is required")
    @Schema(description = "마감 날짜", example = "2025-04-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dueDate;

    @Schema(description = "최종 마감일시 (선택)", example = "2025-04-15T23:59:00")
    private LocalDateTime deadline;

    @NotNull(message = "Category ID is required")
    @Schema(description = "카테고리 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @Schema(description = "그룹 ID (개인 할일인 경우 null)", example = "null")
    private Long groupId;

    @Schema(description = "우선순위", example = "MEDIUM", allowableValues = {"LOW", "MEDIUM", "HIGH"})
    private TodoPriority priority = TodoPriority.MEDIUM;

    @Schema(description = "상위 할일 ID (서브태스크인 경우)", example = "null")
    private Long parentTodoId; // 서브태스크인 경우

    @Schema(description = "일정 ID (일정 기반 할일인 경우)", example = "null")
    private Long scheduleId; // 일정 기반 할일인 경우
}
