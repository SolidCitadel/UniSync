package com.unisync.schedule.categories.dto;

import com.unisync.schedule.common.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 정보 응답")
public class CategoryResponse {

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    @Schema(description = "그룹 ID (개인 카테고리인 경우 null)", example = "null")
    private Long groupId;

    @Schema(description = "카테고리 이름", example = "학업")
    private String name;

    @Schema(description = "색상 코드", example = "#FF5733")
    private String color;

    @Schema(description = "아이콘", example = "📚")
    private String icon;

    @Schema(description = "기본 카테고리 여부", example = "false")
    private Boolean isDefault;

    @Schema(description = "카테고리 출처 타입", example = "USER_CREATED")
    private String sourceType;

    @Schema(description = "카테고리 출처 ID (연동 카테고리인 경우)", example = "12345")
    private String sourceId;

    @Schema(description = "생성 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime updatedAt;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .cognitoSub(category.getCognitoSub())
                .groupId(category.getGroupId())
                .name(category.getName())
                .color(category.getColor())
                .icon(category.getIcon())
                .isDefault(category.getIsDefault())
                .sourceType(category.getSourceType())
                .sourceId(category.getSourceId())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
