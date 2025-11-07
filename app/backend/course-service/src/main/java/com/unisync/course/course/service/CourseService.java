package com.unisync.course.course.service;

import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.course.dto.CourseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Course 조회 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;

    /**
     * 사용자가 수강 중인 Course 목록 조회
     * @param cognitoSub Cognito 사용자 ID
     * @return Course 목록
     */
    @Transactional(readOnly = true)
    public List<CourseResponse> getUserCourses(String cognitoSub) {
        List<Enrollment> enrollments = enrollmentRepository.findAllByCognitoSub(cognitoSub);

        return enrollments.stream()
                .map(enrollment -> CourseResponse.from(enrollment.getCourse()))
                .collect(Collectors.toList());
    }

    /**
     * 특정 Course 조회
     * @param courseId Course ID
     * @return Course
     */
    @Transactional(readOnly = true)
    public Optional<CourseResponse> getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .map(CourseResponse::from);
    }

    /**
     * 특정 Course의 Assignment 목록 조회
     * @param courseId Course ID
     * @return Assignment 목록
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getCourseAssignments(Long courseId) {
        List<Assignment> assignments = assignmentRepository.findAllByCourseId(courseId);

        return assignments.stream()
                .map(this::toAssignmentResponse)
                .collect(Collectors.toList());
    }

    /**
     * Assignment Entity를 Response DTO로 변환
     */
    private AssignmentResponse toAssignmentResponse(Assignment assignment) {
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