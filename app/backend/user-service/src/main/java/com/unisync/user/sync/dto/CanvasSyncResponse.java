package com.unisync.user.sync.dto;

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
public class CanvasSyncResponse {

    /**
     * 동기화 성공 여부
     */
    private Boolean success;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 동기화된 Course 개수
     */
    private Integer coursesCount;

    /**
     * 동기화된 Assignment 개수
     */
    private Integer assignmentsCount;

    /**
     * 동기화 시작 시간 (ISO 8601)
     */
    private String syncedAt;
}
