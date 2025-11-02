package com.unisync.course.assignment.service;

import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.common.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Assignment Service
 * Canvas-Sync-Lambda에서 발행한 Assignment 이벤트를 처리하여 DB 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;

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
            .orElseThrow(() -> new IllegalArgumentException(
                "Course not found: canvasCourseId=" + message.getCanvasCourseId()));

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
    }

    /**
     * Assignment 업데이트
     * @param message SQS에서 수신한 Assignment 업데이트 메시지
     */
    @Transactional
    public void updateAssignment(AssignmentEventMessage message) {
        // Canvas Assignment ID로 기존 레코드 조회
        Assignment assignment = assignmentRepository.findByCanvasAssignmentId(message.getCanvasAssignmentId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Assignment not found: canvasAssignmentId=" + message.getCanvasAssignmentId()));

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
}