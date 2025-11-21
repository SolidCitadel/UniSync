package com.unisync.schedule.todos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "할일 진행률 변경 요청")
public class UpdateTodoProgressRequest {

    @NotNull(message = "Progress percentage is required")
    @Min(value = 0, message = "Progress percentage must be at least 0")
    @Max(value = 100, message = "Progress percentage must be at most 100")
    @Schema(description = "진행률 (0-100)", example = "75", minimum = "0", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer progressPercentage;
}
