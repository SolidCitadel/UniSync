package com.unisync.user.group.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 그룹 수정 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "그룹 수정 요청")
public class GroupUpdateRequest {

    @Size(max = 100, message = "그룹명은 100자 이하로 입력하세요")
    @Schema(description = "그룹명", example = "팀 프로젝트 (수정)")
    private String name;

    @Schema(description = "그룹 설명", example = "업데이트된 설명")
    private String description;
}
