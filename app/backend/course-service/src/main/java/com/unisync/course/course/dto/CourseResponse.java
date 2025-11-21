package com.unisync.course.course.dto;

import com.unisync.course.common.entity.Course;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "과목 정보 응답")
public class CourseResponse {

    @Schema(description = "과목 ID", example = "1")
    private Long id;

    @Schema(description = "Canvas 과목 ID", example = "12345")
    private Long canvasCourseId;

    @Schema(description = "과목명", example = "데이터베이스")
    private String name;

    @Schema(description = "과목 코드", example = "CS101")
    private String courseCode;

    @Schema(description = "과목 설명", example = "데이터베이스 설계 및 SQL 기초")
    private String description;

    @Schema(description = "과목 시작일시", example = "2025-03-01T00:00:00")
    private LocalDateTime startAt;

    @Schema(description = "과목 종료일시", example = "2025-06-30T23:59:59")
    private LocalDateTime endAt;

    @Schema(description = "생성 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2025-01-15T10:30:00")
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