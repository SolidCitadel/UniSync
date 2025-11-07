package com.unisync.course.course.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.shared.dto.sqs.AssignmentSyncNeededEvent;
import com.unisync.shared.dto.sqs.CourseEnrollmentEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Course Enrollment Listener
 * Canvas-Sync-Lambda가 발행한 Course Enrollment 이벤트를 SQS에서 수신하여 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEnrollmentListener {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    /**
     * course-enrollment-queue에서 메시지를 수신하여 Course 및 Enrollment 생성
     *
     * @param messageBody JSON 형식의 CourseEnrollmentEvent 메시지
     */
    @SqsListener(value = "course-enrollment-queue")
    public void receiveCourseEnrollment(String messageBody) {
        log.info("📥 Received course-enrollment event");

        try {
            CourseEnrollmentEvent event = objectMapper.readValue(messageBody, CourseEnrollmentEvent.class);

            log.info("   - cognitoSub={}, canvasCourseId={}, courseName={}",
                    event.getCognitoSub(), event.getCanvasCourseId(), event.getCourseName());

            // 1. Course가 DB에 있는지 확인
            Optional<Course> existingCourse = courseRepository
                    .findByCanvasCourseId(event.getCanvasCourseId());

            Course course;
            boolean isNewCourse = false;

            if (existingCourse.isEmpty()) {
                // 2-a. Course 없음 → 생성
                course = Course.builder()
                        .canvasCourseId(event.getCanvasCourseId())
                        .name(event.getCourseName())
                        .courseCode(event.getCourseCode())
                        .startAt(event.getStartAt())
                        .endAt(event.getEndAt())
                        .build();

                course = courseRepository.save(course);
                isNewCourse = true;

                log.info("   ✅ Created new course: id={}, canvasCourseId={}, name={}",
                        course.getId(), event.getCanvasCourseId(), event.getCourseName());
            } else {
                // 2-b. Course 있음 → 기존 사용
                course = existingCourse.get();
                log.info("   ℹ️ Course already exists: id={}, name={}", course.getId(), course.getName());
            }

            // 3. Enrollment 생성 (중복 체크)
            if (!enrollmentRepository.existsByCognitoSubAndCourseId(event.getCognitoSub(), course.getId())) {
                Enrollment enrollment = Enrollment.builder()
                        .cognitoSub(event.getCognitoSub())
                        .course(course)
                        .isSyncLeader(isNewCourse) // 첫 등록자가 Leader
                        .build();

                enrollmentRepository.save(enrollment);

                log.info("   ✅ Created enrollment: cognitoSub={}, courseId={}, leader={}",
                        event.getCognitoSub(), course.getId(), isNewCourse);
            } else {
                log.info("   ℹ️ Enrollment already exists: cognitoSub={}, courseId={}",
                        event.getCognitoSub(), course.getId());
            }

            // 4. 새 Course면 Assignment 동기화 필요
            if (isNewCourse) {
                AssignmentSyncNeededEvent syncEvent = AssignmentSyncNeededEvent.builder()
                        .courseId(course.getId())
                        .canvasCourseId(course.getCanvasCourseId())
                        .leaderCognitoSub(event.getCognitoSub())
                        .build();

                sqsTemplate.send("assignment-sync-needed-queue", syncEvent);

                log.info("   📤 Published assignment-sync-needed event for courseId={}", course.getId());
            }

            log.info("✅ Successfully processed course-enrollment event");

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse course-enrollment event: {}", messageBody, e);
            throw new RuntimeException("Failed to parse course-enrollment event", e);
        } catch (Exception e) {
            log.error("❌ Failed to process course-enrollment event", e);
            throw e;
        }
    }
}