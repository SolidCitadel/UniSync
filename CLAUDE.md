# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요
Canvas LMS 연동 학업 일정관리 서비스. **자동 동기화 + AI 분석**으로 수동 입력 제거.

**현재 상태**: 기획/설계 완료, 구현 시작 전

## 아키텍처
- **마이크로서비스**: User/Course/Sync/Schedule/Social (Spring Boot, 서비스별 DB 분리)
- **서버리스**: Sync-Workflow (Step Functions), LLM (Lambda)
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

```
EventBridge (5분마다)
  → Step Functions
  → Canvas API 폴링 (Leader 토큰)
  → 새 과제 감지
     → SQS: assignment-events-queue
     → LLM Lambda: 분석
     → SQS: task-creation-queue
     → Sync-Service: task 저장
  → 제출 감지
     → SQS: submission-events-queue
     → LLM Lambda: 검증
     → Sync-Service: task 상태 업데이트
```

## 데이터 모델 핵심
- **Assignments**: `canvas_assignment_id` (UNIQUE)
- **Tasks**: `assignment_id` FK, `parent_task_id` (자기참조), `is_ai_generated`
- **Enrollments**: `is_sync_leader` (Leader 플래그)
- **Credentials**: `provider` ENUM, `access_token` (암호화)

## SQS 메시지

### assignment-events-queue
```json
{ "eventType": "ASSIGNMENT_CREATED", "assignmentId": "canvas_123", ... }
```

### submission-events-queue
```json
{ "eventType": "SUBMISSION_DETECTED", "userId": 1, "submissionMetadata": {...} }
```

### task-creation-queue (LLM → Sync-Service)
```json
{ "assignmentId": 10, "tasks": [{"title": "...", "subtasks": []}] }
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
cd app/backend/sync-service && ./gradlew bootRun
cd app/backend/schedule-service && ./gradlew bootRun
cd app/backend/social-service && ./gradlew bootRun

# 3. 프론트엔드 실행
cd frontend && npm run dev
```

### 테스트
```bash
# 단위 테스트
./gradlew test

# 통합 테스트
./gradlew build

# 특정 테스트 실행
./gradlew test --tests AssignmentServiceTest
```

### API 문서
- Swagger UI: http://localhost:808{1-5}/swagger-ui.html (각 서비스별)

## 서비스 포트
- User-Service: 8081
- Course-Service: 8082
- Sync-Service: 8083
- Schedule-Service: 8084
- Social-Service: 8085
- Frontend: 3000

## 참고 문서
- [기획서](./기획.md) - 문제 정의, 핵심 기능, 사용자 시나리오
- [설계서](./설계서.md) - 상세 아키텍처, API 설계, DB 스키마, 배포 전략