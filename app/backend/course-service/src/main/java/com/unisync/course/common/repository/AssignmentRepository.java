package com.unisync.course.common.repository;

import com.unisync.course.common.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Assignment Repository
 */
@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /**
     * Canvas Assignment ID로 조회 (UNIQUE 컬럼)
     * @param canvasAssignmentId Canvas Assignment ID
     * @return Assignment Optional
     */
    Optional<Assignment> findByCanvasAssignmentId(Long canvasAssignmentId);

    /**
     * Canvas Assignment ID 존재 여부 확인 (중복 방지)
     * @param canvasAssignmentId Canvas Assignment ID
     * @return 존재 여부
     */
    boolean existsByCanvasAssignmentId(Long canvasAssignmentId);
}