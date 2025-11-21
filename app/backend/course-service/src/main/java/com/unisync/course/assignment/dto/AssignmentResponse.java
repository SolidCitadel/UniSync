package com.unisync.course.assignment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "과제 정보 응답")
public class AssignmentResponse {

    @Schema(description = "과제 ID", example = "1")
    private Long id;

    @Schema(description = "Canvas 과제 ID", example = "67890")
    private Long canvasAssignmentId;

    @Schema(description = "과목 ID", example = "1")
    private Long courseId;

    @Schema(description = "과제 제목", example = "중간고사 프로젝트")
    private String title;

    @Schema(description = "과제 설명", example = "데이터베이스 설계 및 구현")
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "제출 마감일시", example = "2025-04-15T23:59:59")
    private LocalDateTime dueAt;

    @Schema(description = "배점", example = "100")
    private Integer pointsPossible;

    @Schema(description = "제출 유형 (online_upload, online_text_entry 등)", example = "online_upload")
    private String submissionTypes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "생성 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "수정 일시", example = "2025-01-15T10:30:00")
    private LocalDateTime updatedAt;
}