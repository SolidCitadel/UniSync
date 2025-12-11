package com.unisync.course.course.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.shared.dto.sqs.CourseEnrollmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Optional;

/**
 * CourseEnrollment 이벤트 리스너 (테스트에서 직접 호출)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEnrollmentListener {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ObjectMapper objectMapper;

    public void receiveCourseEnrollment(String messageBody) {
        try {
            CourseEnrollmentEvent event = objectMapper.readValue(messageBody, CourseEnrollmentEvent.class);
            validateEvent(event);
            handleEvent(event);
        } catch (IOException e) {
            log.error("Failed to parse course-enrollment event: {}", messageBody, e);
            throw new RuntimeException("Failed to parse course-enrollment event", e);
        }
    }

    private void validateEvent(CourseEnrollmentEvent event) {
        if (event.getCanvasCourseId() == null || !StringUtils.hasText(event.getCognitoSub())) {
            throw new IllegalArgumentException("Invalid course-enrollment event");
        }
    }

    private void handleEvent(CourseEnrollmentEvent event) {
        CourseUpsertResult upsertResult = upsertCourse(event);
        Course course = upsertResult.course();

        // Enrollment 생성 (중복 체크)
        boolean exists = enrollmentRepository.existsByCognitoSubAndCourseId(
                event.getCognitoSub(), course.getId()
        );
        if (exists) {
            return;
        }

        // 기존 Course에 추가되는 Enrollment는 리더가 아니다.
        boolean isSyncLeader = !upsertResult.existedBefore();
        Enrollment enrollment = Enrollment.builder()
                .cognitoSub(event.getCognitoSub())
                .course(course)
                .isSyncLeader(isSyncLeader)
                .build();
        enrollmentRepository.save(enrollment);
    }

    private CourseUpsertResult upsertCourse(CourseEnrollmentEvent event) {
        Optional<Course> existing = courseRepository.findByCanvasCourseId(event.getCanvasCourseId());
        if (existing.isPresent()) {
            return new CourseUpsertResult(existing.get(), true);
        }

        Course course = Course.builder()
                .canvasCourseId(event.getCanvasCourseId())
                .name(event.getCourseName())
                .courseCode(event.getCourseCode())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .build();

        Course saved = courseRepository.save(course);
        return new CourseUpsertResult(saved, false);
    }

    private record CourseUpsertResult(Course course, boolean existedBefore) {}
}
