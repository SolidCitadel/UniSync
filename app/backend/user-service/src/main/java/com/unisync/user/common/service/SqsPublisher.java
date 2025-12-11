package com.unisync.user.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * SQS 메시지 발행 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqsPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    /**
     * SQS 큐에 메시지를 발행합니다
     *
     * @param queueName 큐 이름 (예: "user-token-registered-queue")
     * @param message   발행할 메시지 객체 (DTO)
     */
    public void publish(String queueName, Object message) {
        try {
            // 1. 큐 URL 가져오기
            String queueUrl = getQueueUrl(queueName);

            // 2. 메시지를 JSON으로 변환
            String messageBody = objectMapper.writeValueAsString(message);

            // 3. SQS로 메시지 전송
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(sendMsgRequest);

            log.info("✅ SQS message published successfully");
            log.info("   Queue: {}", queueName);
            log.info("   MessageId: {}", response.messageId());
            log.info("   Body: {}", messageBody);

        } catch (Exception e) {
            log.error("❌ Failed to publish SQS message to queue: {}", queueName, e);
            throw new RuntimeException("Failed to publish SQS message", e);
        }
    }

    /**
     * 큐 이름으로부터 큐 URL을 가져옵니다
     * queueName이 전체 URL인 경우 그대로 반환
     */
    private String getQueueUrl(String queueName) {
        // queueName이 이미 전체 URL인 경우 그대로 사용
        if (queueName.startsWith("https://")) {
            log.debug("Queue URL (direct): {}", queueName);
            return queueName;
        }
        
        try {
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            String queueUrl = sqsClient.getQueueUrl(getQueueUrlRequest).queueUrl();
            log.debug("Queue URL for {}: {}", queueName, queueUrl);
            return queueUrl;

        } catch (Exception e) {
            log.error("Failed to get queue URL for: {}", queueName, e);
            throw new RuntimeException("Failed to get queue URL: " + queueName, e);
        }
    }
}
