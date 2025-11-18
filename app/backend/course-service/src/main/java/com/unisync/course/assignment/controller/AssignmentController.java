package com.unisync.course.assignment.controller;

import com.unisync.course.assignment.dto.AssignmentResponse;
import com.unisync.course.assignment.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Assignment REST API Controller
 */
@RestController
@RequestMapping("/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    /**
     * Canvas Assignment ID로 Assignment 조회
     */
    @GetMapping("/canvas/{canvasAssignmentId}")
    public ResponseEntity<AssignmentResponse> getAssignmentByCanvasId(
            @PathVariable Long canvasAssignmentId
    ) {
        return assignmentService.findByCanvasAssignmentId(canvasAssignmentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}