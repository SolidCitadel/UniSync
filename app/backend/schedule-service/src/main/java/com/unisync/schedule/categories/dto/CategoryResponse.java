package com.unisync.schedule.categories.dto;

import com.unisync.schedule.common.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long categoryId;
    private Long userId;
    private Long groupId;
    private String name;
    private String color;
    private String icon;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .userId(category.getUserId())
                .groupId(category.getGroupId())
                .name(category.getName())
                .color(category.getColor())
                .icon(category.getIcon())
                .isDefault(category.getIsDefault())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
