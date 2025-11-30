package com.unisync.course.sync.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.assignment.service.AssignmentService;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.sync.dto.CanvasSyncMessage;
import com.unisync.course.sync.dto.CanvasSyncMessage.AssignmentData;
import com.unisync.course.sync.dto.CanvasSyncMessage.CourseData;
import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Canvas Sync Listener
 * lambda-to-courseservice-sync 메시지를 수신하여 Course/Enrollment/Assignment를 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasSyncListener {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentService assignmentService;
    private final ObjectMapper objectMapper;

    /**
     * lambda-to-courseservice-sync 큐에서 통합 동기화 메시지 수신
     *
     * @param messageBody JSON 형식의 CanvasSyncMessage
     */
    @SqsListener(value = "lambda-to-courseservice-sync")
    @Transactional
    public void receiveCanvasSync(String messageBody) {
        log.info("Received Canvas sync message");

        try {
            CanvasSyncMessage syncMessage = objectMapper.readValue(messageBody, CanvasSyncMessage.class);

            log.info("   - cognitoSub={}, courses={}, syncedAt={}, syncMode={}",
                    syncMessage.getCognitoSub(),
                    syncMessage.getCourses().size(),
                    syncMessage.getSyncedAt(),
                    syncMessage.getSyncMode());

            String cognitoSub = syncMessage.getCognitoSub();
            String syncMode = syncMessage.getSyncMode() != null ? syncMessage.getSyncMode() : "full";
            String eventType = syncMessage.getEventType();
            int totalAssignments = 0;

            // 각 Course 처리
            for (CourseData courseData : syncMessage.getCourses()) {
                // 1. Course 생성/업데이트
                Course course = processCourse(courseData);

                // 2. Enrollment 생성
                processEnrollment(cognitoSub, course, courseData);

                // 3. Assignments 처리 (courses_only 모드면 건너뜀)
                if (!"courses_only".equals(syncMode) && !"CANVAS_COURSES_SYNCED".equals(eventType)) {
                    for (AssignmentData assignmentData : courseData.getAssignments()) {
                        processAssignment(course, assignmentData);
                        totalAssignments++;
                    }
                }

                log.info("   Processed course: id={}, name={}, assignments={}",
                        course.getId(), course.getName(), courseData.getAssignments().size());
            }

            log.info("Successfully processed Canvas sync: {} courses, {} assignments (mode={})",
                    syncMessage.getCourses().size(), totalAssignments, syncMode);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Canvas sync message: {}", messageBody, e);
            throw new RuntimeException("Failed to parse Canvas sync message", e);
        } catch (Exception e) {
            log.error("Failed to process Canvas sync message", e);
            throw e;
        }
    }

    /**
     * Course 생성 또는 조회
     */
    private Course processCourse(CourseData courseData) {
        Optional<Course> existingCourse = courseRepository
                .findByCanvasCourseId(courseData.getCanvasCourseId());

        if (existingCourse.isEmpty()) {
            Course course = Course.builder()
                    .canvasCourseId(courseData.getCanvasCourseId())
                    .name(courseData.getCourseName())
                    .courseCode(courseData.getCourseCode())
                    .startAt(parseDateTime(courseData.getStartAt()))
                    .endAt(parseDateTime(courseData.getEndAt()))
                    .build();

            return courseRepository.save(course);
        } else {
            return existingCourse.get();
        }
    }

    /**
     * ISO 8601 문자열을 LocalDateTime으로 변환 (null-safe)
     * Canvas API는 'Z' suffix를 포함하므로 제거 후 파싱
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        String normalized = dateTimeStr.replace("Z", "").split("\\.")[0];
        return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Enrollment 생성 (중복 체크)
     */
    private void processEnrollment(String cognitoSub, Course course, CourseData courseData) {
        if (!enrollmentRepository.existsByCognitoSubAndCourseId(cognitoSub, course.getId())) {
            boolean isNewCourse = courseRepository.findByCanvasCourseId(courseData.getCanvasCourseId())
                    .map(c -> c.getId().equals(course.getId()))
                    .orElse(false);

            Enrollment enrollment = Enrollment.builder()
                    .cognitoSub(cognitoSub)
                    .course(course)
                    .isSyncLeader(isNewCourse)
                    .build();

            enrollmentRepository.save(enrollment);
        }
    }

    /**
     * Assignment 생성/업데이트
     */
    private void processAssignment(Course course, AssignmentData assignmentData) {
        AssignmentEventMessage eventMessage = AssignmentEventMessage.builder()
                .eventType("ASSIGNMENT_CREATED")
                .canvasCourseId(course.getCanvasCourseId())
                .canvasAssignmentId(assignmentData.getCanvasAssignmentId())
                .title(assignmentData.getTitle())
                .description(assignmentData.getDescription())
                .dueAt(parseDateTime(assignmentData.getDueAt()))
                .pointsPossible(assignmentData.getPointsPossible() != null
                        ? assignmentData.getPointsPossible().intValue()
                        : null)
                .submissionTypes(assignmentData.getSubmissionTypes())
                .createdAt(parseDateTime(assignmentData.getCreatedAt()))
                .updatedAt(parseDateTime(assignmentData.getUpdatedAt()))
                .build();

        assignmentService.createAssignment(eventMessage);
    }
}
