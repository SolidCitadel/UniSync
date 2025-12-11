package com.unisync.course.common.repository;

import com.unisync.course.common.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /**
     * 특정 사용자와 과목의 수강 관계 조회
     */
    Optional<Enrollment> findByCognitoSubAndCourseId(String cognitoSub, Long courseId);

    /**
     * 특정 사용자가 수강 중인 모든 과목 조회
     */
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course WHERE e.cognitoSub = :cognitoSub")
    List<Enrollment> findAllByCognitoSub(@Param("cognitoSub") String cognitoSub);

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
    boolean existsByCognitoSubAndCourseId(String cognitoSub, Long courseId);

    /**
     * 특정 과목의 Leader 조회
     */
    @Query("SELECT e FROM Enrollment e WHERE e.course.id = :courseId AND e.isSyncLeader = true")
    Optional<Enrollment> findLeaderByCourseId(@Param("courseId") Long courseId);

    /**
     * 특정 사용자의 활성화된 수강 과목만 조회
     */
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course WHERE e.cognitoSub = :cognitoSub AND e.isSyncEnabled = true")
    List<Enrollment> findAllByCognitoSubAndIsSyncEnabled(@Param("cognitoSub") String cognitoSub);

    /**
     * 특정 과목의 활성화된 수강생만 조회 (Assignment → Schedule 이벤트 발행용)
     */
    @Query("SELECT e FROM Enrollment e WHERE e.course.id = :courseId AND e.isSyncEnabled = true")
    List<Enrollment> findAllByCourseIdAndIsSyncEnabled(@Param("courseId") Long courseId);

    /**
     * 전체 활성화된 수강생 조회 (사용자별 assignments 배치 이벤트용)
     */
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course WHERE e.isSyncEnabled = true")
    List<Enrollment> findAllByIsSyncEnabledTrue();

    /**
     * 사용자별 Assignments 조회 (enabled enrollment 대상)
     */
    @Query("""
            SELECT a.id AS assignmentId,
                   a.canvasAssignmentId AS canvasAssignmentId,
                   c.canvasCourseId AS canvasCourseId,
                   c.id AS courseId,
                   c.name AS courseName,
                   a.title AS title,
                   a.description AS description,
                   FUNCTION('DATE_FORMAT', a.dueAt, '%Y-%m-%dT%H:%i:%s') AS dueAt,
                   a.pointsPossible AS pointsPossible
            FROM Enrollment e
            JOIN e.course c
            JOIN Assignment a ON a.course.id = c.id
            WHERE e.cognitoSub = :cognitoSub AND e.isSyncEnabled = true
        """)
    List<AssignmentProjection> findAssignmentsByCognitoSub(@Param("cognitoSub") String cognitoSub);
}
