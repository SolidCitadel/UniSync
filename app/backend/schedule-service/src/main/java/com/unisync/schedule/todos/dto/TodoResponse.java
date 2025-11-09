package com.unisync.schedule.todos.dto;

import com.unisync.schedule.common.entity.Todo;
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
public class TodoResponse {

    private Long todoId;
    private String cognitoSub;
    private Long groupId;
    private Long categoryId;
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String status;
    private String priority;
    private Integer progressPercentage;
    private Long parentTodoId;
    private Long scheduleId;
    private Boolean isAiGenerated;
    private LocalDateTime createdAt;
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
                .status(todo.getStatus().name())
                .priority(todo.getPriority().name())
                .progressPercentage(todo.getProgressPercentage())
                .parentTodoId(todo.getParentTodoId())
                .scheduleId(todo.getScheduleId())
                .isAiGenerated(todo.getIsAiGenerated())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .build();
    }
}
