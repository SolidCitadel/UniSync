# Backend Services

백엔드 마이크로서비스 개발 환경 설정 및 실행 가이드입니다.

> **프로젝트 전체 개요는 [루트 README.md](../../README.md)를 참고하세요.**

## 빠른 시작

### 1. 환경변수 설정
```bash
# 템플릿 복사
cp .env.local.example .env.local

# 실제 값으로 수정 (ENCRYPTION_KEY, API 키 등)
vi .env.local
```

### 2. 인프라 실행
```bash
# MySQL, LocalStack 실행
docker-compose up -d

# LocalStack 초기화 확인
docker-compose logs localstack | grep "Cognito User Pool created"

# .env 파일에서 생성된 Cognito 값 확인
cat .env | grep COGNITO

# .env.local에 Cognito 값 복사
# COGNITO_USER_POOL_ID=ap-northeast-2_xxxxx
# COGNITO_CLIENT_ID=xxxxx
```

### 3. 서비스 실행
```bash
# user-service 실행
cd app/backend/user-service
./gradlew bootRun

# 또는 IDE에서 Active Profile을 'local'로 설정 후 실행
# IntelliJ: Run > Edit Configurations > Active profiles: local
```

## 서비스 포트

- **API Gateway**: 8080 (모든 요청 진입점, JWT 인증)
- User-Service: 8081
- Course-Service: 8082
- Schedule-Service: 8083

## API Gateway 라우팅

### 외부 API (프론트엔드 → API Gateway → 백엔드)

API Gateway는 `/api` prefix만 제거하여 백엔드 서비스로 전달:

```
# User-Service
클라이언트: /api/v1/auth/**            → 백엔드: /v1/auth/**
클라이언트: /api/v1/users/**           → 백엔드: /v1/users/**
클라이언트: /api/v1/credentials/**     → 백엔드: /v1/credentials/**
클라이언트: /api/v1/friends/**         → 백엔드: /v1/friends/**
클라이언트: /api/v1/integrations/**    → 백엔드: /v1/integrations/**

# Course-Service
클라이언트: /api/v1/courses/**         → 백엔드: /v1/courses/**
클라이언트: /api/v1/assignments/**     → 백엔드: /v1/assignments/**
클라이언트: /api/v1/tasks/**           → 백엔드: /v1/tasks/**
클라이언트: /api/v1/notices/**         → 백엔드: /v1/notices/**

# Schedule-Service
클라이언트: /api/v1/schedules/**       → 백엔드: /v1/schedules/**
클라이언트: /api/v1/todos/**           → 백엔드: /v1/todos/**
클라이언트: /api/v1/categories/**      → 백엔드: /v1/categories/**
```

**인증 예외 (JWT 불필요)**: `/api/v1/auth/signup`, `/api/v1/auth/signin`

### 내부 API (서비스 간 직접 통신)

Lambda 등 내부 서비스는 백엔드에 직접 호출:

```
# User-Service 내부 API
Lambda → 백엔드: /internal/v1/credentials/canvas/by-cognito-sub/{cognitoSub}
  - 인증: X-Api-Key 헤더
  - 용도: Canvas 토큰 조회 (복호화된 토큰 반환)

# User-Service → Lambda 통신 (Phase 1 Canvas 동기화)
User-Service → Lambda: canvas-sync-lambda 함수 직접 호출 (AWS SDK)
  - Payload: {"cognitoSub": "..."}
  - 응답: {"statusCode": 200, "body": {"coursesCount": 5, ...}}
```

**API Gateway 차단**: `/api/internal/**` 경로는 403 Forbidden 응답

## 환경변수 및 프로파일

### 환경변수 파일 구조

```
.env                    # docker-compose 인프라 설정 (커밋됨)
.env.common             # 앱 컨테이너 공통 설정 (커밋됨)
.env.local              # 로컬 개발 전체 설정 (gitignore)
.env.local.example      # 템플릿 (커밋됨)
.env.acceptance         # acceptance 오버라이드 (커밋됨)
.env.demo               # demo 오버라이드 (커밋됨)
```

**역할**:
- **`.env`**: docker-compose.yml이 자동 로드. MySQL, LocalStack 등 인프라 설정
- **`.env.common`**: 컨테이너에 주입되는 공통 앱 설정 (Service URLs, SQS 큐 등)
- **`.env.local`**: **Spring 직접 실행용 모든 설정** (gitignore). 비밀 정보 + 로컬 전용 + .env.common 전체 내용
- **`.env.acceptance`**: acceptance 테스트 특화 설정 (.env.common 오버라이드)
- **`.env.demo`**: 데모 환경 특화 설정 (.env.common 오버라이드)

### Spring 프로파일 (application-*.yml)

각 서비스의 `src/main/resources/`:

1. **`application.yml`** (공통)
   - 모든 프로파일 공유 기본값
   - 민감 정보는 `${...}` 플레이스홀더
   - 커밋됨 ✓

2. **`application-local.yml`** (로컬 개발용)
   - IDE에서 직접 실행 시 사용
   - localhost로 인프라 접속
   - 모든 값은 플레이스홀더
   - Gradle이 `.env.local` 로드
   - 커밋됨 ✓

3. **`application-acceptance.yml`** (인수 테스트용)
   - docker-compose.acceptance.yml 전용
   - `ddl-auto: create-drop`
   - 커밋됨 ✓

4. **`application-prod.yml`** (운영용)
   - docker-compose.demo.yml 및 ECS 배포용
   - `ddl-auto: validate`
   - 커밋됨 ✓

> **환경변수/프로파일 상세 가이드는 [시스템 아키텍처](../../docs/design/system-architecture.md)를 참고하세요.**

### Gradle 환경변수 로딩

모든 서비스의 `build.gradle.kts`에 java-dotenv 설정:

```kotlin
// .env.local 로드 (모든 설정 포함)
val localEnv = Dotenv.configure()
    .directory(rootDir.absolutePath)
    .filename(".env.local")
    .ignoreIfMissing()
    .load()

tasks.withType<Test> {
    environment(envMap)
}

tasks.named<BootRun>("bootRun") {
    environment(envMap)
}
```

**동작**:
- `./gradlew bootRun` 또는 `./gradlew test` 실행 시 `.env.local` 자동 로드
- IDE에서도 Gradle을 통해 실행하면 동일하게 동작

### 환경변수 로드 확인

```bash
# user-service 환경변수 확인
cd app/backend/user-service
./gradlew printEnv

# course-service
cd app/backend/course-service
./gradlew printEnv

# schedule-service
cd app/backend/schedule-service
./gradlew printEnv

# api-gateway
cd app/backend/api-gateway
./gradlew printEnv
```

## 환경별 실행 방법

### 로컬 개발 (IDE)
```bash
# 1. 인프라 실행
docker-compose up -d

# 2. IDE에서 서비스 실행
# Profile: local
# Gradle이 .env.local 자동 로드
```

### Acceptance 테스트
```bash
# 사전 준비: .env.local 필요 (LOCALSTACK_AUTH_TOKEN)
docker-compose -f docker-compose.acceptance.yml up --build

# 정리
docker-compose -f docker-compose.acceptance.yml down -v
```

### Demo (전체 시스템)
```bash
# 사전 준비: .env.local 필요 (LOCALSTACK_AUTH_TOKEN)
docker-compose -f docker-compose.demo.yml up

# 정리
docker-compose -f docker-compose.demo.yml down
```

## DDD 구조 예시

### User Service
```
com.unisync.userservice/
├── auth/                   # 인증 도메인
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── exception/
├── credentials/            # Canvas 토큰 관리 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
└── common/
    ├── entity/             # User, Credential
    ├── repository/
    ├── config/
    └── exception/
```

### Course Service
```
com.unisync.courseservice/
├── course/
├── assignment/
├── enrollment/
└── common/
    ├── entity/             # Course, Assignment, Enrollment
    ├── repository/
    └── config/
```

### Schedule Service
```
com.unisync.scheduleservice/
├── schedule/
├── todo/
├── category/
└── common/
    ├── entity/             # Schedule, Todo, Category
    ├── repository/
    └── config/
```

> **DDD 구조 상세 원칙은 [시스템 아키텍처](../../docs/design/system-architecture.md)를 참고하세요.**

## 테스트

### Unit Tests
```bash
# 특정 서비스 테스트
cd app/backend/course-service
./gradlew test

# 모든 서비스 테스트 (루트에서)
./gradlew test
```

> **Unit 테스트 작성 가이드는 [테스팅 전략](../../docs/design/testing-strategy.md)을 참고하세요.**

### System Tests
```bash
# 전제 조건: docker-compose.acceptance.yml 실행 중
docker-compose -f docker-compose.acceptance.yml up -d

# 전체 System Tests
poetry run pytest system-tests/ -v
```

> **System 테스트 가이드는 [테스팅 전략](../../docs/design/testing-strategy.md) 및 [System Tests README](../../system-tests/README.md)를 참고하세요.**

## 트러블슈팅

### 문제: 환경변수가 로드 안됨
**해결**:
1. `.env.local` 파일 존재 확인
2. Gradle 스크립트 확인 (build.gradle.kts)
3. `./gradlew printEnv` 실행하여 로드 상태 확인

### 문제: LocalStack 연결 실패
**해결**:
1. `docker-compose ps` 실행하여 LocalStack 상태 확인
2. `.env.local`에 `LOCALSTACK_AUTH_TOKEN` 존재 확인
3. `SQS_ENDPOINT=http://localhost:4566` 확인

### 문제: 서비스 간 통신 실패
**해결**:
1. 포트 번호 확인 (8080-8083)
2. `SERVICE_URL` 환경변수 확인
3. API Gateway 라우팅 설정 확인 (GatewayRoutesConfig.java)

## 참고 문서

- [프로젝트 전체 개요](../../README.md)
- [시스템 아키텍처](../../docs/design/system-architecture.md)
- [SQS 아키텍처](../../docs/design/sqs-architecture.md)
- [테스팅 전략](../../docs/design/testing-strategy.md)
