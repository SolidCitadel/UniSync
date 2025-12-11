package com.unisync.schedule.course.service;

import com.unisync.schedule.categories.service.CategoryService;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.course.dto.CourseDisabledMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course 이벤트 처리 서비스
 * Course-Service에서 발행한 Course 이벤트를 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final ScheduleRepository scheduleRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Course 이벤트 처리
     * @param message SQS에서 수신한 Course 이벤트 메시지
     */
    @Transactional
    public void processCourseEvent(CourseDisabledMessage message) {
        String eventType = message.getEventType();

        log.info("Processing course event: eventType={}, courseId={}, cognitoSub={}",
                eventType, message.getCourseId(), message.getCognitoSub());

        switch (eventType) {
            case "COURSE_DISABLED":
                deleteCourseSchedules(message);
                break;
            default:
                log.warn("Unknown event type: {}", eventType);
        }
    }

    /**
     * 과목 비활성화 시 해당 과목의 모든 일정 삭제
     */
    private void deleteCourseSchedules(CourseDisabledMessage message) {
        String sourceType = "CANVAS_COURSE";
        String sourceId = message.getCourseId().toString();

        // 1. 과목에 해당하는 카테고리 조회
        Category category = categoryRepository.findByCognitoSubAndSourceTypeAndSourceId(
                        message.getCognitoSub(), sourceType, sourceId)
                .orElse(null);

        if (category == null) {
            log.info("No category found for course: courseId={}, cognitoSub={}",
                    message.getCourseId(), message.getCognitoSub());
            return;
        }

        // 2. 해당 카테고리의 모든 일정 삭제
        int deletedCount = scheduleRepository.findByCategoryIdAndCognitoSub(
                        category.getCategoryId(), message.getCognitoSub())
                .size();

        scheduleRepository.deleteAllByCognitoSubAndCategoryId(
                message.getCognitoSub(), category.getCategoryId());

        log.info("✅ Deleted {} schedules for disabled course: courseId={}, categoryId={}, cognitoSub={}",
                deletedCount, message.getCourseId(), category.getCategoryId(), message.getCognitoSub());
    }
}
