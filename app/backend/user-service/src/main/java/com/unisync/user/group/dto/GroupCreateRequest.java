package com.unisync.user.group.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 그룹 생성 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "그룹 생성 요청")
public class GroupCreateRequest {

    @NotBlank(message = "그룹명은 필수입니다")
    @Size(max = 100, message = "그룹명은 100자 이하로 입력하세요")
    @Schema(description = "그룹명", example = "팀 프로젝트", required = true)
    private String name;

    @Schema(description = "그룹 설명", example = "소프트웨어 공학 팀 프로젝트")
    private String description;
}
