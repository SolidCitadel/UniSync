package com.unisync.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.io.IOException;
import java.util.Base64;

/**
 * API Gateway 통합 테스트
 * - Testcontainers로 LocalStack 실행 (Cognito 포함)
 * - 실제 JWT 토큰 발급 및 검증
 * - 인증 플로우 전체 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webClient;

    private static MockWebServer userService;
    private static String testUserPoolId;
    private static String testClientId;
    private static CognitoIdentityProviderClient cognitoClient;
    private static boolean testUserCreated = false;

    // LocalStack Pro Container (Cognito IDP는 Pro 전용)
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack-pro:latest"))
            .withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"))
            .withEnv("SERVICES", "cognito-idp,sts");

    /**
     * Spring 속성을 동적으로 설정
     * - LocalStack Cognito User Pool 생성
     * - MockWebServer 시작
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) throws IOException {
        // MockWebServer 시작 (고정 포트)
        userService = new MockWebServer();
        userService.start(8081);

        // Cognito User Pool 생성
        cognitoClient = CognitoIdentityProviderClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();

        CreateUserPoolResponse userPool = cognitoClient.createUserPool(CreateUserPoolRequest.builder()
                .poolName("test-user-pool")
                .policies(UserPoolPolicyType.builder()
                        .passwordPolicy(PasswordPolicyType.builder()
                                .minimumLength(6)
                                .requireUppercase(false)
                                .requireLowercase(false)
                                .requireNumbers(false)
                                .requireSymbols(false)
                                .build())
                        .build())
                .build());
        testUserPoolId = userPool.userPool().id();

        CreateUserPoolClientResponse client = cognitoClient.createUserPoolClient(
                CreateUserPoolClientRequest.builder()
                        .userPoolId(testUserPoolId)
                        .clientName("test-client")
                        .explicitAuthFlows(ExplicitAuthFlowsType.ADMIN_NO_SRP_AUTH)
                        .build());
        testClientId = client.userPoolClient().clientId();

        // LocalStack Cognito 설정
        registry.add("aws.cognito.user-pool-id", () -> testUserPoolId);
        registry.add("aws.cognito.region", () -> localstack.getRegion());
        registry.add("aws.cognito.endpoint", () -> localstack.getEndpoint().toString());

        // MockWebServer URL (Course/Schedule Service는 테스트하지 않으므로 더미)
        registry.add("services.user-service.url", () -> "http://localhost:8081");
        registry.add("services.course-service.url", () -> "http://localhost:8082");
        registry.add("services.schedule-service.url", () -> "http://localhost:8083");
    }

    /**
     * 각 테스트 전에 실행 - 테스트 사용자가 생성되어 있는지 확인
     */
    @BeforeEach
    void setUp() {
        if (!testUserCreated) {
            createTestUser();
            testUserCreated = true;
        }
    }

    /**
     * 테스트용 사용자 생성 (각 테스트 클래스 시작 시 한 번만 실행)
     */
    private static void createTestUser() {
        if (cognitoClient == null || testUserPoolId == null) {
            throw new IllegalStateException("Cognito client not initialized");
        }

        try {
            // 테스트용 사용자 생성
            cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(testUserPoolId)
                    .username("testuser")
                    .temporaryPassword("TempPass123!")
                    .messageAction(MessageActionType.SUPPRESS)
                    .build());

            // 영구 비밀번호 설정
            cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                    .userPoolId(testUserPoolId)
                    .username("testuser")
                    .password("Password123!")
                    .permanent(true)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test user: " + e.getMessage(), e);
        }
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (userService != null) userService.shutdown();
    }

    // ==================== JWT 인증 테스트 ====================

    @Test
    @DisplayName("JWT 토큰 없이 보호된 경로 접근 시 401 반환")
    void testProtectedRouteWithoutToken() {
        // Given: MockWebServer 응답 설정 (실제로 도달하지 않음)
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":123}"));

        // When & Then: Authorization 헤더 없이 요청
        webClient.get()
                .uri("/api/v1/users/123")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("잘못된 JWT 토큰으로 접근 시 401 반환")
    void testProtectedRouteWithInvalidToken() {
        // Given
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":123}"));

        // When & Then: 잘못된 토큰으로 요청
        webClient.get()
                .uri("/api/v1/users/123")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 보호된 경로 접근 성공")
    void testProtectedRouteWithValidToken() {
        // Given: 실제 JWT 토큰 발급
        String idToken = authenticateAndGetToken("testuser", "Password123!");

        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":123,\"name\":\"Test User\"}"));

        // When & Then: 유효한 토큰으로 요청
        webClient.get()
                .uri("/api/v1/users/123")
                .header("Authorization", "Bearer " + idToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(123)
                .jsonPath("$.name").isEqualTo("Test User");
    }

    @Test
    @DisplayName("인증 제외 경로(/api/v1/auth/**)는 JWT 없이 접근 가능")
    void testAuthPathWithoutToken() {
        // Given
        userService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"Success\"}"));

        // When & Then: /api/v1/auth/** 경로는 JWT 없이 접근 가능
        webClient.post()
                .uri("/api/v1/auth/signup")
                .bodyValue("{\"email\":\"test@test.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk();
    }

    // ==================== Helper Methods ====================

    /**
     * Cognito에서 실제 JWT 토큰 발급
     */
    private String authenticateAndGetToken(String username, String password) {
        AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(
                AdminInitiateAuthRequest.builder()
                        .userPoolId(testUserPoolId)
                        .clientId(testClientId)
                        .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                        .authParameters(java.util.Map.of(
                                "USERNAME", username,
                                "PASSWORD", password
                        ))
                        .build());

        return authResponse.authenticationResult().idToken();
    }

    /**
     * JWT 토큰에서 Payload 디코딩 (검증용)
     */
    private String decodeJwtPayload(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token");
        }
        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
        return new String(decodedBytes);
    }
}
