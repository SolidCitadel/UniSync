package com.unisync.schedule.categories.dto;

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
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Color code is required")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be in #RRGGBB format")
    private String color;

    @Size(max = 50, message = "Icon must be at most 50 characters")
    private String icon;

    private Long groupId;
}
