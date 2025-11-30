package com.unisync.schedule.common.repository;

import com.unisync.schedule.common.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 사용자 ID로 조회
    List<Category> findByCognitoSub(String cognitoSub);

    // 그룹 ID로 조회
    List<Category> findByGroupId(Long groupId);

    // 사용자 또는 그룹의 모든 카테고리 조회
    List<Category> findByCognitoSubOrGroupId(String cognitoSub, Long groupId);

    // 기본 카테고리 조회
    List<Category> findByCognitoSubAndIsDefault(String cognitoSub, Boolean isDefault);

    // 카테고리명 중복 체크 (사용자)
    boolean existsByCognitoSubAndName(String cognitoSub, String name);

    // 카테고리명 중복 체크 (그룹)
    boolean existsByGroupIdAndName(Long groupId, String name);

    // 카테고리명으로 조회 (사용자)
    Optional<Category> findByCognitoSubAndName(String cognitoSub, String name);

    // 그룹 카테고리 삭제 (그룹 삭제 시)
    void deleteByGroupId(Long groupId);

    // 그룹 카테고리 존재 여부
    boolean existsByGroupId(Long groupId);

    /**
     * 외부 소스로 카테고리 조회 (Phase 1.1: 과목별 카테고리)
     * 예: cognitoSub="user-123", sourceType="CANVAS_COURSE", sourceId="10"
     */
    Optional<Category> findByCognitoSubAndSourceTypeAndSourceId(
            String cognitoSub,
            String sourceType,
            String sourceId
    );

    /**
     * 외부 소스 중복 체크
     */
    boolean existsByCognitoSubAndSourceTypeAndSourceId(
            String cognitoSub,
            String sourceType,
            String sourceId
    );
}
