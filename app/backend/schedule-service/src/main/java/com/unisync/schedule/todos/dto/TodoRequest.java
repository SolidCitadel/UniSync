package com.unisync.schedule.todos.dto;

import com.unisync.schedule.common.entity.Todo.TodoPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private Long groupId;

    private TodoPriority priority = TodoPriority.MEDIUM;

    private Long parentTodoId; // 서브태스크인 경우

    private Long scheduleId; // 일정 기반 할일인 경우
}
