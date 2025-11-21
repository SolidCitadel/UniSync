# API Documentation 설계

API Gateway에서 모든 마이크로서비스의 OpenAPI 문서를 통합하여 제공하는 Swagger Aggregation 설계입니다.

## 현재 상태

### 각 서비스별 OpenAPI 설정

| 서비스 | 포트 | Swagger UI | API Docs |
|--------|------|------------|----------|
| User Service | 8081 | http://localhost:8081/swagger-ui.html | /v3/api-docs |
| Course Service | 8082 | http://localhost:8082/swagger-ui.html | /v3/api-docs |
| Schedule Service | 8083 | http://localhost:8083/swagger-ui.html | /v3/api-docs |
| **API Gateway** | 8080 | ❌ 미구현 | ❌ 미구현 |

**문제점**:
- 개발자가 각 서비스별로 Swagger에 접근해야 함
- API Gateway의 URL Rewrite (`/api/v1/...` → `/v1/...`)가 문서에 반영되지 않음
- 실제 클라이언트가 호출하는 경로와 문서의 경로가 다름

## 목표

```
클라이언트 → http://localhost:8080/swagger-ui.html
            ↓
     드롭다운에서 서비스 선택
            ↓
     User / Course / Schedule Service API 문서 표시
     (경로는 /api/v1/... 형태로 Gateway 기준)
```

## 설계

### 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                     API Gateway (8080)                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Swagger UI Aggregation                      │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐                  │   │
│  │  │  User   │  │ Course  │  │Schedule │  ← 드롭다운 선택  │   │
│  │  │ Service │  │ Service │  │ Service │                  │   │
│  │  └────┬────┘  └────┬────┘  └────┬────┘                  │   │
│  └───────┼────────────┼───────────┼────────────────────────┘   │
│          │            │           │                             │
│  /v3/api-docs/user    │    /v3/api-docs/schedule               │
│          │    /v3/api-docs/course │                             │
└──────────┼────────────┼───────────┼─────────────────────────────┘
           │            │           │
           ▼            ▼           ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │  User    │  │  Course  │  │ Schedule │
    │ Service  │  │  Service │  │  Service │
    │  (8081)  │  │  (8082)  │  │  (8083)  │
    │          │  │          │  │          │
    │/v3/api-  │  │/v3/api-  │  │/v3/api-  │
    │  docs    │  │  docs    │  │  docs    │
    └──────────┘  └──────────┘  └──────────┘
```

### 구현 단계

#### Phase 1: API Gateway에 Swagger UI 추가

**1. 의존성 추가** (`api-gateway/build.gradle.kts`)

```kotlin
dependencies {
    // 기존 의존성...

    // Spring Cloud Gateway용 Springdoc (WebFlux 기반)
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.3.0")
}
```

> **주의**: API Gateway는 WebFlux 기반이므로 `webflux-ui` 사용 (서비스들은 `webmvc-ui`)

**2. Springdoc 설정** (`api-gateway/application.yml`)

```yaml
springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    urls:
      - name: User Service
        url: /v3/api-docs/user-service
      - name: Course Service
        url: /v3/api-docs/course-service
      - name: Schedule Service
        url: /v3/api-docs/schedule-service
    urls-primary-name: User Service  # 기본 선택
  api-docs:
    enabled: true
  webjars:
    prefix: /webjars
```

#### Phase 2: API Docs 라우팅 추가

**3. 라우팅 추가** (`GatewayRoutesConfig.java`)

```java
@Bean
public RouteLocator customRoutes(RouteLocatorBuilder builder, JwtAuthenticationFilter jwtAuthFilter) {
    return builder.routes()
            // ========== Swagger API Docs 라우팅 (JWT 인증 제외) ==========
            .route("user-service-api-docs", r -> r
                    .path("/v3/api-docs/user-service")
                    .filters(f -> f
                            .rewritePath("/v3/api-docs/user-service", "/v3/api-docs")
                    )
                    .uri(userServiceUrl)
            )
            .route("course-service-api-docs", r -> r
                    .path("/v3/api-docs/course-service")
                    .filters(f -> f
                            .rewritePath("/v3/api-docs/course-service", "/v3/api-docs")
                    )
                    .uri(courseServiceUrl)
            )
            .route("schedule-service-api-docs", r -> r
                    .path("/v3/api-docs/schedule-service")
                    .filters(f -> f
                            .rewritePath("/v3/api-docs/schedule-service", "/v3/api-docs")
                    )
                    .uri(scheduleServiceUrl)
            )

            // ========== 기존 라우팅 ==========
            // Block internal APIs
            .route("block-internal-apis", r -> r
                    // ...
            )
            // ... 나머지 라우팅
            .build();
}
```

**4. JWT 제외 경로 추가** (`api-gateway/application.yml`)

```yaml
jwt:
  exclude-paths:
    - /api/v1/auth/**      # 인증 API
    - /actuator/**         # 헬스체크
    - /swagger-ui/**       # Swagger UI 정적 리소스
    - /swagger-ui.html     # Swagger UI 메인
    - /v3/api-docs/**      # OpenAPI 스펙
    - /webjars/**          # Swagger UI 웹자르
```

#### Phase 3: 각 서비스의 OpenAPI 서버 URL 수정

각 서비스의 API 문서가 Gateway 기준 경로(`/api/v1/...`)를 표시하도록 설정합니다.

**5. OpenAPI 설정 클래스 추가** (각 서비스)

```java
// user-service/src/main/java/com/unisync/user/common/config/OpenApiConfig.java
package com.unisync.user.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${springdoc.server.url:/api}")
    private String serverUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .version("1.0.0")
                        .description("사용자, 인증, 자격 증명 관리 API"))
                .servers(List.of(
                        new Server()
                                .url(serverUrl)
                                .description("API Gateway")
                ));
    }
}
```

**6. 서비스별 application.yml 수정**

```yaml
# 각 서비스의 application.yml에 추가
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
  server:
    url: /api  # Gateway 기준 prefix
```

### URL 매핑 정리

| 요청 경로 (Gateway) | 변환 후 (Backend) | 용도 |
|---------------------|-------------------|------|
| `/swagger-ui.html` | - | Swagger UI 메인 |
| `/v3/api-docs/user-service` | `/v3/api-docs` (User) | User Service OpenAPI 스펙 |
| `/v3/api-docs/course-service` | `/v3/api-docs` (Course) | Course Service OpenAPI 스펙 |
| `/v3/api-docs/schedule-service` | `/v3/api-docs` (Schedule) | Schedule Service OpenAPI 스펙 |
| `/api/v1/users/**` | `/v1/users/**` (User) | 실제 API 호출 |

### 서비스별 OpenAPI 스펙에서의 경로 표시

OpenAPI 스펙의 `servers` 설정으로 경로가 Gateway 기준으로 표시됩니다:

```json
{
  "openapi": "3.0.1",
  "info": {
    "title": "User Service API",
    "version": "1.0.0"
  },
  "servers": [
    {
      "url": "/api",
      "description": "API Gateway"
    }
  ],
  "paths": {
    "/v1/users/me": {
      "get": { ... }
    }
  }
}
```

Swagger UI에서 "Try it out" 실행 시:
- 표시 경로: `GET /api/v1/users/me`
- 실제 호출: `http://localhost:8080/api/v1/users/me`
- Gateway가 `/api/v1/users/me` → `/v1/users/me`로 rewrite하여 User Service로 전달

## 구현 체크리스트

### API Gateway
- [x] `springdoc-openapi-starter-webflux-ui` 의존성 추가
- [x] `application.yml`에 springdoc 설정 추가
- [x] `GatewayRoutesConfig.java`에 API docs 라우팅 추가
- [x] JWT 제외 경로에 Swagger 관련 경로 추가

### User Service
- [x] `OpenApiConfig.java` 추가
- [x] `application.yml`에 `springdoc.server.url` 추가

### Course Service
- [x] `OpenApiConfig.java` 추가
- [x] `application.yml`에 `springdoc.server.url` 추가

### Schedule Service
- [x] `OpenApiConfig.java` 추가
- [x] `application.yml`에 `springdoc.server.url` 추가

## 테스트 방법

### 1. 개별 서비스 Swagger 확인

```bash
# 각 서비스 직접 접근 (기존 방식)
curl http://localhost:8081/v3/api-docs  # User Service
curl http://localhost:8082/v3/api-docs  # Course Service
curl http://localhost:8083/v3/api-docs  # Schedule Service
```

### 2. Gateway 통합 Swagger 확인

```bash
# Gateway를 통한 API docs 프록시
curl http://localhost:8080/v3/api-docs/user-service
curl http://localhost:8080/v3/api-docs/course-service
curl http://localhost:8080/v3/api-docs/schedule-service

# Swagger UI 접속
open http://localhost:8080/swagger-ui.html
```

### 3. API 호출 테스트

Swagger UI에서 "Try it out" 클릭:
1. 인증이 필요한 API: 401 Unauthorized (정상)
2. 인증 제외 API (`/api/v1/auth/**`): 정상 동작

## 고려사항

### 1. 환경별 서버 URL

```yaml
# application-local.yml
springdoc:
  server:
    url: /api

# application-prod.yml
springdoc:
  server:
    url: https://api.unisync.com/api
```

### 2. API 버전 관리

현재 `/v1/`을 사용 중. 향후 버전 추가 시:
- `/v2/` 경로 추가
- OpenAPI 스펙에서 버전별 그룹핑 가능

### 3. 인증 토큰 입력

Swagger UI에서 JWT 토큰 입력:
```yaml
springdoc:
  swagger-ui:
    oauth:
      client-id: ${COGNITO_CLIENT_ID}
    # 또는 Bearer 토큰 직접 입력
```

## 참고 문서

- [Springdoc OpenAPI](https://springdoc.org/)
- [Spring Cloud Gateway + Springdoc](https://springdoc.org/#spring-cloud-gateway-support)
- [시스템 아키텍처](./system-architecture.md)
- [API Gateway 라우팅](../../app/backend/CLAUDE.md#api-gateway-라우팅)