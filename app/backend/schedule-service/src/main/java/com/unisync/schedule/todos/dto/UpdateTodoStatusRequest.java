package com.unisync.schedule.todos.dto;

import com.unisync.schedule.common.entity.Todo.TodoStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTodoStatusRequest {

    @NotNull(message = "Status is required")
    private TodoStatus status;
}
