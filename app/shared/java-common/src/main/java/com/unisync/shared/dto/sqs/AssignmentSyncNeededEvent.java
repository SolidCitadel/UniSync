package com.unisync.shared.dto.sqs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 새 Course가 등록되었을 때 Assignment 동기화가 필요함을 알리는 이벤트
 *
 * Queue: assignment-sync-needed-queue
 * Publisher: Course-Service (CourseEnrollmentListener)
 * Consumer: Canvas-Sync-Lambda (assignment_sync_handler)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentSyncNeededEvent {

    /**
     * UniSync DB의 Course ID
     */
    private Long courseId;

    /**
     * Canvas Course ID
     */
    private Long canvasCourseId;

    /**
     * Leader 사용자 ID (이 사용자의 토큰으로 Canvas API 호출)
     */
    private Long leaderUserId;
}