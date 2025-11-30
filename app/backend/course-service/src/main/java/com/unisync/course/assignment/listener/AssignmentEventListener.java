package com.unisync.course.assignment.listener;

import com.unisync.course.assignment.service.AssignmentService;
import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Assignment 이벤트를 수신하여 서비스로 위임.
 * (테스트에서 직접 메서드를 호출하는 구조)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentEventListener {

    private final AssignmentService assignmentService;

    public void receiveAssignmentEvent(AssignmentEventMessage message) {
        if (message == null || !StringUtils.hasText(message.getEventType())) {
            log.warn("Ignored assignment event: eventType is null/empty");
            return;
        }

        String eventType = message.getEventType();
        switch (eventType) {
            case "ASSIGNMENT_CREATED" -> assignmentService.createAssignment(message);
            case "ASSIGNMENT_UPDATED" -> assignmentService.updateAssignment(message);
            case "ASSIGNMENT_DELETED" -> log.info("ASSIGNMENT_DELETED not supported in Phase 1. Ignored.");
            default -> log.warn("Unknown assignment event type: {}", eventType);
        }
    }
}
