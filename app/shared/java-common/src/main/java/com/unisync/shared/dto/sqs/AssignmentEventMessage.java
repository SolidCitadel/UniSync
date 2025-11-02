package com.unisync.shared.dto.sqs;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Canvas-Sync-Lambda가 발행하는 Assignment 이벤트 메시지
 * SQS Queue: assignment-events-queue
 *
 * 공유 DTO: course-service, schedule-service, Canvas-Sync-Lambda가 사용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentEventMessage {

    /**
     * 이벤트 타입: ASSIGNMENT_CREATED, ASSIGNMENT_UPDATED
     */
    private String eventType;

    /**
     * Canvas Assignment ID (Canvas API에서 제공)
     */
    private Long canvasAssignmentId;

    /**
     * Canvas Course ID (Canvas API에서 제공)
     */
    private Long canvasCourseId;

    /**
     * 과제 제목
     */
    private String title;

    /**
     * 과제 설명
     */
    private String description;

    /**
     * 마감일
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueAt;

    /**
     * 배점
     */
    private Integer pointsPossible;

    /**
     * 제출 유형 (예: "online_upload,online_text_entry")
     */
    private String submissionTypes;

    /**
     * Canvas에서 생성된 시간
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Canvas에서 마지막 수정된 시간
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}