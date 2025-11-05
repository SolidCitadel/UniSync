package com.unisync.shared.dto.sqs;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Canvas에서 가져온 Course 정보를 Course-Service에 전달하는 SQS 이벤트
 *
 * Queue: course-enrollment-queue
 * Publisher: Canvas-Sync-Lambda (initial_sync_handler)
 * Consumer: Course-Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollmentEvent {

    /**
     * 사용자 ID
     */
    private Long userId;

    /**
     * Canvas Course ID
     */
    private Long canvasCourseId;

    /**
     * Course 이름
     */
    private String courseName;

    /**
     * Course 코드 (예: "CSE101")
     */
    private String courseCode;

    /**
     * Canvas Course 상태 (예: "available", "completed")
     */
    private String workflowState;

    /**
     * 시작일
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSS]'Z'")
    private LocalDateTime startAt;

    /**
     * 종료일
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSS]'Z'")
    private LocalDateTime endAt;

    /**
     * 이벤트 발행 시간
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSS]")
    private LocalDateTime publishedAt;
}