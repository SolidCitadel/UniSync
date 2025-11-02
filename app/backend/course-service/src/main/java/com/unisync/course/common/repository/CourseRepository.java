package com.unisync.course.common.repository;

import com.unisync.course.common.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Course Repository
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * Canvas Course ID로 조회
     * @param canvasCourseId Canvas Course ID
     * @return Course Optional
     */
    Optional<Course> findByCanvasCourseId(Long canvasCourseId);

    /**
     * Canvas Course ID 존재 여부 확인
     * @param canvasCourseId Canvas Course ID
     * @return 존재 여부
     */
    boolean existsByCanvasCourseId(Long canvasCourseId);
}