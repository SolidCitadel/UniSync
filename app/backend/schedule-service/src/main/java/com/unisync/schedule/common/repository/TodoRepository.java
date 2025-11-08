package com.unisync.schedule.common.repository;

import com.unisync.schedule.common.entity.Todo;
import com.unisync.schedule.common.entity.Todo.TodoPriority;
import com.unisync.schedule.common.entity.Todo.TodoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {

    // 사용자 ID로 조회
    List<Todo> findByUserId(Long userId);

    // 그룹 ID로 조회
    List<Todo> findByGroupId(Long groupId);

    // 특정 기간의 할일 조회 (사용자)
    @Query("SELECT t FROM Todo t WHERE t.userId = :userId " +
           "AND t.startDate >= :startDate AND t.dueDate <= :endDate " +
           "ORDER BY t.dueDate, t.priority DESC")
    List<Todo> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // 특정 기간의 할일 조회 (그룹)
    @Query("SELECT t FROM Todo t WHERE t.groupId = :groupId " +
           "AND t.startDate >= :startDate AND t.dueDate <= :endDate " +
           "ORDER BY t.dueDate, t.priority DESC")
    List<Todo> findByGroupIdAndDateRange(
        @Param("groupId") Long groupId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // 카테고리로 조회
    List<Todo> findByCategoryIdAndUserId(Long categoryId, Long userId);

    // 상태로 조회
    List<Todo> findByUserIdAndStatus(Long userId, TodoStatus status);

    // 우선순위로 조회
    List<Todo> findByUserIdAndPriority(Long userId, TodoPriority priority);

    // 서브태스크 조회
    List<Todo> findByParentTodoId(Long parentTodoId);

    // 서브태스크 개수 조회
    long countByParentTodoId(Long parentTodoId);

    // 완료된 서브태스크 개수 조회
    long countByParentTodoIdAndStatus(Long parentTodoId, TodoStatus status);

    // 일정 기반 할일 조회
    List<Todo> findByScheduleId(Long scheduleId);

    // AI 생성 할일 조회
    List<Todo> findByUserIdAndIsAiGenerated(Long userId, Boolean isAiGenerated);
}
