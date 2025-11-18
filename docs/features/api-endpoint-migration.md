# API Endpoint Migration Plan: Internal API 분리

**작성일**: 2025-11-18
**완료일**: 2025-11-18
**상태**: ✅ **완료**
**목표**: 외부 API(`/v1/*`)와 내부 API(`/internal/v1/*`) 명확하게 분리

## 완료 요약

- ✅ Backend Services 업데이트 (16개 파일)
  - User-Service: 5개 컨트롤러 업데이트, 1개 내부 컨트롤러 추가, 1개 DTO 추가
  - Course-Service: 2개 컨트롤러 업데이트 (AssignmentController 버그 수정 포함)
  - Schedule-Service: 3개 컨트롤러 업데이트

- ✅ API Gateway 업데이트
  - URL rewrite 규칙 변경 (`/api` prefix만 제거)
  - `/api/internal/**` 차단 추가 (403 Forbidden)

- ✅ Lambda 업데이트
  - canvas-sync-lambda: 내부 API 경로 사용

- ✅ 테스트 업데이트 (10개 파일)
  - Java 통합 테스트: 5개
  - Python E2E 테스트: 1개

- ✅ 문서 업데이트
  - system-architecture.md: API 설계 섹션 전면 개편
  - app/backend/CLAUDE.md: API Gateway 라우팅 규칙 업데이트

---

## 1. 마이그레이션 개요

### 1.1 현재 상태

```
API Gateway: /api/v1/users → RewritePath → Service: /users (외부 API)
Lambda → Service: /credentials/canvas/by-cognito-sub/{cognitoSub} (내부 API, X-Api-Key 인증)
```

**문제점:**
- 외부 API와 내부 API가 같은 경로 공간 사용
- 내부 API가 명확히 구분되지 않음
- API Gateway를 통해 내부 API 접근 가능 (보안 취약)

### 1.2 목표 상태

```
API Gateway: /api/v1/users → RewritePath(/api만 제거) → Service: /v1/users (외부 API, JWT 인증)
Lambda → Service: /internal/v1/credentials/canvas/by-cognito-sub/{cognitoSub} (내부 API, X-Api-Key 인증)
API Gateway: /api/internal/** → 403 차단
```

**개선점:**
- ✅ URL만 봐도 외부/내부 구분 명확
- ✅ 네트워크 레벨 격리 가능 (Security Group)
- ✅ API Gateway 우회로 성능 향상
- ✅ 다른 인증 방식 (JWT vs API Key)
- ✅ 다른 DTO (필터링된 응답 vs 전체 데이터)

---

## 2. 영향 받는 컴포넌트

### 2.1 Backend Services

#### User-Service
**현재 Controller:**
- `UserController` - `/users` (외부)
- `AuthController` - `/auth` (외부, JWT 검증 제외 경로)
- `CredentialsController` - `/credentials` (외부 + 내부 혼재)
  - `/credentials/canvas` (POST, GET, DELETE) - 외부
  - `/credentials/canvas/by-cognito-sub/{cognitoSub}` (GET) - 내부
- `IntegrationStatusController` - `/integrations` (외부)

**변경 사항:**
1. 모든 외부 API를 `/v1/*` 경로로 변경
2. `CredentialsInternalController` 생성 → `/internal/v1/credentials/*`
3. SecurityConfig에서 `/v1/**` (JWT) vs `/internal/v1/**` (API Key) 분리

**새 구조:**
```
user/
├── controller/
│   ├── UserController.java                         (@RequestMapping("/v1/users"))
│   └── UserInternalController.java                 (@RequestMapping("/internal/v1/users")) - 필요시
credentials/
├── controller/
│   ├── CredentialsController.java                  (@RequestMapping("/v1/credentials"))
│   └── CredentialsInternalController.java          (@RequestMapping("/internal/v1/credentials"))
auth/
├── controller/
│   └── AuthController.java                         (@RequestMapping("/v1/auth"))
integration/
├── controller/
│   └── IntegrationStatusController.java            (@RequestMapping("/v1/integrations"))
```

#### Course-Service
**현재 Controller:**
- `CourseController` - `/courses` (외부)
- `AssignmentController` - `/api/assignments` ⚠️ 이미 `/api` 접두사 있음 (버그)

**변경 사항:**
1. `CourseController` → `/v1/courses`
2. `AssignmentController` → `/v1/assignments` (버그 수정)
3. 내부 API 필요 여부 검토 (현재는 없음)

**새 구조:**
```
course/
├── controller/
│   └── CourseController.java                       (@RequestMapping("/v1/courses"))
assignment/
├── controller/
│   └── AssignmentController.java                   (@RequestMapping("/v1/assignments"))
```

#### Schedule-Service
**현재 Controller:**
- `ScheduleController` - `/schedules` (외부)
- `TodoController` - `/todos` (외부)
- `CategoryController` - `/categories` (외부)

**변경 사항:**
1. 모든 Controller를 `/v1/*` 경로로 변경
2. 내부 API 필요 여부 검토 (현재는 없음)

**새 구조:**
```
schedules/
├── controller/
│   └── ScheduleController.java                     (@RequestMapping("/v1/schedules"))
todos/
├── controller/
│   └── TodoController.java                         (@RequestMapping("/v1/todos"))
categories/
├── controller/
│   └── CategoryController.java                     (@RequestMapping("/v1/categories"))
```

### 2.2 API Gateway

**파일:** `app/backend/api-gateway/src/main/java/com/unisync/gateway/config/GatewayRoutesConfig.java`

**현재:**
```java
.rewritePath("/api/v1/(?<segment>.*)", "/${segment}")
// /api/v1/users → /users로 전달
```

**변경 후:**
```java
.rewritePath("/api(?<segment>.*)", "$\\{segment}")
// /api/v1/users → /v1/users로 전달
```

**추가: /internal 경로 차단**
```java
.route("block-internal-apis", r -> r
    .path("/api/internal/**")
    .filters(f -> f.setStatus(HttpStatus.FORBIDDEN))
    .uri("no://op")
)
```

### 2.3 Serverless (Lambda)

**파일:** `app/serverless/canvas-sync-lambda/src/handler.py`

**현재 (line 257):**
```python
url = f"{USER_SERVICE_URL}/credentials/canvas/by-cognito-sub/{cognito_sub}"
```

**변경 후:**
```python
url = f"{USER_SERVICE_URL}/internal/v1/credentials/canvas/by-cognito-sub/{cognito_sub}"
```

### 2.4 Tests

**영향 받는 테스트 파일:**
1. `tests/e2e/test_canvas_sync_e2e.py`
   - Line 38: `f"{user_service}/api/v1/credentials/canvas"` ✅ 이미 올바름
   - Line 55: `f"{user_service}/api/v1/integrations/status"` ✅ 이미 올바름
   - Line 84: `f"{course_service}/api/v1/courses"` ✅ 이미 올바름
   - Line 137: `f"{course_service}/api/v1/courses/{first_course_id}/assignments"` ✅ 이미 올바름

2. `tests/integration/test_assignment_flow.py`
   - Line 50: `f"http://localhost:8082/api/assignments/canvas/{...}"` ❌ 변경 필요
     → `f"http://localhost:8082/v1/assignments/canvas/{...}"` (Gateway 우회하므로 `/api` 불필요)

3. Java 테스트 파일들:
   - `UserControllerTest.java`, `AuthControllerTest.java` 등
   - MockMvc 경로 업데이트 필요

---

## 3. 구현 계획

### Phase 1: Backend Services 변경

#### 1.1 User-Service

**작업 순서:**
1. `CredentialsInternalController.java` 생성
   - `/internal/v1/credentials/canvas/by-cognito-sub/{cognitoSub}` (GET)
   - X-Api-Key 인증
   - `InternalCanvasTokenResponse` DTO 사용 (복호화된 토큰 포함)

2. 기존 Controller들 경로 변경:
   - `UserController`: `/users` → `/v1/users`
   - `AuthController`: `/auth` → `/v1/auth`
   - `CredentialsController`: `/credentials` → `/v1/credentials`
   - `IntegrationStatusController`: `/integrations` → `/v1/integrations`

3. DTO 분리:
   - `CanvasTokenResponse` - 외부 API용 (암호화된 토큰 제외)
   - `InternalCanvasTokenResponse` - 내부 API용 (복호화된 토큰 포함)

4. SecurityConfig 수정:
   - `/v1/**`: JWT 검증 (API Gateway가 전달한 X-Cognito-Sub 헤더 검증)
   - `/internal/v1/**`: API Key 검증 (ServiceAuthValidator)

**파일 목록:**
- ✅ 수정: `UserController.java`
- ✅ 수정: `AuthController.java`
- ✅ 수정: `CredentialsController.java`
- ✅ 수정: `IntegrationStatusController.java`
- ✅ 생성: `CredentialsInternalController.java`
- ✅ 생성: `InternalCanvasTokenResponse.java` (DTO)
- ✅ 수정: `SecurityConfig.java`

#### 1.2 Course-Service

**작업 순서:**
1. Controller 경로 변경:
   - `CourseController`: `/courses` → `/v1/courses`
   - `AssignmentController`: `/api/assignments` → `/v1/assignments` (버그 수정)

**파일 목록:**
- ✅ 수정: `CourseController.java`
- ✅ 수정: `AssignmentController.java`

#### 1.3 Schedule-Service

**작업 순서:**
1. Controller 경로 변경:
   - `ScheduleController`: `/schedules` → `/v1/schedules`
   - `TodoController`: `/todos` → `/v1/todos`
   - `CategoryController`: `/categories` → `/v1/categories`

**파일 목록:**
- ✅ 수정: `ScheduleController.java`
- ✅ 수정: `TodoController.java`
- ✅ 수정: `CategoryController.java`

### Phase 2: API Gateway 변경

**파일:** `GatewayRoutesConfig.java`

**작업:**
1. RewritePath 규칙 변경:
   ```java
   .rewritePath("/api(?<segment>.*)", "$\\{segment}")
   ```

2. `/internal` 경로 차단 추가:
   ```java
   .route("block-internal-apis", r -> r
       .path("/api/internal/**")
       .filters(f -> f.setStatus(HttpStatus.FORBIDDEN))
       .uri("no://op")
   )
   ```

3. JWT 제외 경로 업데이트:
   ```yaml
   jwt:
     exclude-paths:
       - /api/v1/auth/**  # 그대로 유지
       - /actuator/**
   ```

**파일 목록:**
- ✅ 수정: `GatewayRoutesConfig.java`
- ✅ 검토: `application.yml` (JWT 제외 경로)

### Phase 3: Serverless 변경

**파일:** `canvas-sync-lambda/src/handler.py`

**작업:**
1. Line 257: `get_canvas_token()` 함수 수정
   ```python
   url = f"{USER_SERVICE_URL}/internal/v1/credentials/canvas/by-cognito-sub/{cognito_sub}"
   ```

**파일 목록:**
- ✅ 수정: `handler.py`

### Phase 4: Tests 변경

**작업:**
1. Python E2E 테스트:
   - ✅ `test_canvas_sync_e2e.py` - 이미 `/api/v1/*` 사용 중 (변경 불필요)
   - ✅ `test_assignment_flow.py` - Line 50 수정
     - `http://localhost:8082/api/assignments/...` → `http://localhost:8082/v1/assignments/...`

2. Java 테스트:
   - ✅ `UserControllerTest.java` - MockMvc 경로 업데이트
   - ✅ `AuthControllerTest.java` - MockMvc 경로 업데이트
   - ✅ `CredentialsControllerIntegrationTest.java` - API 경로 업데이트
   - ✅ `ScheduleControllerTest.java` - MockMvc 경로 업데이트

**파일 목록:**
- ✅ 수정: `test_assignment_flow.py`
- ✅ 수정: `UserControllerTest.java`
- ✅ 수정: `AuthControllerTest.java`
- ✅ 수정: `CredentialsControllerIntegrationTest.java`
- ✅ 수정: `ScheduleControllerTest.java`
- ✅ 수정: 기타 Controller 테스트 파일들

---

## 4. 검증 계획

### 4.1 Unit Tests
```bash
# 각 서비스별 단위 테스트
cd app/backend/user-service && ./gradlew test
cd app/backend/course-service && ./gradlew test
cd app/backend/schedule-service && ./gradlew test
cd app/backend/api-gateway && ./gradlew test
```

**성공 기준:**
- 모든 Controller 테스트 통과
- SecurityConfig 테스트 통과 (인증 경로 분리)

### 4.2 Integration Tests
```bash
# Python 통합 테스트
cd tests && pytest integration/ -v
```

**성공 기준:**
- `test_assignment_flow.py` 통과
- SQS → Service 플로우 정상 동작

### 4.3 E2E Tests
```bash
# E2E 테스트 (docker-compose 환경)
docker-compose up -d
cd tests && pytest e2e/ -v
```

**성공 기준:**
- `test_canvas_sync_e2e.py` 통과
- Canvas 토큰 등록 → Course/Assignment 동기화 플로우 정상
- Lambda → User-Service 내부 API 호출 성공

### 4.4 Manual API Testing

**외부 API (API Gateway 경유):**
```bash
# 1. 회원가입 (JWT 검증 제외)
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!","name":"Test User"}'

# 2. 로그인 (JWT 발급)
curl -X POST http://localhost:8080/api/v1/auth/signin \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!"}'

# 3. 내 정보 조회 (JWT 필요)
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <JWT_TOKEN>"

# 4. Canvas 토큰 등록 (JWT 필요)
curl -X POST http://localhost:8080/api/v1/credentials/canvas \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"canvasToken":"canvas_api_token_here"}'
```

**내부 API (직접 호출, API Gateway 우회):**
```bash
# Lambda가 User-Service 직접 호출 시뮬레이션
curl -X GET http://localhost:8081/internal/v1/credentials/canvas/by-cognito-sub/abc-123-def-456 \
  -H "X-Api-Key: local-dev-token"
```

**차단된 경로:**
```bash
# API Gateway를 통한 내부 API 접근 시도 → 403 Forbidden
curl -X GET http://localhost:8080/api/internal/v1/credentials/canvas/by-cognito-sub/abc-123 \
  -H "Authorization: Bearer <JWT_TOKEN>"
# Expected: 403 Forbidden
```

---

## 5. Rollback Plan

만약 문제 발생 시:

1. **Git Revert**
   ```bash
   git revert <migration-commit-hash>
   git push origin claude/add-internal-api-prefix-013ReK31qXcveDnLX1yGBLce
   ```

2. **단계별 Rollback**
   - Phase 4 실패 → 테스트만 원복
   - Phase 3 실패 → Lambda만 원복, 서비스는 유지 (하위 호환성 있음)
   - Phase 1-2 실패 → 전체 Rollback

3. **호환성 고려**
   - 새 경로(`/v1/*`, `/internal/v1/*`)와 기존 경로(`/*`) 동시 지원 가능
   - 하지만 운영 중이 아니므로 한번에 마이그레이션 권장

---

## 6. Post-Migration 작업

### 6.1 Documentation Update
- ✅ `docs/design/system-architecture.md` - API 경로 업데이트
- ✅ `app/backend/CLAUDE.md` - API Gateway 라우팅 규칙 업데이트
- ✅ README 파일들 - 예제 API 호출 업데이트

### 6.2 환경 변수 검토
- Lambda 환경 변수 확인: `USER_SERVICE_URL`, `CANVAS_SYNC_API_KEY`
- API Gateway 설정 확인: 서비스 URL

### 6.3 모니터링 설정 (향후)
- CloudWatch Metrics: `/v1/*` vs `/internal/v1/*` 분리
- API Gateway Logs: 차단된 `/api/internal/**` 요청 모니터링

---

## 7. 예상 소요 시간

| Phase | 작업 | 예상 시간 |
|-------|------|-----------|
| Phase 1 | Backend Services 변경 | 2-3시간 |
| Phase 2 | API Gateway 변경 | 30분 |
| Phase 3 | Serverless 변경 | 15분 |
| Phase 4 | Tests 변경 | 1-2시간 |
| 검증 | 전체 테스트 실행 및 수동 검증 | 1시간 |
| **합계** | | **5-7시간** |

---

## 8. 체크리스트

### Backend Services
- [ ] User-Service
  - [ ] UserController → /v1/users
  - [ ] AuthController → /v1/auth
  - [ ] CredentialsController → /v1/credentials
  - [ ] IntegrationStatusController → /v1/integrations
  - [ ] CredentialsInternalController 생성 (신규)
  - [ ] InternalCanvasTokenResponse DTO 생성 (신규)
  - [ ] SecurityConfig 수정 (/v1 vs /internal/v1)
- [ ] Course-Service
  - [ ] CourseController → /v1/courses
  - [ ] AssignmentController → /v1/assignments (버그 수정)
- [ ] Schedule-Service
  - [ ] ScheduleController → /v1/schedules
  - [ ] TodoController → /v1/todos
  - [ ] CategoryController → /v1/categories

### API Gateway
- [ ] GatewayRoutesConfig.java
  - [ ] RewritePath 규칙 변경
  - [ ] /internal 경로 차단 추가

### Serverless
- [ ] canvas-sync-lambda/handler.py
  - [ ] get_canvas_token() URL 변경

### Tests
- [ ] Python 테스트
  - [ ] test_assignment_flow.py 수정
- [ ] Java 테스트
  - [ ] UserControllerTest.java
  - [ ] AuthControllerTest.java
  - [ ] CredentialsControllerIntegrationTest.java
  - [ ] ScheduleControllerTest.java
  - [ ] 기타 Controller 테스트

### 검증
- [ ] Unit Tests 전체 통과
- [ ] Integration Tests 통과
- [ ] E2E Tests 통과
- [ ] Manual API Testing 완료

### Documentation
- [ ] system-architecture.md 업데이트
- [ ] backend CLAUDE.md 업데이트
- [ ] README 업데이트

---

## 9. 참고 자료

- MSA API Gateway 패턴: https://microservices.io/patterns/apigateway.html
- Spring Cloud Gateway 공식 문서: https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/
- AWS API Gateway Best Practices: https://docs.aws.amazon.com/apigateway/latest/developerguide/best-practices.html
