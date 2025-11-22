package com.unisync.schedule.internal.service;

import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.common.repository.TodoRepository;
import com.unisync.schedule.internal.dto.GroupDataDeleteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 관련 Internal Service
 *
 * User-Service에서 그룹 삭제 시 호출됨
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalGroupService {

    private final ScheduleRepository scheduleRepository;
    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 그룹의 모든 데이터 삭제
     *
     * 그룹 삭제 시 User-Service에서 호출. 순서:
     * 1. 할일 삭제 (일정 참조)
     * 2. 일정 삭제
     * 3. 카테고리 삭제
     *
     * @param groupId 그룹 ID
     * @return 삭제 결과
     */
    @Transactional
    public GroupDataDeleteResponse deleteGroupData(Long groupId) {
        log.info("그룹 데이터 삭제 시작: groupId={}", groupId);

        // 데이터 존재 여부 확인
        boolean hasSchedules = scheduleRepository.existsByGroupId(groupId);
        boolean hasTodos = todoRepository.existsByGroupId(groupId);
        boolean hasCategories = categoryRepository.existsByGroupId(groupId);

        if (!hasSchedules && !hasTodos && !hasCategories) {
            log.info("그룹 데이터 없음: groupId={}", groupId);
            return GroupDataDeleteResponse.noData(groupId);
        }

        // 삭제 전 카운트
        long scheduleCount = scheduleRepository.findByGroupId(groupId).size();
        long todoCount = todoRepository.findByGroupId(groupId).size();
        long categoryCount = categoryRepository.findByGroupId(groupId).size();

        // 1. 할일 삭제 (일정 참조가 있을 수 있음)
        if (hasTodos) {
            todoRepository.deleteByGroupId(groupId);
            log.debug("그룹 할일 삭제 완료: groupId={}, count={}", groupId, todoCount);
        }

        // 2. 일정 삭제
        if (hasSchedules) {
            scheduleRepository.deleteByGroupId(groupId);
            log.debug("그룹 일정 삭제 완료: groupId={}, count={}", groupId, scheduleCount);
        }

        // 3. 카테고리 삭제
        if (hasCategories) {
            categoryRepository.deleteByGroupId(groupId);
            log.debug("그룹 카테고리 삭제 완료: groupId={}, count={}", groupId, categoryCount);
        }

        log.info("그룹 데이터 삭제 완료: groupId={}, schedules={}, todos={}, categories={}",
                groupId, scheduleCount, todoCount, categoryCount);

        return GroupDataDeleteResponse.success(groupId, scheduleCount, todoCount, categoryCount);
    }
}
