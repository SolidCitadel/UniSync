package com.unisync.course.course.dto;

import com.unisync.course.common.entity.Course;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Course 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {

    private Long id;
    private Long canvasCourseId;
    private String name;
    private String courseCode;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity를 Response DTO로 변환
     */
    public static CourseResponse from(Course course) {
        return CourseResponse.builder()
                .id(course.getId())
                .canvasCourseId(course.getCanvasCourseId())
                .name(course.getName())
                .courseCode(course.getCourseCode())
                .description(course.getDescription())
                .startAt(course.getStartAt())
                .endAt(course.getEndAt())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}