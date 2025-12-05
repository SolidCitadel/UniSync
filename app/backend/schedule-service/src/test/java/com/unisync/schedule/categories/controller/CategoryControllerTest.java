package com.unisync.schedule.categories.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.schedule.categories.dto.CategoryRequest;
import com.unisync.schedule.categories.dto.CategoryResponse;
import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import com.unisync.schedule.categories.model.CategorySourceType;
import com.unisync.schedule.categories.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CategoryController 단위 테스트
 */
@WebMvcTest(CategoryController.class)
@DisplayName("CategoryController 단위 테스트")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    private ObjectMapper objectMapper;
    private static final String COGNITO_SUB = "test-user-cognito-sub";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ========================================
    // GET /v1/categories 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/categories - 카테고리 목록 조회 성공")
    void getCategories_Success() throws Exception {
        // Given
        List<CategoryResponse> categories = Arrays.asList(
                CategoryResponse.builder()
                        .categoryId(1L)
                        .name("학업")
                        .color("#FF5733")
                        .icon("📚")
                        .isDefault(false)
                        .build(),
                CategoryResponse.builder()
                        .categoryId(2L)
                        .name("Canvas")
                        .color("#0066CC")
                        .icon("🎓")
                        .isDefault(true)
                        .build()
        );

        given(categoryService.getCategories(COGNITO_SUB, null, false, null))
                .willReturn(categories);

        // When & Then
        mockMvc.perform(get("/v1/categories")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("학업"))
                .andExpect(jsonPath("$[1].name").value("Canvas"));

        then(categoryService).should().getCategories(COGNITO_SUB, null, false, null);
    }

    @Test
    @DisplayName("GET /v1/categories - 빈 목록")
    void getCategories_Empty() throws Exception {
        // Given
        given(categoryService.getCategories(COGNITO_SUB, null, false, null))
                .willReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/v1/categories")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ========================================
    // GET /v1/categories/{categoryId} 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/categories/{categoryId} - 카테고리 상세 조회 성공")
    void getCategoryById_Success() throws Exception {
        // Given
        CategoryResponse category = CategoryResponse.builder()
                .categoryId(1L)
                .cognitoSub(COGNITO_SUB)
                .name("학업")
                .color("#FF5733")
                .icon("📚")
                .isDefault(false)
                .build();

        given(categoryService.getCategoryById(1L))
                .willReturn(category);

        // When & Then
        mockMvc.perform(get("/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.name").value("학업"))
                .andExpect(jsonPath("$.color").value("#FF5733"));

        then(categoryService).should().getCategoryById(1L);
    }

    @Test
    @DisplayName("GET /v1/categories/{categoryId} - 존재하지 않는 카테고리 404")
    void getCategoryById_NotFound() throws Exception {
        // Given
        given(categoryService.getCategoryById(999L))
                .willThrow(new CategoryNotFoundException("카테고리를 찾을 수 없습니다: 999"));

        // When & Then
        mockMvc.perform(get("/v1/categories/999"))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // POST /v1/categories 테스트
    // ========================================

    @Test
    @DisplayName("POST /v1/categories - 카테고리 생성 성공")
    void createCategory_Success() throws Exception {
        // Given
        CategoryRequest request = new CategoryRequest();
        request.setName("새 카테고리");
        request.setColor("#00FF00");
        request.setIcon("🆕");

        CategoryResponse response = CategoryResponse.builder()
                .categoryId(1L)
                .cognitoSub(COGNITO_SUB)
                .name("새 카테고리")
                .color("#00FF00")
                .icon("🆕")
                .isDefault(false)
                .build();

        given(categoryService.createCategory(any(CategoryRequest.class), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/categories")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.name").value("새 카테고리"));

        then(categoryService).should().createCategory(any(CategoryRequest.class), eq(COGNITO_SUB));
    }

    // ========================================
    // PUT /v1/categories/{categoryId} 테스트
    // ========================================

    @Test
    @DisplayName("PUT /v1/categories/{categoryId} - 카테고리 수정 성공")
    void updateCategory_Success() throws Exception {
        // Given
        CategoryRequest request = new CategoryRequest();
        request.setName("수정된 카테고리");
        request.setColor("#FF0000");
        request.setIcon("✏️");

        CategoryResponse response = CategoryResponse.builder()
                .categoryId(1L)
                .name("수정된 카테고리")
                .color("#FF0000")
                .icon("✏️")
                .build();

        given(categoryService.updateCategory(eq(1L), any(CategoryRequest.class), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(put("/v1/categories/1")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정된 카테고리"));

        then(categoryService).should().updateCategory(eq(1L), any(CategoryRequest.class), eq(COGNITO_SUB));
    }

    // ========================================
    // DELETE /v1/categories/{categoryId} 테스트
    // ========================================

    @Test
    @DisplayName("DELETE /v1/categories/{categoryId} - 카테고리 삭제 성공")
    void deleteCategory_Success() throws Exception {
        // Given
        willDoNothing().given(categoryService).deleteCategory(1L, COGNITO_SUB);

        // When & Then
        mockMvc.perform(delete("/v1/categories/1")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNoContent());

        then(categoryService).should().deleteCategory(1L, COGNITO_SUB);
    }

    @Test
    @DisplayName("DELETE /v1/categories/{categoryId} - 존재하지 않는 카테고리 삭제 시 404")
    void deleteCategory_NotFound() throws Exception {
        // Given
        willThrow(new CategoryNotFoundException("카테고리를 찾을 수 없습니다: 999"))
                .given(categoryService).deleteCategory(999L, COGNITO_SUB);

        // When & Then
        mockMvc.perform(delete("/v1/categories/999")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNotFound());
    }
}
