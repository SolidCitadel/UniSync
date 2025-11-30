package com.unisync.course.sync.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.assignment.dto.UserAssignmentsBatchEvent;
import com.unisync.course.assignment.dto.UserAssignmentsBatchEvent.AssignmentPayload;
import com.unisync.course.assignment.publisher.AssignmentEventPublisher;
import com.unisync.course.assignment.service.AssignmentService;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.AssignmentProjection;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final AssignmentEventPublisher assignmentEventPublisher;
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
            String syncMode = syncMessage.getSyncMode() != null ? syncMessage.getSyncMode() : "assignments";
            String eventType = syncMessage.getEventType();
            int totalAssignments = 0;

            // 각 Course 처리
            for (CourseData courseData : syncMessage.getCourses()) {
                // 1. Course 생성/업데이트
                Course course = processCourse(courseData);

                // 2. Enrollment 생성
                processEnrollment(cognitoSub, course, courseData);

                // assignments 카운트만 누적 (실제 저장/발행은 후속 배치 처리)
                if (!"courses".equals(syncMode) && !"CANVAS_COURSES_SYNCED".equals(eventType)) {
                    totalAssignments += courseData.getAssignments().size();
                }

                log.info("   Processed course: id={}, name={}, assignments={}",
                        course.getId(), course.getName(), courseData.getAssignments().size());
            }

            // assignments 모드일 때 사용자별 배치 이벤트 생성/발행
            if (!"courses".equals(syncMode) && !"CANVAS_COURSES_SYNCED".equals(eventType)) {
                publishUserAssignmentBatches(syncMessage);
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
     * assignments 모드에서 사용자별 배치 메시지 발행
     */
    private void publishUserAssignmentBatches(CanvasSyncMessage syncMessage) {
        String cognitoSub = syncMessage.getCognitoSub();

        // 1) 과목/과제 데이터를 저장 (AssignmentEventMessage를 통해 AssignmentService에 위임)
        // 저장하면서 Assignment ID를 가져올 수 있도록 AssignmentService가 CANVAS assignment -> DB 저장 수행
        // AssignmentService는 현재 SQS 이벤트 기반이므로, 여기서는 assignmentService.createAssignment(...) 호출
        // 대신 assignment 데이터를 일괄 저장하는 헬퍼가 필요하지만, 간소화를 위해 기존 createAssignment를 재사용

        List<Course> courses = syncMessage.getCourses().stream()
                .map(this::processCourse) // 이미 처리했지만 안전 차원에서 매핑
                .collect(Collectors.toList());

        // CanvasSyncMessage의 courseData는 Assignments를 포함하고 있으므로, DB에 저장한다
        syncMessage.getCourses().forEach(courseData -> {
            Course course = courseRepository.findByCanvasCourseId(courseData.getCanvasCourseId())
                    .orElse(null);
            if (course == null) {
                return;
            }
            courseData.getAssignments().forEach(assignmentData -> {
                // AssignmentService.createAssignment를 재사용 (이미 저장된 경우 중복 체크)
                assignmentService.createAssignment(AssignmentEventMessage.builder()
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
                        .build());
            });
        });

        // 2) 사용자별로 Assignment를 조회하여 배치 메시지 구성
        // 현재 Course-Service는 사용자별 assignments 조회 API가 없으므로, repository에서 직접 조회 (Batch 발행을 위한 내부 처리)
        // enabled enrollments 조회
        Map<String, List<Enrollment>> enrollmentsByUser = enrollmentRepository.findAllByIsSyncEnabledTrue()
                .stream()
                .collect(Collectors.groupingBy(Enrollment::getCognitoSub));

        List<UserAssignmentsBatchEvent> batchEvents = new ArrayList<>();

        for (Map.Entry<String, List<Enrollment>> entry : enrollmentsByUser.entrySet()) {
            String userSub = entry.getKey();
            List<Enrollment> enrollments = entry.getValue();

            // 해당 사용자의 과제 전체 조회 (enabled 과목만)
            List<AssignmentPayload> assignments = enrollmentRepository.findAssignmentsByCognitoSub(userSub)
                    .stream()
                    .map(a -> AssignmentPayload.builder()
                            .assignmentId(a.getAssignmentId())
                            .canvasAssignmentId(a.getCanvasAssignmentId())
                            .canvasCourseId(a.getCanvasCourseId())
                            .courseId(a.getCourseId())
                            .courseName(a.getCourseName())
                            .title(a.getTitle())
                            .description(a.getDescription())
                            .dueAt(a.getDueAt())
                            .pointsPossible(a.getPointsPossible())
                            .build())
                    .collect(Collectors.toList());

            if (assignments.isEmpty()) {
                continue;
            }

            batchEvents.add(UserAssignmentsBatchEvent.builder()
                    .eventType("USER_ASSIGNMENTS_CREATED")
                    .cognitoSub(userSub)
                    .syncedAt(syncMessage.getSyncedAt())
                    .assignments(assignments)
                    .build());
        }

        if (!batchEvents.isEmpty()) {
            assignmentEventPublisher.publishAssignmentBatchEvents(batchEvents);
            log.info("📤 Published {} batch events for assignments", batchEvents.size());
        } else {
            log.info("No assignments to publish for enabled users");
        }
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
                    .isSyncEnabled(true)
                    .build();

            enrollmentRepository.save(enrollment);
        }
    }

    // Assignment 저장은 AssignmentEventListener를 통해 진행되므로 여기서는 로직 제거
}
