package com.unisync.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API Gateway 라우팅 및 경로 재작성 테스트
 * MockWebServer를 사용하여 백엔드 서비스를 모킹하고 Gateway의 동작을 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingTest {

    @Autowired
    private WebTestClient webClient;

    private static MockWebServer userService;
    private static MockWebServer courseService;
    private static MockWebServer syncService;
    private static MockWebServer scheduleService;
    private static MockWebServer socialService;

    /**
     * MockWebServer를 실제 서비스 포트에 바인딩
     */
    @BeforeEach
    void setUp() throws IOException {
        userService = new MockWebServer();
        userService.start(8081);

        courseService = new MockWebServer();
        courseService.start(8082);

        syncService = new MockWebServer();
        syncService.start(8083);

        scheduleService = new MockWebServer();
        scheduleService.start(8084);

        socialService = new MockWebServer();
        socialService.start(8085);
    }

    /**
     * 테스트 후 MockWebServer 종료
     */
    @AfterEach
    void tearDown() throws IOException {
        if (userService != null) userService.shutdown();
        if (courseService != null) courseService.shutdown();
        if (syncService != null) syncService.shutdown();
        if (scheduleService != null) scheduleService.shutdown();
        if (socialService != null) socialService.shutdown();
    }

    /**
     * Spring 속성을 동적으로 설정 (테스트 환경에서 JWT 검증 비활성화)
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // JWT 필터를 우회하기 위해 모든 경로를 제외 경로로 설정
        registry.add("jwt.exclude-paths[0]", () -> "/**");
    }

    // ==================== User Service 테스트 ====================

    @Test
    @DisplayName("User Service: /api/v1/auth/signup → /auth/signup 경로 재작성")
    void testAuthSignupPathRewrite() throws InterruptedException {
        // Given: Mock 응답 설정
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"User created\"}")
                .addHeader("Content-Type", "application/json"));

        // When: Gateway로 요청
        webClient.post()
                .uri("/api/v1/auth/signup")
                .bodyValue("{\"email\":\"test@test.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User created");

        // Then: 재작성된 경로 검증
        RecordedRequest request = userService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/auth/signup");
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
        assertThat(request.getPath()).isEqualTo("/auth/signin");
    }

    @Test
    @DisplayName("User Service: /api/v1/users/123 → /users/123 경로 재작성")
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
        assertThat(request.getPath()).isEqualTo("/users/123");
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
        assertThat(request.getPath()).isEqualTo("/users/profile");
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
        assertThat(request.getPath()).isEqualTo("/courses");
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
        assertThat(request.getPath()).isEqualTo("/courses/456");
    }

    // ==================== Sync Service 테스트 ====================

    @Test
    @DisplayName("Sync Service: /api/v1/sync/status → /sync/status 경로 재작성")
    void testSyncPathRewrite() throws InterruptedException {
        // Given
        syncService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"synced\"}"));

        // When
        webClient.get()
                .uri("/api/v1/sync/status")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = syncService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/sync/status");
    }

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
        assertThat(request.getPath()).isEqualTo("/schedules");
    }

    // ==================== Social Service 테스트 ====================

    @Test
    @DisplayName("Social Service: /api/v1/social/feed → /social/feed 경로 재작성")
    void testSocialPathRewrite() throws InterruptedException {
        // Given
        socialService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        // When
        webClient.get()
                .uri("/api/v1/social/feed")
                .exchange()
                .expectStatus().isOk();

        // Then
        RecordedRequest request = socialService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/social/feed");
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
        assertThat(request.getPath()).isEqualTo("/users?page=1&size=10");
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
        assertThat(request.getPath()).isEqualTo("/users/123/profile");
    }
}