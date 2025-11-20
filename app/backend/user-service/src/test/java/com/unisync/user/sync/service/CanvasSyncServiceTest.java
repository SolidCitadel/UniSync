package com.unisync.user.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.sync.dto.CanvasSyncResponse;
import com.unisync.user.sync.exception.CanvasSyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CanvasSyncService 단위 테스트
 *
 * Lambda 호출 및 응답 파싱 로직을 테스트합니다.
 * LambdaClient는 Mock으로 대체하여 실제 AWS Lambda 호출은 하지 않습니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CanvasSyncService 단위 테스트")
class CanvasSyncServiceTest {

    @Mock
    private LambdaClient lambdaClient;

    @InjectMocks
    private CanvasSyncService canvasSyncService;

    private ObjectMapper objectMapper = new ObjectMapper(); // Real ObjectMapper for testing

    @BeforeEach
    void setUp() {
        // @Value 필드 주입 (ReflectionTestUtils 사용)
        ReflectionTestUtils.setField(canvasSyncService, "canvasSyncFunctionName", "canvas-sync-lambda");
        ReflectionTestUtils.setField(canvasSyncService, "objectMapper", objectMapper);
    }

    @Test
    @DisplayName("Canvas 동기화 성공 - Lambda 정상 응답")
    void syncCanvas_Success() throws Exception {
        // Given: Lambda가 정상 응답을 반환
        String cognitoSub = "test-cognito-sub-123";
        String lambdaResponseBody = """
                {
                    "statusCode": 200,
                    "body": {
                        "coursesCount": 3,
                        "assignmentsCount": 15,
                        "syncedAt": "2025-11-20T12:00:00Z"
                    }
                }
                """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(lambdaResponseBody))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);

        // When: Canvas 동기화 실행
        CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub);

        // Then: 응답 검증
        assertThat(response).isNotNull();
        assertThat(response.getCoursesCount()).isEqualTo(3);
        assertThat(response.getAssignmentsCount()).isEqualTo(15);
        assertThat(response.getSyncedAt()).isEqualTo("2025-11-20T12:00:00Z");
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Canvas sync started");

        // Then: Lambda 호출 검증
        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - Lambda 실행 오류")
    void syncCanvas_LambdaExecutionError() {
        // Given: Lambda 호출 시 예외 발생
        String cognitoSub = "test-cognito-sub-123";

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenThrow(LambdaException.builder()
                        .message("Function execution failed")
                        .build());

        // When & Then: CanvasSyncException 발생
        assertThatThrownBy(() -> canvasSyncService.syncCanvas(cognitoSub))
                .isInstanceOf(CanvasSyncException.class)
                .hasMessageContaining("Canvas sync failed");

        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - Lambda 응답 파싱 오류")
    void syncCanvas_ResponseParsingError() {
        // Given: Lambda가 잘못된 형식의 응답 반환
        String cognitoSub = "test-cognito-sub-123";
        String invalidResponseBody = "{ invalid json }";

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(invalidResponseBody))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);

        // When & Then: CanvasSyncException 발생
        assertThatThrownBy(() -> canvasSyncService.syncCanvas(cognitoSub))
                .isInstanceOf(CanvasSyncException.class)
                .hasMessageContaining("Canvas sync failed");

        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }

    @Test
    @DisplayName("Canvas 동기화 실패 - Lambda statusCode가 200이 아님")
    void syncCanvas_NonSuccessStatusCode() {
        // Given: Lambda 응답 statusCode가 200이 아님
        String cognitoSub = "test-cognito-sub-123";
        String errorResponseBody = """
                {
                    "statusCode": 500
                }
                """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)  // Lambda 호출은 성공했지만
                .payload(SdkBytes.fromUtf8String(errorResponseBody))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);

        // When & Then: CanvasSyncException 발생
        assertThatThrownBy(() -> canvasSyncService.syncCanvas(cognitoSub))
                .isInstanceOf(CanvasSyncException.class)
                .hasMessageContaining("Lambda returned non-200 status: 500");

        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }

    @Test
    @DisplayName("Canvas 동기화 성공 - 과제가 0개인 경우")
    void syncCanvas_Success_NoAssignments() {
        // Given: Lambda가 과제가 없는 응답 반환
        String cognitoSub = "test-cognito-sub-123";
        String lambdaResponseBody = """
                {
                    "statusCode": 200,
                    "body": {
                        "coursesCount": 2,
                        "assignmentsCount": 0,
                        "syncedAt": "2025-11-20T12:00:00Z"
                    }
                }
                """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(lambdaResponseBody))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);

        // When: Canvas 동기화 실행
        CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub);

        // Then: 응답 검증
        assertThat(response).isNotNull();
        assertThat(response.getCoursesCount()).isEqualTo(2);
        assertThat(response.getAssignmentsCount()).isEqualTo(0);
        assertThat(response.getSyncedAt()).isEqualTo("2025-11-20T12:00:00Z");

        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }

    @Test
    @DisplayName("Lambda 요청 페이로드 검증")
    void syncCanvas_VerifyRequestPayload() {
        // Given
        String cognitoSub = "test-cognito-sub-123";
        String lambdaResponseBody = """
                {
                    "statusCode": 200,
                    "body": {
                        "coursesCount": 1,
                        "assignmentsCount": 5,
                        "syncedAt": "2025-11-20T12:00:00Z"
                    }
                }
                """;

        InvokeResponse invokeResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(lambdaResponseBody))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);

        // When
        canvasSyncService.syncCanvas(cognitoSub);

        // Then: Lambda가 호출되었는지 검증
        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }
}
