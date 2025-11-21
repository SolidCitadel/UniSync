package com.unisync.user.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.sync.dto.CanvasSyncResponse;
import com.unisync.user.sync.exception.CanvasSyncException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.InvocationType;

/**
 * Canvas 수동 동기화 서비스
 * Phase 1: Spring에서 Lambda 직접 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasSyncService {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.lambda.canvas-sync-function-name}")
    private String canvasSyncFunctionName;

    /**
     * Canvas 수동 동기화 시작
     *
     * @param cognitoSub Cognito 사용자 ID
     * @return 동기화 결과 통계
     */
    public CanvasSyncResponse syncCanvas(String cognitoSub) {
        log.info("Starting Canvas sync for cognitoSub: {}", cognitoSub);

        try {
            // 1. Lambda 페이로드 생성
            CanvasSyncRequest request = new CanvasSyncRequest(cognitoSub);
            String payload = objectMapper.writeValueAsString(request);

            log.debug("Lambda payload: {}", payload);

            // 2. Lambda 동기 호출
            InvokeRequest invokeRequest = InvokeRequest.builder()
                    .functionName(canvasSyncFunctionName)
                    .invocationType(InvocationType.REQUEST_RESPONSE) // 동기 호출
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build();

            InvokeResponse invokeResponse = lambdaClient.invoke(invokeRequest);

            // 3. Lambda 실행 에러 확인 (예외 발생 시)
            String responsePayload = invokeResponse.payload().asUtf8String();
            log.debug("Lambda response: {}", responsePayload);

            if (invokeResponse.functionError() != null) {
                log.error("Lambda execution error: {}", responsePayload);
                throw new CanvasSyncException(
                        "Lambda execution failed: " + responsePayload
                );
            }

            // 4. 응답 파싱
            LambdaResponse lambdaResponse = objectMapper.readValue(
                    responsePayload,
                    LambdaResponse.class
            );

            // 5. Lambda 응답 상태 확인
            if (lambdaResponse.getStatusCode() == null || lambdaResponse.getStatusCode() != 200) {
                throw new CanvasSyncException(
                        "Lambda returned non-200 status: " + lambdaResponse.getStatusCode()
                );
            }

            // 5. 결과 반환
            CanvasSyncResponse syncResponse = CanvasSyncResponse.builder()
                    .success(true)
                    .message("Canvas sync started")
                    .coursesCount(lambdaResponse.getBody().getCoursesCount())
                    .assignmentsCount(lambdaResponse.getBody().getAssignmentsCount())
                    .syncedAt(lambdaResponse.getBody().getSyncedAt())
                    .build();

            log.info("Canvas sync completed: {} courses, {} assignments",
                    syncResponse.getCoursesCount(),
                    syncResponse.getAssignmentsCount());

            return syncResponse;

        } catch (CanvasSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to invoke Canvas sync Lambda", e);
            throw new CanvasSyncException("Canvas sync failed: " + e.getMessage(), e);
        }
    }

    // 내부 DTO: Lambda 요청
    private record CanvasSyncRequest(String cognitoSub) {}

    // 내부 DTO: Lambda 응답
    @Data
    private static class LambdaResponse {
        private Integer statusCode;
        private LambdaResponseBody body;

        @Data
        static class LambdaResponseBody {
            private Integer coursesCount;
            private Integer assignmentsCount;
            private String syncedAt;
        }
    }
}
