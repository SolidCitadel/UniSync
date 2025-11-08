package com.unisync.schedule.common.repository;

import com.unisync.schedule.common.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 사용자 ID로 조회
    List<Category> findByUserId(Long userId);

    // 그룹 ID로 조회
    List<Category> findByGroupId(Long groupId);

    // 사용자 또는 그룹의 모든 카테고리 조회
    List<Category> findByUserIdOrGroupId(Long userId, Long groupId);

    // 기본 카테고리 조회
    List<Category> findByUserIdAndIsDefault(Long userId, Boolean isDefault);

    // 카테고리명 중복 체크 (사용자)
    boolean existsByUserIdAndName(Long userId, String name);

    // 카테고리명 중복 체크 (그룹)
    boolean existsByGroupIdAndName(Long groupId, String name);

    // 카테고리명으로 조회 (사용자)
    Optional<Category> findByUserIdAndName(Long userId, String name);
}
