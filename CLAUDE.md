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
- 🚧 Schedule-Service의 일정 및 할일 관리 기능
- 🚧 Step Functions 워크플로우 구성
- 🚧 LLM 할일 자동 생성

## 아키텍처
- **마이크로서비스** (3개): Spring Boot 기반, 서비스별 DB 분리
  - **User-Service**: 사용자/인증/소셜 기능
  - **Course-Service**: Canvas 학업 데이터 (과목/과제)
  - **Schedule-Service**: 일정(Schedule, 시간 단위) 및 할일(Todo, 기간 단위) 통합 관리
- **서버리스**: Canvas-Sync-Workflow, Google-Calendar-Sync-Workflow (Step Functions + Lambda), LLM-Lambda
- **이벤트 기반**: SQS로 비동기 통신

## 기술 스택

### Backend
- **Java 21** (LTS) + **Spring Boot 3.5.7**
- **Gradle 8.5** + Kotlin DSL
- **MySQL 8.0** + Spring Data JPA
- **AWS Cognito** + JWT
- **SpringDoc OpenAPI 3** (Swagger)

### 인프라
- **로컬 개발**: Docker Compose + LocalStack
- **메시징**: SQS
- **워크플로우**: Step Functions
- **서버리스**: Lambda
- **스토리지**: S3

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
     → Course-Service: Assignment 저장
     → Schedule-Service:
        1. 일정(Schedule) 자동 생성 (과제 마감일)
        2. LLM-Lambda 트리거: 과제 설명 분석
        3. 할일(Todo) 및 서브태스크 자동 생성
  → 제출 감지
     → SQS: submission-events-queue
     → LLM-Lambda: 제출물 검증
     → Schedule-Service: 일정/할일 상태 업데이트
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
- **Assignments**: `canvas_assignment_id` (UNIQUE) - Course-Service
- **Schedules**: `start_time`, `end_time`, `source` (CANVAS/USER/GOOGLE 등), `category_id` (필수) - Schedule-Service
- **Todos**: `start_date`, `due_date` (둘 다 필수), `schedule_id` FK, `parent_todo_id` (서브태스크), `is_ai_generated` - Schedule-Service
- **Categories**: 일정/할일 분류 체계, 개인/그룹별 - Schedule-Service
- **Groups**: 협업을 위한 그룹, 권한 관리 (OWNER, ADMIN, MEMBER) - Schedule-Service
- **Enrollments**: `is_sync_leader` (Leader 플래그) - Course-Service
- **Credentials**: `provider` ENUM, `access_token` (암호화) - User-Service

## 공유 모듈 (Shared Modules)

서비스 간 DTO 표준화를 위해 `java-common`, `python-common` 모듈 사용.

**주요 메시지 스키마** (`assignment-events-queue`):
```json
{
  "eventType": "ASSIGNMENT_CREATED | ASSIGNMENT_UPDATED",
  "canvasAssignmentId": 123456,
  "canvasCourseId": 789,
  "title": "중간고사 프로젝트",
  "dueAt": "2025-11-15T23:59:59",
  "pointsPossible": 100,
  "submissionTypes": "online_upload"
}
```

자세한 내용: [app/shared/README.md](app/shared/README.md)

## 환경변수 및 프로파일 관리 규칙

### 프로파일 구분
- **`local`**: 로컬 개발용, `application-local.yml`에 하드코딩 (.gitignore 처리)
- **`docker`**: Docker Compose, 환경변수 주입
- **`test`**: 테스트용, H2 인메모리 DB

### 로컬 개발 환경 설정 (중요!)

**구조 개요**:
```
루트/.env (gitignored)
  ↓ (LocalStack 초기화 스크립트가 자동 업데이트)
  ↓ (sync-local-config.py로 동기화)
  ↓
각 서비스/application-local.yml (gitignored, 하드코딩)
  ↓
IDE에서 서비스 실행 (Profile: local)
```

**1단계: LocalStack 실행** (최초 1회 또는 재시작 시)
```bash
docker-compose up -d
# LocalStack 초기화 스크립트가 Cognito User Pool 생성 후 루트/.env 자동 업데이트
```

**2단계: 환경변수 동기화** (IDE 로컬 실행 전)
```bash
python scripts/dev/sync-local-config.py
```

이 스크립트는:
- 루트 `.env` 파일에서 모든 환경변수 읽기
- 각 서비스의 `application-local.yml` 파일에 자동 업데이트
  - Cognito User Pool ID, Client ID
  - MySQL 비밀번호
  - 암호화 키
  - SQS 큐 이름
  - API 키
  - Canvas Base URL
- YAML 형식과 주석 유지

**3단계: IDE에서 서비스 실행**
- Active Profile을 `local`로 설정
- 각 서비스는 `application-local.yml`의 하드코딩된 값 사용

**신규 개발자 초기 설정**:
```bash
# 1. 각 서비스별로 application-local.yml 생성
cd app/backend/user-service/src/main/resources
cp application-local.yml.example application-local.yml

cd ../../course-service/src/main/resources
cp application-local.yml.example application-local.yml

cd ../../schedule-service/src/main/resources
cp application-local.yml.example application-local.yml

cd ../../api-gateway/src/main/resources
cp application-local.yml.example application-local.yml

# 2. LocalStack 실행
docker-compose up -d

# 3. 환경변수 동기화
python scripts/dev/sync-local-config.py

# 4. IDE에서 Active Profile을 'local'로 설정 후 서비스 실행
```

**주의사항**:
- `application-local.yml`은 `.gitignore`에 포함되어 커밋되지 않음
- LocalStack 재시작 시 User Pool ID가 변경될 수 있으므로 `sync-local-config.py` 재실행 필요
- `.env` 파일도 `.gitignore`에 포함되어 있으며, 민감한 정보(API 키 등) 포함

### Docker/배포 환경

```yaml
# docker-compose.app.yml
user-service:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - USER_SERVICE_DATABASE_URL=jdbc:mysql://mysql:3306/user_db?...
    - COGNITO_USER_POOL_ID=${COGNITO_USER_POOL_ID}  # .env에서 주입
```

```bash
# AWS ECS (프로덕션)
USER_SERVICE_DATABASE_URL=jdbc:mysql://rds-endpoint/user_db?...
COGNITO_USER_POOL_ID=ap-northeast-2_xxx  # AWS Secrets Manager에서 주입
```

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

### 핵심 원칙
- 관련 기능은 같은 도메인 패키지에 배치 (High Cohesion)
- 도메인 간 직접 의존 금지 → `common`을 통해 공유 (Low Coupling)
- Entity는 `common/entity`, Repository는 `common/repository`
- 도메인 특화 예외는 각 도메인, 공통 예외는 `common/exception`
- Entity 직접 반환 금지 - 각 도메인의 DTO만 사용

## 서비스 포트
- **API Gateway: 8080** (모든 요청의 진입점, JWT 인증)
- User-Service: 8081
- Course-Service: 8082
- Schedule-Service: 8083

**API Gateway 라우팅** (path prefix `/api/v1` 제거 후 백엔드 서비스로 전달):
```yaml
# User-Service (사용자/인증/소셜/그룹)
/api/v1/auth/**        → /auth/**
/api/v1/users/**       → /users/**
/api/v1/friends/**     → /friends/**
/api/v1/groups/**      → /groups/**

# Course-Service (Canvas 학업 데이터)
/api/v1/courses/**     → /courses/**
/api/v1/assignments/** → /assignments/**
/api/v1/notices/**     → /notices/**

# Schedule-Service (일정 + 할일)
/api/v1/schedules/**   → /schedules/**
/api/v1/todos/**       → /todos/**
/api/v1/categories/**  → /categories/**
```

**백엔드 서비스 엔드포인트**: 환경변수로 주입 (로컬/Docker/ECS 환경별 상이)

**인증 예외** (JWT 불필요):
- `/api/v1/auth/register`, `/api/v1/auth/login`

## 테스트 구조

### 디렉토리 구조
```
tests/                                # 통합/E2E 테스트 (Python)
├── api/                              # 외부 API 직접 호출 테스트
│   └── test_canvas_api.py
├── integration/                      # 서비스 간 통합 테스트
│   ├── test_assignment_flow.py       # SQS → Service → DB
│   ├── test_assignment_flow_with_lambda.py
│   └── test_lambda_integration.py    # LocalStack Lambda 배포/호출
├── e2e/                              # End-to-End 테스트
│   ├── test_canvas_sync_e2e.py
│   └── test_canvas_sync_with_jwt_e2e.py
└── README.md

app/backend/{service}/src/test/       # Java 서비스별 단위/통합 테스트
├── user-service/src/test/
├── course-service/src/test/
└── schedule-service/src/test/

app/serverless/{lambda}/tests/        # Lambda별 단위 테스트 (Python)
├── canvas-sync-lambda/tests/
└── llm-lambda/tests/

scripts/test/                         # 테스트 실행 스크립트
├── test-all.py                       # 대화형 메뉴
├── test-unit.sh/bat                  # Lambda 단위 테스트 실행
└── test-e2e.sh/bat                   # E2E 테스트 실행
```

### 테스트 레벨
- **단위 테스트**: Lambda/서비스별 로직 검증 (`app/{type}/{name}/tests/`)
- **통합 테스트**: 서비스 간 협업 검증 (`tests/integration/`)
- **E2E 테스트**: Canvas API부터 DB까지 전체 플로우 (`tests/e2e/`)
- **API 테스트**: 외부 API 직접 호출 검증 (`tests/api/`)

### 테스트 실행
```bash
# 대화형 메뉴 (권장)
python scripts/test/test-all.py

# Lambda 단위 테스트
bash scripts/test/test-unit.sh

# E2E 테스트
bash scripts/test/test-e2e.sh

# 특정 서비스 테스트 (Java)
cd app/backend/course-service
./gradlew test
```

자세한 내용: [tests/README.md](tests/README.md)

## 참고 문서
- [기획서](./기획.md) - 문제 정의, 핵심 기능, 사용자 시나리오
- [설계서](./설계서.md) - 상세 아키텍처, API 설계, DB 스키마, 배포 전략