package com.unisync.course.assignment.service;

import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.assignment.dto.AssignmentToScheduleEventDto;
import com.unisync.course.assignment.exception.AssignmentNotFoundException;
import com.unisync.course.assignment.publisher.AssignmentEventPublisher;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.course.exception.CourseNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Assignment Service
 * Canvas-Sync-Lambda에서 발행한 Assignment 이벤트를 처리하여 DB 저장
 * 저장 후 Schedule-Service로 변환 이벤트 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentEventPublisher assignmentEventPublisher;

    /**
     * Assignment 생성
     * @param message SQS에서 수신한 Assignment 이벤트 메시지
     */
    @Transactional
    public void createAssignment(AssignmentEventMessage message) {
        // 1. 중복 체크 (canvas_assignment_id는 UNIQUE)
        if (assignmentRepository.existsByCanvasAssignmentId(message.getCanvasAssignmentId())) {
            log.warn("Assignment already exists: canvasAssignmentId={}", message.getCanvasAssignmentId());
            return;
        }

        // 2. Course 조회 (Canvas Course ID로)
        Course course = courseRepository.findByCanvasCourseId(message.getCanvasCourseId())
            .orElseThrow(() -> new CourseNotFoundException(
                "과목을 찾을 수 없습니다: canvasCourseId=" + message.getCanvasCourseId()));

        // 3. Assignment 생성
        Assignment assignment = Assignment.builder()
            .canvasAssignmentId(message.getCanvasAssignmentId())
            .course(course)
            .title(message.getTitle())
            .description(message.getDescription())
            .dueAt(message.getDueAt())
            .pointsPossible(message.getPointsPossible())
            .submissionTypes(message.getSubmissionTypes())
            .build();

        Assignment saved = assignmentRepository.save(assignment);

        log.info("✅ Created assignment: id={}, canvasAssignmentId={}, title={}",
                 saved.getId(), saved.getCanvasAssignmentId(), saved.getTitle());

        // 4. Schedule-Service로 변환 이벤트 발행 (모든 수강생에게)
        publishAssignmentToScheduleEvents(saved, "ASSIGNMENT_CREATED");
    }

    /**
     * Assignment 업데이트
     * @param message SQS에서 수신한 Assignment 업데이트 메시지
     */
    @Transactional
    public void updateAssignment(AssignmentEventMessage message) {
        // Canvas Assignment ID로 기존 레코드 조회
        Assignment assignment = assignmentRepository.findByCanvasAssignmentId(message.getCanvasAssignmentId())
            .orElseThrow(() -> new AssignmentNotFoundException(
                "과제를 찾을 수 없습니다: canvasAssignmentId=" + message.getCanvasAssignmentId()));

        // 필드 업데이트
        assignment.updateFromCanvas(
            message.getTitle(),
            message.getDescription(),
            message.getDueAt(),
            message.getPointsPossible(),
            message.getSubmissionTypes()
        );

        Assignment saved = assignmentRepository.save(assignment);

        log.info("✅ Updated assignment: id={}, canvasAssignmentId={}",
                 saved.getId(), saved.getCanvasAssignmentId());

        // Schedule-Service로 변환 이벤트 발행 (모든 수강생에게)
        publishAssignmentToScheduleEvents(saved, "ASSIGNMENT_UPDATED");
    }

    /**
     * Canvas Assignment ID로 Assignment 조회
     * @param canvasAssignmentId Canvas Assignment ID
     * @return AssignmentResponse (Optional)
     */
    @Transactional(readOnly = true)
    public Optional<AssignmentResponse> findByCanvasAssignmentId(Long canvasAssignmentId) {
        return assignmentRepository.findByCanvasAssignmentId(canvasAssignmentId)
                .map(this::toResponse);
    }

    /**
     * Entity를 Response DTO로 변환
     */
    private AssignmentResponse toResponse(Assignment assignment) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .canvasAssignmentId(assignment.getCanvasAssignmentId())
                .courseId(assignment.getCourse().getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .dueAt(assignment.getDueAt())
                .pointsPossible(assignment.getPointsPossible())
                .submissionTypes(assignment.getSubmissionTypes())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }

    /**
     * Schedule-Service로 Assignment → Schedule 변환 이벤트 발행
     * 해당 과목을 수강하는 모든 사용자에게 이벤트 발행
     */
    private void publishAssignmentToScheduleEvents(Assignment assignment, String eventType) {
        Course course = assignment.getCourse();

        // 1. 과목을 수강하는 모든 사용자 조회
        List<Enrollment> enrollments = enrollmentRepository.findAllByCourseId(course.getId());

        if (enrollments.isEmpty()) {
            log.warn("No enrollments found for course: courseId={}", course.getId());
            return;
        }

        // 2. 각 수강생별로 이벤트 DTO 생성
        List<AssignmentToScheduleEventDto> events = enrollments.stream()
            .map(enrollment -> AssignmentToScheduleEventDto.builder()
                .eventType(eventType)
                .assignmentId(assignment.getId())
                .cognitoSub(enrollment.getCognitoSub())
                .canvasAssignmentId(assignment.getCanvasAssignmentId())
                .canvasCourseId(course.getCanvasCourseId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .dueAt(assignment.getDueAt())
                .pointsPossible(assignment.getPointsPossible())
                .courseId(course.getId())
                .courseName(course.getName())
                .build())
            .collect(Collectors.toList());

        // 3. SQS로 발행
        assignmentEventPublisher.publishAssignmentEvents(events);

        log.info("📤 Published {} assignment events to {} users: assignmentId={}, eventType={}",
                events.size(), enrollments.size(), assignment.getId(), eventType);
    }
}