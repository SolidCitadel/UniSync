package com.unisync.course.assignment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Assignment 조회 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentResponse {
    private Long id;
    private Long canvasAssignmentId;
    private Long courseId;
    private String title;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueAt;

    private Integer pointsPossible;
    private String submissionTypes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}