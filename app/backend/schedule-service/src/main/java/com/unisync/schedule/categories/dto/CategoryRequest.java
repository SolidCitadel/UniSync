package com.unisync.schedule.categories.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 생성/수정 요청")
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be at most 100 characters")
    @Schema(description = "카테고리 이름", example = "학업", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Color code is required")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be in #RRGGBB format")
    @Schema(description = "색상 코드 (#RRGGBB 형식)", example = "#FF5733", pattern = "^#[0-9A-Fa-f]{6}$", requiredMode = Schema.RequiredMode.REQUIRED)
    private String color;

    @Size(max = 50, message = "Icon must be at most 50 characters")
    @Schema(description = "아이콘 (이모지 또는 아이콘 이름)", example = "📚", maxLength = 50)
    private String icon;

    @Schema(description = "그룹 ID (개인 카테고리인 경우 null)", example = "null")
    private Long groupId;
}
