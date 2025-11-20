package com.unisync.course.assignment.listener;

import com.unisync.course.assignment.service.AssignmentService;
import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Assignment Event Listener
 * Canvas-Sync-Lambda가 발행한 assignment 이벤트를 SQS에서 수신하여 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentEventListener {

    private final AssignmentService assignmentService;

    /**
     * lambda-to-courseservice-assignments 큐에서 메시지를 수신하여 Assignment 생성/수정
     *
     * @param message Canvas-Sync-Lambda가 발행한 Assignment 이벤트 메시지
     * @SqsListener 설정:
     *   - value: 큐 이름 (LocalStack에서는 큐 이름, AWS에서는 큐 URL 또는 이름)
     *   - deletionPolicy: 메시지 삭제 정책 (ON_SUCCESS: 정상 처리 후 자동 삭제)
     */
    @SqsListener(value = "lambda-to-courseservice-assignments")
    public void receiveAssignmentEvent(AssignmentEventMessage message) {
        log.info("📥 Received assignment event: type={}, canvasAssignmentId={}, title={}",
                 message.getEventType(), message.getCanvasAssignmentId(), message.getTitle());

        try {
            if ("ASSIGNMENT_CREATED".equals(message.getEventType())) {
                assignmentService.createAssignment(message);
            } else if ("ASSIGNMENT_UPDATED".equals(message.getEventType())) {
                assignmentService.updateAssignment(message);
            } else {
                log.warn("⚠️ Unknown event type: {}", message.getEventType());
            }

            log.info("✅ Successfully processed assignment event: canvasAssignmentId={}",
                     message.getCanvasAssignmentId());
        } catch (Exception e) {
            log.error("❌ Failed to process assignment event: canvasAssignmentId={}",
                      message.getCanvasAssignmentId(), e);
            // 예외 발생 시 메시지가 큐로 돌아가고, maxReceiveCount 초과 시 DLQ로 이동
            throw e;
        }
    }
}