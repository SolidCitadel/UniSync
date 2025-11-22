package com.unisync.user.group.dto;

import com.unisync.user.common.entity.GroupRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 멤버 역할 변경 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "멤버 역할 변경 요청")
public class UpdateRoleRequest {

    @NotNull(message = "역할은 필수입니다")
    @Schema(description = "새 역할", example = "ADMIN", required = true)
    private GroupRole role;
}
