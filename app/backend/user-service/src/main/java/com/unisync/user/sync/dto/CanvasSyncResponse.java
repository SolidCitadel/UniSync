package com.unisync.user.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Canvas 수동 동기화 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Canvas 수동 동기화 응답")
public class CanvasSyncResponse {

    /**
     * 동기화 성공 여부
     */
    @Schema(description = "동기화 성공 여부", example = "true")
    private Boolean success;

    /**
     * 응답 메시지
     */
    @Schema(description = "응답 메시지", example = "Canvas 동기화가 성공적으로 시작되었습니다")
    private String message;

    /**
     * 동기화된 Course 개수
     */
    @Schema(description = "동기화된 과목 개수", example = "5")
    private Integer coursesCount;

    /**
     * 동기화된 Assignment 개수
     */
    @Schema(description = "동기화된 과제 개수", example = "12")
    private Integer assignmentsCount;

    /**
     * 동기화 시작 시간 (ISO 8601)
     */
    @Schema(description = "동기화 시작 시간 (ISO 8601)", example = "2025-01-15T10:30:00Z")
    private String syncedAt;
}
