package com.unisync.course.sync.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.course.assignment.dto.AssignmentToScheduleEventDto;
import com.unisync.course.assignment.publisher.AssignmentEventPublisher;
import com.unisync.course.assignment.service.AssignmentService;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.sync.dto.CanvasSyncMessage;
import com.unisync.course.sync.dto.CanvasSyncMessage.CourseData;
import com.unisync.course.sync.dto.CanvasSyncMessage.AssignmentData;
import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Canvas Sync Listener
 * Lambda가 발행한 통합 동기화 메시지를 처리
 * (단일 메시지에 모든 courses + assignments 포함)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasSyncListener {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentService assignmentService;
    private final AssignmentEventPublisher assignmentEventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * lambda-to-courseservice-sync 큐에서 통합 동기화 메시지 수신
     * 단일 Course (CANVAS_COURSE_SYNCED) 또는 다중 Courses (CANVAS_SYNC_COMPLETED) 지원
     *
     * @param messageBody JSON 형식의 CanvasSyncMessage
     */
    @SqsListener(value = "lambda-to-courseservice-sync")
    @Transactional
    public void receiveCanvasSync(String messageBody) {
        log.info("📥 Received Canvas sync message");

        try {
            CanvasSyncMessage syncMessage = objectMapper.readValue(messageBody, CanvasSyncMessage.class);
            String cognitoSub = syncMessage.getCognitoSub();
            int totalAssignments = 0;
            int totalCourses = 0;

            // 단일 Course 형식 지원 (CANVAS_COURSE_SYNCED)
            if (syncMessage.getCourse() != null) {
                CourseData courseData = syncMessage.getCourse();
                Course course = processCourse(courseData);
                
                // Assignment를 먼저 처리 (DB에 저장)
                if (courseData.getAssignments() != null) {
                    for (AssignmentData assignmentData : courseData.getAssignments()) {
                        processAssignment(course, assignmentData);
                        totalAssignments++;
                    }
                }
                
                // Enrollment 생성 후 Schedule 이벤트 발행 (이제 Assignment가 DB에 있음)
                processEnrollment(cognitoSub, course, courseData);
                
                totalCourses = 1;
                log.info("   ✅ Processed course: id={}, name={}, assignments={}",
                        course.getId(), course.getName(), 
                        courseData.getAssignments() != null ? courseData.getAssignments().size() : 0);
            }
            
            // 배열 형식 지원 (CANVAS_SYNC_COMPLETED)
            if (syncMessage.getCourses() != null && !syncMessage.getCourses().isEmpty()) {
                for (CourseData courseData : syncMessage.getCourses()) {
                    Course course = processCourse(courseData);

                    // Assignment를 먼저 처리 (DB에 저장)
                    if (courseData.getAssignments() != null) {
                        for (AssignmentData assignmentData : courseData.getAssignments()) {
                            processAssignment(course, assignmentData);
                            totalAssignments++;
                        }
                    }
                    
                    // Enrollment 생성 후 Schedule 이벤트 발행 (이제 Assignment가 DB에 있음)
                    processEnrollment(cognitoSub, course, courseData);
                    
                    totalCourses++;
                    log.info("   ✅ Processed course: id={}, name={}, assignments={}",
                            course.getId(), course.getName(),
                            courseData.getAssignments() != null ? courseData.getAssignments().size() : 0);
                }
            }

            log.info("✅ Successfully processed Canvas sync: {} courses, {} assignments",
                    totalCourses, totalAssignments);

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse Canvas sync message: {}", messageBody, e);
            throw new RuntimeException("Failed to parse Canvas sync message", e);
        } catch (Exception e) {
            log.error("❌ Failed to process Canvas sync message", e);
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
        // 'Z' suffix 및 밀리초 제거: "2021-11-23T15:00:00Z" -> "2021-11-23T15:00:00"
        String normalized = dateTimeStr.replace("Z", "").split("\\.")[0];
        return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Enrollment 생성 (중복 체크)
     * 새 Enrollment일 경우, 해당 Course의 기존 Assignment에 대해 Schedule 이벤트 발행
     */
    private void processEnrollment(String cognitoSub, Course course, CourseData courseData) {
        if (!enrollmentRepository.existsByCognitoSubAndCourseId(cognitoSub, course.getId())) {
            // 첫 등록자가 Leader (Course가 새로 생성된 경우)
            boolean isNewCourse = courseRepository.findByCanvasCourseId(courseData.getCanvasCourseId())
                    .map(c -> c.getId().equals(course.getId()))
                    .orElse(false);

            Enrollment enrollment = Enrollment.builder()
                    .cognitoSub(cognitoSub)
                    .course(course)
                    .isSyncLeader(isNewCourse)
                    .build();

            enrollmentRepository.save(enrollment);
            log.info("📝 Created enrollment: cognitoSub={}, courseId={}", cognitoSub, course.getId());

            // 새 Enrollment일 경우, 해당 Course의 기존 Assignment에 대해 Schedule 이벤트 발행
            publishExistingAssignmentsToSchedule(course, cognitoSub);
        }
    }

    /**
     * 새 사용자에게 기존 Assignment들에 대한 Schedule 이벤트 발행
     */
    private void publishExistingAssignmentsToSchedule(Course course, String cognitoSub) {
        List<Assignment> existingAssignments = assignmentRepository.findAllByCourseId(course.getId());

        if (existingAssignments.isEmpty()) {
            log.debug("No existing assignments for course: courseId={}", course.getId());
            return;
        }

        List<AssignmentToScheduleEventDto> events = existingAssignments.stream()
                .map(assignment -> AssignmentToScheduleEventDto.builder()
                        .eventType("ASSIGNMENT_CREATED")
                        .assignmentId(assignment.getId())
                        .cognitoSub(cognitoSub)
                        .canvasAssignmentId(assignment.getCanvasAssignmentId())
                        .canvasCourseId(course.getCanvasCourseId())
                        .title(assignment.getTitle())
                        .description(assignment.getDescription())
                        .dueAt(assignment.getDueAt())
                        .pointsPossible(assignment.getPointsPossible())
                        .courseId(course.getId())
                        .courseName(course.getName())
                        .build())
                .toList();

        assignmentEventPublisher.publishAssignmentEvents(events);

        log.info("📤 Published {} schedule events for existing assignments to user: cognitoSub={}, courseId={}",
                events.size(), cognitoSub, course.getId());
    }

    /**
     * Assignment 생성/업데이트
     */
    private void processAssignment(Course course, AssignmentData assignmentData) {
        // AssignmentService에 전달하기 위해 기존 DTO 형식으로 변환
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
