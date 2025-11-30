package com.unisync.user.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.sync.dto.CanvasSyncResponse;
import com.unisync.user.sync.exception.CanvasSyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.LambdaException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CanvasSyncService 단위 테스트")
class CanvasSyncServiceTest {

    @Mock
    private LambdaClient lambdaClient;

    private CanvasSyncService canvasSyncService;
    private ObjectMapper objectMapper;
    private String cognitoSub;
    private String canvasSyncFunctionName;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper(); // 실제 ObjectMapper 사용
        canvasSyncService = new CanvasSyncService(lambdaClient, objectMapper);

        cognitoSub = "test-cognito-sub-123";
        canvasSyncFunctionName = "canvas-sync-lambda";

        // @Value 필드 주입
        ReflectionTestUtils.setField(canvasSyncService, "canvasSyncFunctionName", canvasSyncFunctionName);
    }

    @Test
    @DisplayName("Canvas 동기화 성공 - Lambda 호출 및 응답 파싱")
    void syncCanvas_Success() throws Exception {
        // Given
        String lambdaResponsePayload = """
            {
                "statusCode": 200,
                "body": {
                    "coursesCount": 5,
                    "assignmentsCount": 12,
                    "syncedAt": "2025-01-22T10:30:00"
                }
            }
            """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(lambdaResponsePayload))
                .build();

        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(invokeResponse);

        // When
        CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub, "assignments");

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Canvas sync started");
        assertThat(response.getCoursesCount()).isEqualTo(5);
        assertThat(response.getAssignmentsCount()).isEqualTo(12);
        assertThat(response.getSyncedAt()).isEqualTo("2025-01-22T10:30:00");

        // Lambda 호출 검증
        ArgumentCaptor<InvokeRequest> requestCaptor = ArgumentCaptor.forClass(InvokeRequest.class);
        then(lambdaClient).should().invoke(requestCaptor.capture());

        InvokeRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.functionName()).isEqualTo(canvasSyncFunctionName);
        assertThat(capturedRequest.invocationType()).isEqualTo(InvocationType.REQUEST_RESPONSE);

        // 페이로드에 cognitoSub 포함 확인
        String payload = capturedRequest.payload().asUtf8String();
        assertThat(payload).contains(cognitoSub);
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - Lambda non-200 status code")
    void syncCanvas_Failure_NonOkStatus() {
        // Given
        String lambdaResponsePayload = """
            {
                "statusCode": 500,
                "body": {
                    "coursesCount": 0,
                    "assignmentsCount": 0,
                    "syncedAt": ""
                }
            }
            """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200) // Lambda 자체는 성공했지만 비즈니스 로직 실패
                .payload(SdkBytes.fromUtf8String(lambdaResponsePayload))
                .build();

        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(invokeResponse);

        // When & Then
        assertThatThrownBy(() -> canvasSyncService.syncCanvas(cognitoSub, "assignments"))
                .isInstanceOf(CanvasSyncException.class)
                .hasMessageContaining("Lambda returned non-200 status: 500");
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - Lambda 호출 예외")
    void syncCanvas_Failure_LambdaException() {
        // Given
        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willThrow(LambdaException.builder()
                        .message("Lambda function not found")
                        .build());

        // When & Then
        assertThatThrownBy(() -> canvasSyncService.syncCanvas(cognitoSub, "assignments"))
                .isInstanceOf(CanvasSyncException.class)
                .hasMessageContaining("Canvas sync failed");
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - 잘못된 Lambda 응답 형식")
    void syncCanvas_Failure_InvalidResponseFormat() {
        // Given
        String invalidLambdaResponse = """
            {
                "invalid": "response"
            }
            """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(invalidLambdaResponse))
                .build();

        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(invokeResponse);

        // When & Then
        assertThatThrownBy(() -> canvasSyncService.syncCanvas(cognitoSub, "assignments"))
                .isInstanceOf(CanvasSyncException.class)
                .hasMessageContaining("Canvas sync failed");
    }

    @Test
    @DisplayName("Canvas 동기화 성공 - 과목 0개, 과제 0개")
    void syncCanvas_Success_ZeroCounts() throws Exception {
        // Given
        String lambdaResponsePayload = """
            {
                "statusCode": 200,
                "body": {
                    "coursesCount": 0,
                    "assignmentsCount": 0,
                    "syncedAt": "2025-01-22T10:30:00"
                }
            }
            """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(lambdaResponsePayload))
                .build();

        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(invokeResponse);

        // When
        CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub, "assignments");

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCoursesCount()).isZero();
        assertThat(response.getAssignmentsCount()).isZero();
    }

    @Test
    @DisplayName("Canvas 동기화 - Lambda 페이로드 형식 검증")
    void syncCanvas_VerifyPayloadFormat() throws Exception {
        // Given
        String lambdaResponsePayload = """
            {
                "statusCode": 200,
                "body": {
                    "coursesCount": 1,
                    "assignmentsCount": 1,
                    "syncedAt": "2025-01-22T10:30:00"
                }
            }
            """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(lambdaResponsePayload))
                .build();

        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(invokeResponse);

        // When
        canvasSyncService.syncCanvas(cognitoSub, "assignments");

        // Then
        ArgumentCaptor<InvokeRequest> requestCaptor = ArgumentCaptor.forClass(InvokeRequest.class);
        then(lambdaClient).should().invoke(requestCaptor.capture());

        String payload = requestCaptor.getValue().payload().asUtf8String();

        // JSON 형식 확인
        assertThat(payload).contains("\"cognitoSub\"");
        assertThat(payload).contains("\"" + cognitoSub + "\"");
        assertThat(payload).contains("\"syncMode\"").contains("\"assignments\"");

        // 파싱 가능한지 확인
        assertThatNoException().isThrownBy(() -> objectMapper.readTree(payload));
    }
}
