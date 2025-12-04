package com.unisync.schedule.common.repository;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // 사용자 ID로 조회
    List<Schedule> findByCognitoSub(String cognitoSub);

    // 그룹 ID로 조회
    List<Schedule> findByGroupId(Long groupId);

    // 특정 기간의 일정 조회 (사용자)
    @Query("SELECT s FROM Schedule s WHERE s.cognitoSub = :cognitoSub " +
           "AND s.startTime < :endDate AND s.endTime > :startDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByCognitoSubAndDateRange(
        @Param("cognitoSub") String cognitoSub,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // 특정 기간의 일정 조회 (그룹)
    @Query("SELECT s FROM Schedule s WHERE s.groupId = :groupId " +
           "AND s.startTime < :endDate AND s.endTime > :startDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByGroupIdAndDateRange(
        @Param("groupId") Long groupId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // 카테고리로 조회
    List<Schedule> findByCategoryIdAndCognitoSub(Long categoryId, String cognitoSub);

    // 사용자 + 소스별 조회 (Canvas batch 정리용)
    List<Schedule> findByCognitoSubAndSource(String cognitoSub, ScheduleSource source);

    // 카테고리 기반 일정 삭제 (Phase 1.1: 과목 비활성화 시)
    void deleteAllByCognitoSubAndCategoryId(String cognitoSub, Long categoryId);

    // 상태로 조회
    List<Schedule> findByCognitoSubAndStatus(String cognitoSub, ScheduleStatus status);

    // 외부 소스로 조회 (Canvas, Google Calendar 등)
    Optional<Schedule> findBySourceAndSourceId(ScheduleSource source, String sourceId);

    // 중복 체크 (Canvas 과제 등)
    boolean existsBySourceAndSourceId(ScheduleSource source, String sourceId);

    // 그룹 일정 삭제 (그룹 삭제 시)
    void deleteByGroupId(Long groupId);

    // 그룹 일정 존재 여부
    boolean existsByGroupId(Long groupId);

    // 여러 그룹 일정 조회
    List<Schedule> findByGroupIdIn(List<Long> groupIds);

    @Query("SELECT s FROM Schedule s WHERE s.groupId IN :groupIds " +
           "AND s.startTime < :endDate AND s.endTime > :startDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByGroupIdsAndDateRange(
        @Param("groupIds") List<Long> groupIds,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // 여러 사용자 + 그룹의 일정 조회 (일정 조율용)
    @Query("SELECT s FROM Schedule s WHERE " +
           "(s.cognitoSub IN :cognitoSubs OR s.groupId = :groupId) " +
           "AND s.startTime < :endDate AND s.endTime > :startDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByUsersOrGroupAndDateRange(
        @Param("cognitoSubs") List<String> cognitoSubs,
        @Param("groupId") Long groupId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
