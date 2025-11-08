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
    List<Schedule> findByUserId(Long userId);

    // 그룹 ID로 조회
    List<Schedule> findByGroupId(Long groupId);

    // 특정 기간의 일정 조회 (사용자)
    @Query("SELECT s FROM Schedule s WHERE s.userId = :userId " +
           "AND s.startTime >= :startDate AND s.endTime <= :endDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // 특정 기간의 일정 조회 (그룹)
    @Query("SELECT s FROM Schedule s WHERE s.groupId = :groupId " +
           "AND s.startTime >= :startDate AND s.endTime <= :endDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByGroupIdAndDateRange(
        @Param("groupId") Long groupId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // 카테고리로 조회
    List<Schedule> findByCategoryIdAndUserId(Long categoryId, Long userId);

    // 상태로 조회
    List<Schedule> findByUserIdAndStatus(Long userId, ScheduleStatus status);

    // 외부 소스로 조회 (Canvas, Google Calendar 등)
    Optional<Schedule> findBySourceAndSourceId(ScheduleSource source, String sourceId);

    // 중복 체크 (Canvas 과제 등)
    boolean existsBySourceAndSourceId(ScheduleSource source, String sourceId);
}
