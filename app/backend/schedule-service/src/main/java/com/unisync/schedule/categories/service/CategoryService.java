package com.unisync.schedule.categories.service;

import com.unisync.schedule.categories.dto.CategoryRequest;
import com.unisync.schedule.categories.dto.CategoryResponse;
import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import com.unisync.schedule.categories.exception.DuplicateCategoryException;
import com.unisync.schedule.categories.model.CategorySourceType;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.internal.service.GroupPermissionService;
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
    private final GroupPermissionService groupPermissionService;

    private static final String USER_CREATED = CategorySourceType.USER_CREATED.name();
    private static final String CANVAS_COURSE = CategorySourceType.CANVAS_COURSE.name();

    /**
     * 카테고리 생성
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request, String cognitoSub) {
        log.info("카테고리 생성 요청 - cognitoSub: {}, name: {}, groupId: {}", cognitoSub, request.getName(), request.getGroupId());

        // 그룹 카테고리인 경우 쓰기 권한 검증
        groupPermissionService.validateWritePermission(request.getGroupId(), cognitoSub);

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
                .sourceType(USER_CREATED)
                .sourceId(null)
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
    public List<CategoryResponse> getCategoriesByUserId(String cognitoSub, CategorySourceType sourceType) {
        log.info("사용자 카테고리 조회 - cognitoSub: {}, sourceType: {}", cognitoSub,
                sourceType != null ? sourceType.name() : "ALL");

        List<Category> categories = sourceType == null
                ? categoryRepository.findByCognitoSub(cognitoSub)
                : categoryRepository.findByCognitoSubAndSourceType(cognitoSub, sourceType.name());

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

        // 연동 카테고리는 수정 불가
        if (isLinkedCategory(category)) {
            throw new UnauthorizedAccessException("연동된 카테고리는 수정할 수 없습니다.");
        }

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

        // 연동 카테고리는 삭제 불가
        if (isLinkedCategory(category)) {
            throw new UnauthorizedAccessException("연동된 카테고리는 삭제할 수 없습니다.");
        }

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
     * Canvas 과제용 기본 카테고리 조회 또는 생성
     * Assignment → Schedule 변환 시 사용 (Phase 1.0 - 단일 Canvas 카테고리)
     *
     * @deprecated Phase 1.1부터 getOrCreateCourseCategory 사용
     */
    @Deprecated
    @Transactional
    public Long getOrCreateCanvasCategory(String cognitoSub) {
        String canvasCategoryName = "Canvas";

        // 기존 Canvas 카테고리 조회
        return categoryRepository.findByCognitoSubAndName(cognitoSub, canvasCategoryName)
                .map(Category::getCategoryId)
                .orElseGet(() -> {
                    // Canvas 카테고리 없으면 생성
                    Category canvasCategory = Category.builder()
                            .cognitoSub(cognitoSub)
                            .groupId(null)
                            .name(canvasCategoryName)
                            .color("#FF6B6B") // Canvas 빨강 계열
                            .icon("📚")
                            .isDefault(true) // Canvas 카테고리는 기본 카테고리
                            .build();

                    Category saved = categoryRepository.save(canvasCategory);
                    log.info("✅ Created default Canvas category for user: cognitoSub={}, categoryId={}",
                            cognitoSub, saved.getCategoryId());

                    return saved.getCategoryId();
                });
    }

    /**
     * Canvas 과목별 카테고리 조회 또는 생성 (Phase 1.1)
     * Assignment → Schedule 변환 시 사용
     *
     * @param cognitoSub 사용자 Cognito Sub
     * @param courseId Course ID (source_id로 사용)
     * @param courseName 과목명 (카테고리 이름으로 사용)
     * @return 카테고리 ID
     */
    @Transactional
    public Long getOrCreateCourseCategory(String cognitoSub, Long courseId, String courseName) {
        String sourceType = CANVAS_COURSE;
        String sourceId = courseId.toString();

        // 기존 과목 카테고리 조회 (source_type + source_id로)
        return categoryRepository.findByCognitoSubAndSourceTypeAndSourceId(cognitoSub, sourceType, sourceId)
                .map(Category::getCategoryId)
                .orElseGet(() -> {
                    // 과목 카테고리 없으면 생성
                    Category courseCategory = Category.builder()
                            .cognitoSub(cognitoSub)
                            .groupId(null)
                            .name(courseName) // "데이터구조", "알고리즘" 등
                            .color(generateColorForCourse(courseId)) // 과목별 색상
                            .icon("📚")
                            .isDefault(true) // Canvas 과목 카테고리는 기본 카테고리
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .build();

                    Category saved = categoryRepository.save(courseCategory);
                    log.info("✅ Created course category: cognitoSub={}, courseId={}, courseName={}, categoryId={}",
                            cognitoSub, courseId, courseName, saved.getCategoryId());

                    return saved.getCategoryId();
                });
    }

    /**
     * 과목별 색상 자동 생성
     * courseId를 해시하여 일관된 색상 할당
     */
    private String generateColorForCourse(Long courseId) {
        // 과목별 미리 정의된 색상 팔레트
        String[] colors = {
                "#FF6B6B", // 빨강
                "#4ECDC4", // 청록
                "#45B7D1", // 파랑
                "#FFA07A", // 주황
                "#98D8C8", // 민트
                "#F7DC6F", // 노랑
                "#BB8FCE", // 보라
                "#85C1E2"  // 하늘
        };

        // courseId를 색상 개수로 나눈 나머지로 색상 선택
        int index = (int) (courseId % colors.length);
        return colors[index];
    }

    /**
     * 연동(비사용자) 카테고리 여부
     */
    private boolean isLinkedCategory(Category category) {
        return category.getSourceType() != null && !USER_CREATED.equals(category.getSourceType());
    }

    /**
     * 카테고리 소유권/권한 검증
     */
    private void validateCategoryOwnership(Category category, String cognitoSub) {
        if (category.getGroupId() != null) {
            // 그룹 카테고리: User-Service에서 권한 확인
            groupPermissionService.validateWritePermission(category.getGroupId(), cognitoSub);
        } else if (!category.getCognitoSub().equals(cognitoSub)) {
            // 개인 카테고리: cognitoSub 일치 확인
            throw new UnauthorizedAccessException("해당 카테고리에 접근할 권한이 없습니다.");
        }
    }
}
