package com.unisync.schedule.categories.service;

import com.unisync.schedule.categories.dto.CategoryRequest;
import com.unisync.schedule.categories.dto.CategoryResponse;
import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import com.unisync.schedule.categories.exception.DuplicateCategoryException;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService 단위 테스트")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category testCategory;
    private Category defaultCategory;
    private CategoryRequest testRequest;
    private String cognitoSub;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-user-sub-abc123";
        categoryId = 10L;

        testRequest = new CategoryRequest();
        testRequest.setName("학업");
        testRequest.setColor("#FF5733");
        testRequest.setIcon("📚");

        testCategory = new Category();
        testCategory.setCategoryId(categoryId);
        testCategory.setCognitoSub(cognitoSub);
        testCategory.setName("학업");
        testCategory.setColor("#FF5733");
        testCategory.setIcon("📚");
        testCategory.setIsDefault(false);

        defaultCategory = new Category();
        defaultCategory.setCategoryId(100L);
        defaultCategory.setCognitoSub(cognitoSub);
        defaultCategory.setName("기본");
        defaultCategory.setColor("#000000");
        defaultCategory.setIcon("📌");
        defaultCategory.setIsDefault(true);
    }

    @Test
    @DisplayName("카테고리 생성 성공")
    void createCategory_Success() {
        // given
        given(categoryRepository.existsByCognitoSubAndName(cognitoSub, "학업")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(testCategory);

        // when
        CategoryResponse response = categoryService.createCategory(testRequest, cognitoSub);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCategoryId()).isEqualTo(categoryId);
        assertThat(response.getName()).isEqualTo("학업");
        assertThat(response.getColor()).isEqualTo("#FF5733");
        assertThat(response.getIcon()).isEqualTo("📚");
        assertThat(response.getCognitoSub()).isEqualTo(cognitoSub);

        then(categoryRepository).should().existsByCognitoSubAndName(cognitoSub, "학업");
        then(categoryRepository).should().save(any(Category.class));
    }

    @Test
    @DisplayName("카테고리 생성 실패 - 중복된 이름")
    void createCategory_DuplicateName() {
        // given
        given(categoryRepository.existsByCognitoSubAndName(cognitoSub, "학업")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> categoryService.createCategory(testRequest, cognitoSub))
                .isInstanceOf(DuplicateCategoryException.class)
                .hasMessageContaining("이미 존재하는 카테고리 이름입니다");

        then(categoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("카테고리 ID로 조회 성공")
    void getCategoryById_Success() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(testCategory));

        // when
        CategoryResponse response = categoryService.getCategoryById(categoryId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCategoryId()).isEqualTo(categoryId);
        assertThat(response.getName()).isEqualTo("학업");

        then(categoryRepository).should().findById(categoryId);
    }

    @Test
    @DisplayName("카테고리 ID로 조회 실패 - 존재하지 않는 카테고리")
    void getCategoryById_NotFound() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.getCategoryById(categoryId))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("사용자 ID로 카테고리 목록 조회 성공")
    void getCategoriesByUserId_Success() {
        // given
        List<Category> categories = List.of(testCategory, defaultCategory);
        given(categoryRepository.findByCognitoSub(cognitoSub)).willReturn(categories);

        // when
        List<CategoryResponse> responses = categoryService.getCategoriesByUserId(cognitoSub);

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(responses.get(1).getCognitoSub()).isEqualTo(cognitoSub);

        then(categoryRepository).should().findByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("카테고리 수정 성공")
    void updateCategory_Success() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(testCategory));
        given(categoryRepository.existsByCognitoSubAndName(cognitoSub, "개인")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(testCategory);

        CategoryRequest updateRequest = new CategoryRequest();
        updateRequest.setName("개인");
        updateRequest.setColor("#00FF00");
        updateRequest.setIcon("🏠");

        // when
        CategoryResponse response = categoryService.updateCategory(categoryId, updateRequest, cognitoSub);

        // then
        assertThat(response).isNotNull();
        then(categoryRepository).should().findById(categoryId);
        then(categoryRepository).should().save(any(Category.class));
    }

    @Test
    @DisplayName("카테고리 수정 실패 - 권한 없음")
    void updateCategory_Unauthorized() {
        // given
        String unauthorizedCognitoSub = "different-user-sub-xyz";
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(testCategory));

        // when & then
        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, testRequest, unauthorizedCognitoSub))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("해당 카테고리에 접근할 권한이 없습니다");

        then(categoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("카테고리 수정 실패 - 기본 카테고리는 수정 불가")
    void updateCategory_DefaultCategoryNotModifiable() {
        // given
        given(categoryRepository.findById(defaultCategory.getCategoryId()))
                .willReturn(Optional.of(defaultCategory));

        // when & then
        assertThatThrownBy(() -> categoryService.updateCategory(
                defaultCategory.getCategoryId(), testRequest, cognitoSub))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("기본 카테고리는 수정할 수 없습니다");

        then(categoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("카테고리 수정 실패 - 중복된 이름으로 변경")
    void updateCategory_DuplicateName() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(testCategory));
        given(categoryRepository.existsByCognitoSubAndName(cognitoSub, "개인")).willReturn(true);

        CategoryRequest updateRequest = new CategoryRequest();
        updateRequest.setName("개인");
        updateRequest.setColor("#00FF00");
        updateRequest.setIcon("🏠");

        // when & then
        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, updateRequest, cognitoSub))
                .isInstanceOf(DuplicateCategoryException.class)
                .hasMessageContaining("이미 존재하는 카테고리 이름입니다");

        then(categoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("카테고리 수정 - 같은 이름으로 유지는 허용")
    void updateCategory_SameNameAllowed() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(testCategory));
        given(categoryRepository.save(any(Category.class))).willReturn(testCategory);

        CategoryRequest updateRequest = new CategoryRequest();
        updateRequest.setName("학업"); // 같은 이름
        updateRequest.setColor("#0000FF"); // 색상만 변경
        updateRequest.setIcon("📚");

        // when
        CategoryResponse response = categoryService.updateCategory(categoryId, updateRequest, cognitoSub);

        // then
        assertThat(response).isNotNull();
        then(categoryRepository).should().findById(categoryId);
        then(categoryRepository).should().save(any(Category.class));
        then(categoryRepository).should(never()).existsByCognitoSubAndName(anyString(), anyString());
    }

    @Test
    @DisplayName("카테고리 삭제 성공")
    void deleteCategory_Success() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(testCategory));
        willDoNothing().given(categoryRepository).delete(testCategory);

        // when
        categoryService.deleteCategory(categoryId, cognitoSub);

        // then
        then(categoryRepository).should().findById(categoryId);
        then(categoryRepository).should().delete(testCategory);
    }

    @Test
    @DisplayName("카테고리 삭제 실패 - 권한 없음")
    void deleteCategory_Unauthorized() {
        // given
        String unauthorizedCognitoSub = "different-user-sub-xyz";
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(testCategory));

        // when & then
        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId, unauthorizedCognitoSub))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("해당 카테고리에 접근할 권한이 없습니다");

        then(categoryRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("카테고리 삭제 실패 - 기본 카테고리는 삭제 불가")
    void deleteCategory_DefaultCategoryNotDeletable() {
        // given
        given(categoryRepository.findById(defaultCategory.getCategoryId()))
                .willReturn(Optional.of(defaultCategory));

        // when & then
        assertThatThrownBy(() -> categoryService.deleteCategory(
                defaultCategory.getCategoryId(), cognitoSub))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("기본 카테고리는 삭제할 수 없습니다");

        then(categoryRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("그룹 카테고리 생성 성공")
    void createGroupCategory_Success() {
        // given
        Long groupId = 50L;
        testRequest.setGroupId(groupId);

        Category groupCategory = new Category();
        groupCategory.setCategoryId(categoryId);
        groupCategory.setGroupId(groupId);
        groupCategory.setName("팀 프로젝트");
        groupCategory.setColor("#FF5733");
        groupCategory.setIcon("👥");
        groupCategory.setIsDefault(false);

        given(categoryRepository.existsByGroupIdAndName(groupId, "팀 프로젝트")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(groupCategory);

        testRequest.setName("팀 프로젝트");
        testRequest.setIcon("👥");

        // when
        CategoryResponse response = categoryService.createCategory(testRequest, cognitoSub);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getGroupId()).isEqualTo(groupId);

        then(categoryRepository).should().existsByGroupIdAndName(groupId, "팀 프로젝트");
        then(categoryRepository).should().save(any(Category.class));
    }

    @Test
    @DisplayName("그룹 카테고리 생성 실패 - 중복된 이름")
    void createGroupCategory_DuplicateName() {
        // given
        Long groupId = 50L;
        testRequest.setGroupId(groupId);
        testRequest.setName("팀 프로젝트");

        given(categoryRepository.existsByGroupIdAndName(groupId, "팀 프로젝트")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> categoryService.createCategory(testRequest, cognitoSub))
                .isInstanceOf(DuplicateCategoryException.class)
                .hasMessageContaining("이미 존재하는 카테고리 이름입니다");

        then(categoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("Canvas 카테고리 조회 또는 생성 - 기존 카테고리 존재")
    void getOrCreateCanvasCategory_ExistingCategory() {
        // given
        Category canvasCategory = new Category();
        canvasCategory.setCategoryId(200L);
        canvasCategory.setCognitoSub(cognitoSub);
        canvasCategory.setName("Canvas");
        canvasCategory.setColor("#FF6B6B");
        canvasCategory.setIcon("📚");
        canvasCategory.setIsDefault(true);

        given(categoryRepository.findByCognitoSubAndName(cognitoSub, "Canvas"))
                .willReturn(Optional.of(canvasCategory));

        // when
        Long categoryId = categoryService.getOrCreateCanvasCategory(cognitoSub);

        // then
        assertThat(categoryId).isEqualTo(200L);
        then(categoryRepository).should().findByCognitoSubAndName(cognitoSub, "Canvas");
        then(categoryRepository).should(never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Canvas 카테고리 조회 또는 생성 - 새 카테고리 생성")
    void getOrCreateCanvasCategory_CreateNewCategory() {
        // given
        given(categoryRepository.findByCognitoSubAndName(cognitoSub, "Canvas"))
                .willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(invocation -> {
                    Category saved = invocation.getArgument(0);
                    saved.setCategoryId(300L);
                    return saved;
                });

        // when
        Long categoryId = categoryService.getOrCreateCanvasCategory(cognitoSub);

        // then
        assertThat(categoryId).isEqualTo(300L);
        then(categoryRepository).should().findByCognitoSubAndName(cognitoSub, "Canvas");
        then(categoryRepository).should().save(argThat(category ->
                category.getCognitoSub().equals(cognitoSub) &&
                category.getName().equals("Canvas") &&
                category.getColor().equals("#FF6B6B") &&
                category.getIcon().equals("📚") &&
                category.getIsDefault().equals(true) &&
                category.getGroupId() == null
        ));
    }
}
