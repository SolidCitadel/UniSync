package com.unisync.course.course.controller;

import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.course.dto.CourseResponse;
import com.unisync.course.course.service.CourseService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Course REST API Controller
 */
@RestController
@RequestMapping("/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * 사용자가 수강 중인 Course 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<CourseResponse>> getUserCourses(
            @Parameter(hidden = true) @RequestHeader(value = "X-Cognito-Sub") String cognitoSub
    ) {
        List<CourseResponse> courses = courseService.getUserCourses(cognitoSub);
        return ResponseEntity.ok(courses);
    }

    /**
     * 특정 Course 조회
     */
    @GetMapping("/{courseId}")
    public ResponseEntity<CourseResponse> getCourse(
            @PathVariable Long courseId
    ) {
        return courseService.getCourse(courseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 특정 Course의 Assignment 목록 조회
     */
    @GetMapping("/{courseId}/assignments")
    public ResponseEntity<List<AssignmentResponse>> getCourseAssignments(
            @PathVariable Long courseId
    ) {
        List<AssignmentResponse> assignments = courseService.getCourseAssignments(courseId);
        return ResponseEntity.ok(assignments);
    }
}