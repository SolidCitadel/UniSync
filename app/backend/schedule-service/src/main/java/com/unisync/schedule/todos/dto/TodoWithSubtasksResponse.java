package com.unisync.schedule.todos.dto;

import com.unisync.schedule.common.entity.Todo;
import com.unisync.schedule.common.entity.Todo.TodoPriority;
import com.unisync.schedule.common.entity.Todo.TodoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoWithSubtasksResponse {

    private Long todoId;
    private String cognitoSub;
    private Long groupId;
    private Long categoryId;
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime deadline;
    private TodoStatus status;
    private TodoPriority priority;
    private Integer progressPercentage;
    private Long parentTodoId;
    private Long scheduleId;
    private Boolean isAiGenerated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TodoWithSubtasksResponse> subtasks;

    public static TodoWithSubtasksResponse from(Todo todo, List<TodoWithSubtasksResponse> subtasks) {
        return TodoWithSubtasksResponse.builder()
                .todoId(todo.getTodoId())
                .cognitoSub(todo.getCognitoSub())
                .groupId(todo.getGroupId())
                .categoryId(todo.getCategoryId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .startDate(todo.getStartDate())
                .dueDate(todo.getDueDate())
                .deadline(todo.getDeadline())
                .status(todo.getStatus())
                .priority(todo.getPriority())
                .progressPercentage(todo.getProgressPercentage())
                .parentTodoId(todo.getParentTodoId())
                .scheduleId(todo.getScheduleId())
                .isAiGenerated(todo.getIsAiGenerated())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .subtasks(subtasks)
                .build();
    }
}
