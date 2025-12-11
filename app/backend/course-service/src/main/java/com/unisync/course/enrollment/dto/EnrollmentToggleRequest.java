package com.unisync.course.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enrollment 동기화 활성/비활성 요청 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "수강 동기화 활성/비활성 요청")
public class EnrollmentToggleRequest {

    @NotNull(message = "isSyncEnabled 값은 필수입니다.")
    @Schema(description = "동기화 활성화 여부", example = "false", required = true)
    private Boolean isSyncEnabled;
}
