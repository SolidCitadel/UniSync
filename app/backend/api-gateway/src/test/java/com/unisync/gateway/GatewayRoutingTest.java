package com.unisync.gateway;

import com.unisync.gateway.service.CognitoJwtVerifier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * API Gateway 라우팅 테스트
 * - MockWebServer로 백엔드 서비스 모킹 (고정 포트: 8081, 8082, 8083)
 * - 라우팅 및 경로 재작성만 검증
 * - JWT 검증은 Mock으로 처리 (인증 통합 테스트는 GatewayIntegrationTest에서)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
class GatewayRoutingTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private CognitoJwtVerifier cognitoJwtVerifier;

    private static MockWebServer userService;
    private static MockWebServer courseService;
    private static MockWebServer scheduleService;

    /**
     * MockWebServer를 고정 포트로 시작
     * - application-local.yml의 서비스 URL(localhost:8081, 8082, 8083)에 맞춤
     */
    @BeforeAll
    static void startMockServers() throws IOException {
        userService = new MockWebServer();
        userService.start(8081);

        courseService = new MockWebServer();
        courseService.start(8082);

        scheduleService = new MockWebServer();
        scheduleService.start(8083);
    }

    /**
     * Dummy Cognito 설정만 주입 (Mock 사용하므로 실제 값 불필요)
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.cognito.user-pool-id", () -> "test-pool-id");
        registry.add("aws.cognito.region", () -> "ap-northeast-2");
        registry.add("aws.cognito.endpoint", () -> "http://localhost:4566");
    }

    /**
     * 각 테스트 전에 MockWebServer 큐 정리
     * (이전 테스트의 요청이 남아있지 않도록)
     */
    @BeforeEach
    void setUp() throws InterruptedException {
        // MockWebServer 큐에서 남은 요청 제거 (타임아웃 10ms)
        while (userService.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // 큐가 비어있으면 null 반환
        }
        while (courseService.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // 큐가 비어있으면 null 반환
        }
        while (scheduleService.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // 큐가 비어있으면 null 반환
        }

        // CognitoJwtVerifier Mock 동작 정의 (모든 JWT 토큰 허용)
        when(cognitoJwtVerifier.isValid(anyString())).thenReturn(true);
        when(cognitoJwtVerifier.verify(anyString())).thenReturn(Map.of(
                "sub", "test-user-id",
                "email", "test@test.com",
                "name", "Test User"
        ));
        when(cognitoJwtVerifier.extractUserId(any())).thenReturn("test-user-id");
        when(cognitoJwtVerifier.extractEmail(any())).thenReturn("test@test.com");
        when(cognitoJwtVerifier.extractName(any())).thenReturn("Test User");

        // WebTestClient에 기본 Authorization 헤더 설정 (더미 JWT 토큰)
        webClient = webClient.mutate()
                .defaultHeader("Authorization", "Bearer dummy-jwt-token")
                .build();
    }

    /**
     * 모든 테스트 종료 후 MockWebServer 종료
     */
    @AfterAll
    static void tearDown() throws IOException {
        if (userService != null) userService.shutdown();
        if (courseService != null) courseService.shutdown();
        if (scheduleService != null) scheduleService.shutdown();
    }

    // ==================== User Service 테스트 ====================

    @Test
    @DisplayName("User Service: /api/v1/auth/signup → /auth/signup 경로 재작성")
    void testAuthSignupPathRewrite() throws InterruptedException {
        // Given: Mock 응답 설정 (2번 요청하므로 2번 enqueue)
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"User created\"}")
                .addHeader("Content-Type", "application/json"));
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"User created\"}")
                .addHeader("Content-Type", "application/json"));

        // When: Gateway로 요청
        webClient.post()
                .uri("/api/v1/auth/signup")
                .bodyValue("{\"email\":\"test@test.com\",\"password\":\"password\"}")
                .exchange()
                .expectBody(String.class)
                .consumeWith(response -> {
                    System.out.println("=== Test Response ===");
                    System.out.println("Status: " + response.getStatus());
                    System.out.println("Body: " + response.getResponseBody());
                    System.out.println("====================");
                })
                .returnResult();

        // 다시 요청해서 검증
        webClient.post()
                .uri("/api/v1/auth/signup")
                .bodyValue("{\"email\":\"test@test.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User created");

        // Then: 재작성된 경로 검증 (/api만 제거, /v1은 유지)
        RecordedRequest request = userService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/auth/signup");
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("User Service: /api/v1/auth/signin → /auth/signin 경로 재작성")
    void testAuthSigninPathRewrite() throws InterruptedException {
        // Given
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"accessToken\":\"token123\"}"));

        // When
        webClient.post()
                .uri("/api/v1/auth/signin")
                .bodyValue("{\"email\":\"test@test.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = userService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/auth/signin");
    }

    @Test
    @DisplayName("User Service: /api/v1/users/123 → /v1/users/123 경로 재작성")
    void testUsersPathRewrite() throws InterruptedException {
        // Given
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":123,\"name\":\"John\"}"));

        // When
        webClient.get()
                .uri("/api/v1/users/123")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = userService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/users/123");
    }

    @Test
    @DisplayName("User Service: JWT 헤더 전달 확인")
    void testAuthHeaderPropagation() throws InterruptedException {
        // Given
        userService.enqueue(new MockResponse().setResponseCode(200));

        // When: Authorization 헤더와 함께 요청
        webClient.get()
                .uri("/api/v1/users/profile")
                .header("Authorization", "Bearer test-jwt-token")
                .exchange()
                .expectStatus().isOk();

        // Then: 헤더가 백엔드 서비스로 전달되었는지 확인
        RecordedRequest request = userService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/users/profile");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-jwt-token");
    }

    // ==================== Course Service 테스트 ====================

    @Test
    @DisplayName("Course Service: /api/v1/courses → /courses 경로 재작성")
    void testCoursesPathRewrite() throws InterruptedException {
        // Given
        courseService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        // When
        webClient.get()
                .uri("/api/v1/courses")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = courseService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/courses");
    }

    @Test
    @DisplayName("Course Service: /api/v1/courses/456 → /courses/456 경로 재작성")
    void testCourseDetailPathRewrite() throws InterruptedException {
        // Given
        courseService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":456,\"name\":\"Math 101\"}"));

        // When
        webClient.get()
                .uri("/api/v1/courses/456")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = courseService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/courses/456");
    }

    // Note: /api/v1/sync/** 경로는 제거됨 (user-service의 /api/v1/integrations/canvas/sync로 통합)

    // ==================== Schedule Service 테스트 ====================

    @Test
    @DisplayName("Schedule Service: /api/v1/schedules → /schedules 경로 재작성")
    void testSchedulesPathRewrite() throws InterruptedException {
        // Given
        scheduleService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        // When
        webClient.get()
                .uri("/api/v1/schedules")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = scheduleService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/schedules");
    }

    // ==================== Todos/Categories 경로 테스트 ====================

    @Test
    @DisplayName("Schedule Service: /api/v1/todos → /todos 경로 재작성")
    void testTodosPathRewrite() throws InterruptedException {
        // Given
        scheduleService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        // When
        webClient.get()
                .uri("/api/v1/todos")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = scheduleService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/todos");
    }

    @Test
    @DisplayName("Schedule Service: /api/v1/categories → /categories 경로 재작성")
    void testCategoriesPathRewrite() throws InterruptedException {
        // Given
        scheduleService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        // When
        webClient.get()
                .uri("/api/v1/categories")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = scheduleService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/categories");
    }

    // ==================== 404 테스트 ====================

    @Test
    @DisplayName("존재하지 않는 경로는 404 반환")
    void testNotFoundRoute() {
        webClient.get()
                .uri("/api/v1/invalid/path")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("/api/v1 없이 요청하면 404 반환")
    void testWithoutApiV1Prefix() {
        webClient.get()
                .uri("/users")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ==================== Query Parameter 테스트 ====================

    @Test
    @DisplayName("Query Parameter가 포함된 경로 재작성")
    void testPathRewriteWithQueryParams() throws InterruptedException {
        // Given
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        // When: Query Parameter와 함께 요청
        webClient.get()
                .uri("/api/v1/users?page=1&size=10")
                .exchange()
                .expectStatus().isOk();

        // Then: 경로는 재작성되고 Query Parameter는 유지
        RecordedRequest request = userService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/users?page=1&size=10");
    }

    // ==================== 복잡한 경로 테스트 ====================

    @Test
    @DisplayName("중첩된 경로 재작성: /api/v1/users/123/profile → /users/123/profile")
    void testNestedPathRewrite() throws InterruptedException {
        // Given
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"profile\":\"data\"}"));

        // When
        webClient.get()
                .uri("/api/v1/users/123/profile")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = userService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/users/123/profile");
    }
}