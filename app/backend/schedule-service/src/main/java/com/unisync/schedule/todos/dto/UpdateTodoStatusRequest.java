package com.unisync.schedule.todos.dto;

import com.unisync.schedule.common.entity.Todo.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "할일 상태 변경 요청")
public class UpdateTodoStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "변경할 상태", example = "DONE", allowableValues = {"TODO", "IN_PROGRESS", "DONE"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private TodoStatus status;
}
