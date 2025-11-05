package com.unisync.course.common.repository;

import com.unisync.course.common.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /**
     * 특정 사용자와 과목의 수강 관계 조회
     */
    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    /**
     * 특정 사용자가 수강 중인 모든 과목 조회
     */
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course WHERE e.userId = :userId")
    List<Enrollment> findAllByUserId(@Param("userId") Long userId);

    /**
     * 특정 과목을 수강하는 모든 사용자 조회
     */
    List<Enrollment> findAllByCourseId(Long courseId);

    /**
     * 특정 과목에 수강생이 있는지 확인
     */
    boolean existsByCourseId(Long courseId);

    /**
     * 특정 사용자와 과목의 수강 관계가 있는지 확인
     */
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    /**
     * 특정 과목의 Leader 조회
     */
    @Query("SELECT e FROM Enrollment e WHERE e.course.id = :courseId AND e.isSyncLeader = true")
    Optional<Enrollment> findLeaderByCourseId(@Param("courseId") Long courseId);
}