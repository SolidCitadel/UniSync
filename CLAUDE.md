# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요
Canvas LMS 연동 학업 일정관리 서비스. **자동 동기화 + AI 분석**으로 수동 입력 제거.

**현재 상태**: Phase 2 진행 중 (Canvas 동기화 및 SQS 이벤트 처리)

**구현 완료 항목**:
- ✅ 기본 인프라 구조 (Docker, LocalStack, MySQL)
- ✅ Spring Boot 마이크로서비스 기본 구조 (User, Course, Schedule)
- ✅ **API Gateway (Spring Cloud Gateway + JWT 인증 + Cognito 연동)**
- ✅ Canvas Sync Lambda 구현
- ✅ LLM Lambda 구현
- ✅ SQS 이벤트 기반 통신
- ✅ 공유 모듈(java-common, python-common) 기반 DTO 표준화
- ✅ E2E 통합 테스트 환경
- ✅ course-service의 SQS 구독 및 Assignment 처리

**진행 중**:
- 🚧 User-Service의 인증 및 Canvas 토큰 관리
- 🚧 Schedule-Service의 일정 통합 기능
- 🚧 Step Functions 워크플로우 구성
- 🚧 LLM Task 생성 자동화

## 아키텍처
- **마이크로서비스** (3개): Spring Boot 기반, 서비스별 DB 분리
  - **User-Service**: 사용자/인증/소셜 기능
  - **Course-Service**: Canvas 학업 데이터 (과목/과제/Task)
  - **Schedule-Service**: 시간 기반 일정 통합
- **서버리스**: Canvas-Sync-Workflow, Google-Calendar-Sync-Workflow (Step Functions + Lambda), LLM-Lambda
- **이벤트 기반**: SQS로 비동기 통신

## 기술 스택 (프로젝트 공통)

### Backend
- **Java**: 21 (LTS)
- **Spring Boot**: 3.5.7
- **빌드 도구**: Gradle 8.5 + Kotlin DSL
- **데이터베이스**: MySQL 8.0
- **ORM**: Spring Data JPA (Hibernate)
- **인증**: AWS Cognito + JWT
- **API 문서**: SpringDoc OpenAPI 3 (Swagger)

### 주요 라이브러리 버전
- AWS SDK: 2.29.45
- JJWT: 0.12.6
- Lombok: (Spring Boot 관리)
- MySQL Connector: (Spring Boot 관리)

### 인프라
- **로컬 개발**: Docker Compose + LocalStack
- **메시징**: SQS
- **워크플로우**: Step Functions
- **서버리스**: Lambda
- **스토리지**: S3

### Frontend (예정)
- React 18 + TypeScript
- Vite
- TanStack Query (React Query)

## 중요한 설계 결정

### 1. Canvas API 토큰 방식 (OAuth2 ❌)
- 사용자가 Canvas에서 직접 API 토큰 발급 → UniSync에 입력
- AES-256 암호화 저장
- Credentials 테이블에 `provider='CANVAS'`로 저장

### 2. AI 자동화 (사용자 버튼 ❌)
- 새 과제 감지 → LLM 자동 분석 → task/subtask 생성
- 제출물 감지 → LLM 자동 검증 → 유효하면 task 상태 DONE
- **사용자 액션 없이 Sync-Workflow에서 자동 실행**

### 3. Leader 선출 (과목당 1명만 Canvas API 호출)
- 과목 첫 연동자가 Leader (`is_sync_leader=true`)
- Leader 토큰으로만 Canvas API 폴링 → 비용 절감
- 조회 데이터는 모든 수강생 공유

## 핵심 워크플로우

### Canvas 동기화
```
EventBridge (5분마다)
  → Canvas-Sync-Workflow (Step Functions)
  → Canvas API 폴링 (Leader 토큰)
  → 새 과제 감지
     → SQS: assignment-events-queue
     → LLM-Lambda: 과제 분석
     → SQS: task-creation-queue
     → Course-Service: Assignment & Task 저장
     → Course-Service → Schedule-Service: 일정 생성
  → 제출 감지
     → SQS: submission-events-queue
     → LLM-Lambda: 제출물 검증
     → Course-Service: Task 상태 업데이트
```

### 외부 캘린더 동기화
```
EventBridge
  → Google-Calendar-Sync-Workflow (Step Functions)
  → Google Calendar API 폴링
  → 변경 감지
     → SQS: calendar-events-queue
     → Schedule-Service: User_Schedules 저장
```

## 데이터 모델 핵심
- **Assignments**: `canvas_assignment_id` (UNIQUE)
- **Tasks**: `assignment_id` FK, `parent_task_id` (자기참조), `is_ai_generated`
- **Enrollments**: `is_sync_leader` (Leader 플래그)
- **Credentials**: `provider` ENUM, `access_token` (암호화)

## 공유 모듈 (Shared Modules)

서비스 간 DTO를 표준화하기 위한 공유 모듈을 사용합니다.

### 구조
```
app/shared/
├── java-common/         # Spring Boot 서비스용
│   └── src/main/java/com/unisync/shared/dto/sqs/
│       └── AssignmentEventMessage.java
├── python-common/       # Lambda 함수용
│   └── unisync_shared/dto/
│       └── assignment_event.py
└── message-schemas/     # JSON Schema 정의
```

### 사용법

#### Java (Spring Boot)
```kotlin
// settings.gradle.kts
includeBuild("../../shared/java-common")

// build.gradle.kts
dependencies {
    implementation("com.unisync:java-common:1.0.0")
}
```

#### Python (Lambda)
```python
# requirements.txt
-e ../../shared/python-common

# 코드에서 사용
from unisync_shared.dto import AssignmentEventMessage
```

### 주요 메시지 스키마

#### assignment-events-queue
```json
{
  "eventType": "ASSIGNMENT_CREATED | ASSIGNMENT_UPDATED",
  "canvasAssignmentId": 123456,
  "canvasCourseId": 789,
  "title": "중간고사 프로젝트",
  "description": "Spring Boot로 REST API 구현",
  "dueAt": "2025-11-15T23:59:59",
  "pointsPossible": 100,
  "submissionTypes": "online_upload"
}
```

자세한 내용은 [app/shared/README.md](app/shared/README.md) 참고

## 환경변수 및 프로파일 관리 규칙

### 프로파일 구분
모든 Spring Boot 서비스는 `local`과 `docker` 프로파일을 구분합니다:

- **`local`**: 로컬 개발 환경 (IDE에서 직접 실행)
  - `.env` 파일에서 환경변수 자동 로드 (spring-dotenv 사용)
  - 의미 있는 기본값 제공 (예: `localhost:8081`)

- **`docker`**: Docker Compose 환경
  - `docker-compose.yml`에서 환경변수 주입
  - 기본값 없음 (모든 환경변수는 compose에서 제공)

### 파일 구조
```
application.yml          # 공통 설정, 기본값 없음 (환경변수만 참조)
application-local.yml    # 로컬 개발용 기본값 + dotenv 설정
application-docker.yml   # Docker용 (환경변수만, 기본값 없음)
application-test.yml     # 테스트용 (H2 DB, 고정 테스트 값)
```

### 환경변수 사용 규칙

#### ❌ 금지사항
```yaml
# application.yml에 의미 없는 기본값 제공
canvas:
  base-url: ${CANVAS_BASE_URL:https://canvas.instructure.com}  # ❌ 의미 없는 기본값
```

#### ✅ 올바른 방식

**application.yml** (공통):
```yaml
# 기본값 없음 - 환경변수 필수
canvas:
  base-url: ${CANVAS_BASE_URL}
```

**application-local.yml** (로컬 개발):
```yaml
spring:
  dotenv:
    location: file:../../.env  # 프로젝트 루트의 .env 읽기

# 의미 있는 기본값 제공
canvas:
  base-url: ${CANVAS_BASE_URL:https://khcanvas.khu.ac.kr}
```

**application-docker.yml** (Docker):
```yaml
# 기본값 없음 - docker-compose가 모두 주입
# (이 파일에는 환경변수 설정 불필요)
```

### 기본값 제공 기준

**기본값을 제공해야 하는 경우** (`application-local.yml`):
- ✅ 로컬 개발 시 고정된 값 (예: `localhost:8081`)
- ✅ 프로젝트 특화 값 (예: `https://khcanvas.khu.ac.kr`)
- ✅ 로컬 개발 환경 설정 (예: `LocalStack endpoint`)

**기본값을 제공하지 말아야 하는 경우** (`application.yml`):
- ❌ 환경에 따라 달라지는 값
- ❌ 의미 없는 placeholder 값
- ❌ 보안 관련 값 (토큰, 키 등)

**원칙**: 기본값이 없으면 오류를 띄워서 필수 환경변수 누락을 명확히 알림

### .env 파일 관리
- 프로젝트 루트에 `.env` 파일 위치
- `spring-dotenv` 라이브러리로 자동 로드 (local 프로파일)
- `.env.example`로 필요한 환경변수 문서화
- `.env`는 `.gitignore`에 포함 (보안)

## 주의사항

### 절대 금지
- Canvas 토큰 평문 저장
- 서비스 간 DB 직접 접근 (반드시 API/이벤트)
- 사용자 입력 검증 생략

### 핵심 원칙
- JWT에서 user_id 추출하여 본인 데이터만 접근
- 외부 API 호출은 SQS 비동기 처리
- Entity 직접 반환 금지 (DTO 변환)

## 코드 구조 원칙 (DDD)

### 도메인 단위 패키지 구조
각 마이크로서비스는 **도메인 단위(Domain-based)**로 구성합니다. Layer-based 구조는 사용하지 않습니다.

```
com.unisync.{service}/
├── {domain1}/              # 도메인 1
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── exception/
├── {domain2}/              # 도메인 2
│   ├── controller/
│   ├── service/
│   └── dto/
└── common/                 # 공통
    ├── entity/            # 엔티티 (DB 모델)
    ├── repository/        # JPA Repository
    ├── config/            # 설정
    └── exception/         # 공통 예외
```

### User Service 예시
```
com.unisync.user/
├── auth/                   # 인증 도메인
│   ├── controller/         # AuthController
│   ├── service/            # AuthService, CognitoService
│   ├── dto/                # SignUpRequest, SignInRequest, AuthResponse
│   └── exception/
├── profile/                # 프로필 도메인 (예정)
├── credentials/            # Canvas 토큰 관리 도메인 (예정)
└── common/
    ├── entity/             # User
    ├── repository/         # UserRepository
    └── config/             # AwsCognitoConfig
```

### DDD 설계 원칙

#### 1. 도메인별 응집도 (High Cohesion)
- 관련 기능은 같은 도메인 패키지에 배치
- 예: 회원가입/로그인은 `auth` 도메인에 모두 위치

#### 2. 도메인 간 낮은 결합도 (Low Coupling)
- 도메인 간 직접 의존 금지 → `common` 패키지를 통해 공유
- 예: User Entity는 `common.entity`에 위치하여 모든 도메인에서 참조 가능

#### 3. 명확한 경계 (Bounded Context)
- 각 도메인은 독립적으로 동작 가능
- 도메인 추가/수정 시 다른 도메인에 영향 최소화

#### 4. DTO 분리
- 각 도메인은 자신의 DTO만 사용
- Entity는 절대 Controller에서 직접 반환 금지

#### 5. 공통 요소 관리
- **Entity**: 여러 도메인에서 사용되므로 `common/entity`
- **Repository**: Entity와 함께 `common/repository`
- **Config**: 공통 설정은 `common/config`
- **Exception**: 도메인 특화 예외는 각 도메인, 공통 예외는 `common/exception`

### 장점
- ✅ 기능 추가/수정 시 해당 도메인만 변경
- ✅ 팀 협업 시 도메인별 작업으로 충돌 최소화
- ✅ 코드 네비게이션 용이 (관련 코드가 한 곳에)
- ✅ 마이크로서비스 분리 시 도메인 단위로 추출 가능
- ✅ 테스트 격리 용이

## 개발 환경 설정 (구현 시 사용)

### 로컬 실행
```bash
# 1. 인프라 시작
docker-compose up -d localstack mysql

# 2. 각 서비스 실행 (Gradle Kotlin DSL)
cd app/backend/user-service && ./gradlew bootRun
cd app/backend/course-service && ./gradlew bootRun
cd app/backend/schedule-service && ./gradlew bootRun

# 3. 프론트엔드 실행
cd frontend && npm run dev
```

### 테스트

#### 단위 테스트
```bash
# Java 단위 테스트
./gradlew test

# Python Lambda 함수 테스트
cd app/serverless
python -m pytest canvas-sync-lambda/tests/ -v
python -m pytest llm-lambda/tests/ -v

# 특정 테스트 실행
./gradlew test --tests CourseServiceTest
```

#### E2E 통합 테스트

전체 워크플로우를 검증하는 통합 테스트 환경이 구축되어 있습니다:

```bash
# 자동화된 통합 테스트 (권장)
./scripts/run-integration-tests.sh

# 수동 실행
docker-compose -f docker-compose.test.yml up -d
python -m pytest tests/integration/ -v
docker-compose -f docker-compose.test.yml down -v
```

**테스트 시나리오**:
- Canvas API → Lambda → SQS → Course-Service → DB
- Assignment 생성/수정/중복 처리 검증
- SQS 메시지 처리 검증

자세한 내용은 다음 문서를 참고하세요:
- [tests/README.md](tests/README.md) - 통합 테스트 가이드
- [app/serverless/TESTING.md](app/serverless/TESTING.md) - Lambda 테스트 가이드

### API 문서
- Swagger UI: http://localhost:808{1-3}/swagger-ui.html (각 서비스별)

## 서비스 포트
- **API Gateway: 8080** (모든 요청의 진입점)
- User-Service: 8081
- Course-Service: 8082
- Schedule-Service: 8083
- Frontend: 3000 (예정)

**라우팅 규칙**:
- `/api/v1/auth/**, /api/v1/users/**, /api/v1/friends/**` → User-Service
- `/api/v1/courses/**, /api/v1/assignments/**, /api/v1/tasks/**` → Course-Service
- `/api/v1/schedules/**` → Schedule-Service

## 참고 문서
- [기획서](./기획.md) - 문제 정의, 핵심 기능, 사용자 시나리오
- [설계서](./설계서.md) - 상세 아키텍처, API 설계, DB 스키마, 배포 전략