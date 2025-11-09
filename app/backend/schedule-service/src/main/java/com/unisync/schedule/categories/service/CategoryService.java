package com.unisync.schedule.categories.service;

import com.unisync.schedule.categories.dto.CategoryRequest;
import com.unisync.schedule.categories.dto.CategoryResponse;
import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import com.unisync.schedule.categories.exception.DuplicateCategoryException;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 카테고리 생성
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request, String cognitoSub) {
        log.info("카테고리 생성 요청 - cognitoSub: {}, name: {}", cognitoSub, request.getName());

        // 개인 카테고리인 경우 중복 체크
        if (request.getGroupId() == null) {
            if (categoryRepository.existsByCognitoSubAndName(cognitoSub, request.getName())) {
                throw new DuplicateCategoryException("이미 존재하는 카테고리 이름입니다: " + request.getName());
            }
        } else {
            // 그룹 카테고리인 경우 중복 체크
            if (categoryRepository.existsByGroupIdAndName(request.getGroupId(), request.getName())) {
                throw new DuplicateCategoryException("그룹에 이미 존재하는 카테고리 이름입니다: " + request.getName());
            }
        }

        // Category 엔티티 생성
        Category category = Category.builder()
                .cognitoSub(cognitoSub)
                .groupId(request.getGroupId())
                .name(request.getName())
                .color(request.getColor())
                .icon(request.getIcon())
                .isDefault(false) // 사용자 생성 카테고리는 기본값이 아님
                .build();

        Category savedCategory = categoryRepository.save(category);
        log.info("카테고리 생성 완료 - categoryId: {}", savedCategory.getCategoryId());

        return CategoryResponse.from(savedCategory);
    }

    /**
     * 카테고리 ID로 조회
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long categoryId) {
        log.info("카테고리 조회 - categoryId: {}", categoryId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("카테고리를 찾을 수 없습니다. ID: " + categoryId));

        return CategoryResponse.from(category);
    }

    /**
     * 사용자의 모든 카테고리 조회
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByUserId(String cognitoSub) {
        log.info("사용자 카테고리 전체 조회 - cognitoSub: {}", cognitoSub);

        List<Category> categories = categoryRepository.findByCognitoSub(cognitoSub);

        return categories.stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리 수정
     */
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request, String cognitoSub) {
        log.info("카테고리 수정 요청 - categoryId: {}, cognitoSub: {}", categoryId, cognitoSub);

        // 카테고리 조회 및 권한 확인
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("카테고리를 찾을 수 없습니다. ID: " + categoryId));

        validateCategoryOwnership(category, cognitoSub);

        // 기본 카테고리는 수정 불가
        if (category.getIsDefault()) {
            throw new UnauthorizedAccessException("기본 카테고리는 수정할 수 없습니다.");
        }

        // 카테고리명 변경 시 중복 체크
        if (!category.getName().equals(request.getName())) {
            if (request.getGroupId() == null) {
                // 개인 카테고리
                if (categoryRepository.existsByCognitoSubAndName(cognitoSub, request.getName())) {
                    throw new DuplicateCategoryException("이미 존재하는 카테고리 이름입니다: " + request.getName());
                }
            } else {
                // 그룹 카테고리
                if (categoryRepository.existsByGroupIdAndName(request.getGroupId(), request.getName())) {
                    throw new DuplicateCategoryException("그룹에 이미 존재하는 카테고리 이름입니다: " + request.getName());
                }
            }
        }

        // 카테고리 정보 업데이트
        category.setName(request.getName());
        category.setColor(request.getColor());
        category.setIcon(request.getIcon());
        category.setGroupId(request.getGroupId());

        Category updatedCategory = categoryRepository.save(category);
        log.info("카테고리 수정 완료 - categoryId: {}", categoryId);

        return CategoryResponse.from(updatedCategory);
    }

    /**
     * 카테고리 삭제
     */
    @Transactional
    public void deleteCategory(Long categoryId, String cognitoSub) {
        log.info("카테고리 삭제 요청 - categoryId: {}, cognitoSub: {}", categoryId, cognitoSub);

        // 카테고리 조회 및 권한 확인
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("카테고리를 찾을 수 없습니다. ID: " + categoryId));

        validateCategoryOwnership(category, cognitoSub);

        // 기본 카테고리는 삭제 불가
        if (category.getIsDefault()) {
            throw new UnauthorizedAccessException("기본 카테고리는 삭제할 수 없습니다.");
        }

        categoryRepository.delete(category);
        log.info("카테고리 삭제 완료 - categoryId: {}", categoryId);

        // TODO: 해당 카테고리를 사용하는 일정/할일 처리 로직 필요
        // 옵션 1: 기본 카테고리로 이동
        // 옵션 2: 삭제 전 확인 (일정/할일이 있으면 삭제 불가)
    }

    /**
     * 카테고리 소유권 검증
     */
    private void validateCategoryOwnership(Category category, String cognitoSub) {
        // 그룹 카테고리가 아니고, cognitoSub가 일치하지 않으면 권한 없음
        if (category.getGroupId() == null && !category.getCognitoSub().equals(cognitoSub)) {
            throw new UnauthorizedAccessException("해당 카테고리에 접근할 권한이 없습니다.");
        }
    }
}
