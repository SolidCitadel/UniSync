package com.unisync.schedule.categories.service;

import com.unisync.schedule.categories.dto.CategoryResponse;
import com.unisync.schedule.categories.model.CategorySourceType;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.service.GroupPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GroupPermissionService groupPermissionService;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void test_getCategories_includeGroupsTrue_returnsPersonalAndGroupCategories() {
        List<Long> groupIds = List.of(2L, 3L);
        Category personal = sampleCategory(1L, "sub", null);
        Category group = sampleCategory(2L, "owner", 2L);

        given(userServiceClient.getUserGroupIds("sub")).willReturn(groupIds);
        given(categoryRepository.findByCognitoSubOrGroupIdIn("sub", groupIds)).willReturn(List.of(personal, group));

        List<CategoryResponse> responses = categoryService.getCategories("sub", null, true, null);

        assertThat(responses).hasSize(2);
        verify(groupPermissionService, never()).validateReadPermission(anyLong(), anyString());
    }

    @Test
    void test_getCategories_withGroupId_validatesReadPermission() {
        Category group = sampleCategory(2L, "owner", 5L);
        given(categoryRepository.findByGroupId(5L)).willReturn(List.of(group));

        List<CategoryResponse> responses = categoryService.getCategories("member", 5L, false, CategorySourceType.USER_CREATED);

        assertThat(responses).hasSize(1);
        verify(groupPermissionService).validateReadPermission(5L, "member");
    }

    private Category sampleCategory(Long id, String cognitoSub, Long groupId) {
        return Category.builder()
                .categoryId(id)
                .cognitoSub(cognitoSub)
                .groupId(groupId)
                .name("Category " + id)
                .color("#FFFFFF")
                .sourceType(CategorySourceType.USER_CREATED.name())
                .isDefault(false)
                .build();
    }
}
