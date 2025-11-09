package com.unisync.schedule.categories.controller;

import com.unisync.schedule.categories.dto.CategoryRequest;
import com.unisync.schedule.categories.dto.CategoryResponse;
import com.unisync.schedule.categories.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Category", description = "카테고리 관리 API")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "카테고리 목록 조회")
    public ResponseEntity<List<CategoryResponse>> getCategories(@RequestHeader("X-Cognito-Sub") String cognitoSub) {
        return ResponseEntity.ok(categoryService.getCategoriesByUserId(cognitoSub));
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "카테고리 상세 조회")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long categoryId) {
        return ResponseEntity.ok(categoryService.getCategoryById(categoryId));
    }

    @PostMapping
    @Operation(summary = "카테고리 생성")
    public ResponseEntity<CategoryResponse> createCategory(
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request, cognitoSub));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "카테고리 수정")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long categoryId,
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(categoryId, request, cognitoSub));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "카테고리 삭제")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long categoryId,
            @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        categoryService.deleteCategory(categoryId, cognitoSub);
        return ResponseEntity.noContent().build();
    }
}
