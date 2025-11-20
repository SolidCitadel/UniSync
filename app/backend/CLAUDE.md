# Backend Services - 개발 환경 가이드

백엔드 마이크로서비스 개발을 위한 환경 설정, 프로파일 관리, 코드 구조 가이드입니다.

> **프로젝트 전체 개요는 [루트 CLAUDE.md](../../CLAUDE.md)를 참고하세요.**
> 이 문서는 백엔드 개발에 필요한 실무적인 정보만 다룹니다.

## 서비스 포트
- **API Gateway**: 8080 (모든 요청 진입점, JWT 인증)
- User-Service: 8081
- Course-Service: 8082
- Schedule-Service: 8083

## API Gateway 라우팅
API Gateway는 `/api` prefix 제거 후 백엔드 서비스로 전달:

```
# User-Service
/api/auth/**        → /auth/**
/api/users/**       → /users/**
/api/credentials/** → /credentials/**
/api/friends/**     → /friends/**
/api/groups/**      → /groups/**

# Course-Service
/api/courses/**     → /courses/**
/api/assignments/** → /assignments/**
/api/enrollments/** → /enrollments/**

# Schedule-Service
/api/schedules/**   → /schedules/**
/api/todos/**       → /todos/**
/api/categories/**  → /categories/**
```

인증 예외 (JWT 불필요): `/api/auth/register`, `/api/auth/login`

백엔드 서비스 엔드포인트는 환경변수로 주입 (로컬/Docker/ECS 환경별 상이).

## 환경변수 및 프로파일 관리

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
- **`.env`**: docker-compose.yml이 자동 로드. MySQL, LocalStack 등 인프라 설정 (컨테이너 구성용)
- **`.env.common`**: docker-compose 실행 시 컨테이너에 주입되는 공통 앱 설정 (Service URLs, SQS 큐, Canvas URL 등)
- **`.env.local`**: **Spring 직접 실행용 모든 설정** (gitignore). 비밀 정보 + 로컬 오버라이드 (localhost) + .env.common 전체 내용 포함
- **`.env.acceptance`**: acceptance 테스트 특화 설정 (.env.common 오버라이드, 테스트 DB/더미 키)
- **`.env.demo`**: 데모 환경 특화 설정 (.env.common 오버라이드, DockerHub 이미지 실행용)

**핵심 원칙**:
- **IDE 직접 실행**: `.env.local`만 읽음 (Gradle이 로드, 모든 설정 포함)
- **compose 실행**: `.env.common` + `.env.{acceptance|demo}` 조합 (env_file로 주입)

### Spring 프로파일 (application-*.yml)

각 서비스의 `src/main/resources/`에 다음 프로파일 파일들이 있습니다:

1. **`application.yml`** (공통)
   - 모든 프로파일이 공유하는 기본값
   - 민감 정보는 `${...}` 플레이스홀더로 정의
   - 커밋됨 ✓

2. **`application-local.yml`** (로컬 개발용)
   - IDE에서 main()을 직접 실행할 때 사용
   - `docker-compose up`으로 띄운 인프라(MySQL, LocalStack)에 localhost로 접속
   - `ddl-auto: update`, 상세 SQL 로깅
   - **모든 값은 플레이스홀더** (`${...}`) - 하드코딩 금지
   - Gradle bootRun/test가 `.env.local`을 로드하여 환경변수 주입
   - **커밋됨** ✓ (더 이상 gitignore 아님)

3. **`application-acceptance.yml`** (인수 테스트용)
   - `docker-compose.acceptance.yml`로 실행되는 앱 전용
   - `ddl-auto: create-drop`, 테스트 독립성 보장
   - 환경 변수로 설정 주입받음 (.env.common + .env.acceptance)
   - 커밋됨 ✓

4. **`application-prod.yml`** (운영용)
   - `docker-compose.demo.yml` 및 실제 ECS 배포 시 사용
   - `ddl-auto: validate`, `logging.level.root: INFO`, `shutdown: graceful`
   - 환경 변수로 설정 주입받음 (.env.common + .env.demo)
   - 커밋됨 ✓

**테스트 프로파일** (`src/test/resources/`):
- **`application-integration.yml`** (통합 테스트용)
  - `@ActiveProfiles("integration")` 사용하는 Spring 통합 테스트용
  - Testcontainers 또는 H2 인메모리 DB 사용
  - docker-compose와 무관
  - 커밋됨 ✓

### Docker Compose 파일

1. **`docker-compose.yml`** (기본 - 인프라만)
   - `docker-compose up` 실행 시 사용
   - **인프라 서비스만 정의** (LocalStack, MySQL)
   - **Spring 서비스는 IDE에서 직접 실행**
   - `.env` 파일 자동 로드
   - 커밋됨 ✓

2. **`docker-compose.override.yml`** (개인화)
   - `docker-compose up` 시 자동으로 병합되어 기본 설정 덮어쓰기
   - 개인용 포트 변경, LocalStack 추가 서비스 등에 사용 가능
   - 현재는 `.example` 템플릿만 제공
   - **gitignore됨** (.gitignore ✓)

3. **`docker-compose.acceptance.yml`** (인수 테스트 환경)
   - `docker-compose -f docker-compose.acceptance.yml up --build`로 실행
   - 자동화된 인수/E2E 테스트 환경
   - Spring 서비스를 **빌드**하여 실행
   - `SPRING_PROFILES_ACTIVE=acceptance` 주입
   - 휘발성 볼륨 (tmpfs) 사용
   - env_file: `.env.local` (LocalStack 토큰) + `.env.common` + `.env.acceptance`
   - **주의**: 로컬에서 실행 시 `.env.local` 필수 (LOCALSTACK_AUTH_TOKEN)
   - 커밋됨 ✓

4. **`docker-compose.demo.yml`** (데모 환경)
   - `docker-compose -f docker-compose.demo.yml up`로 실행
   - 프론트엔드/인프라 담당자용 전체 시스템 데모
   - Spring 서비스를 **DockerHub 이미지**로 실행 (빌드 불필요)
   - `SPRING_PROFILES_ACTIVE=prod` 주입
   - 영구 볼륨 사용 (demo-*)
   - env_file: `.env.local` (LocalStack 토큰) + `.env.common` + `.env.demo`
   - **주의**: 로컬에서 실행 시 `.env.local` 필수 (LOCALSTACK_AUTH_TOKEN)
   - 커밋됨 ✓

## 로컬 개발 환경 설정

### 구조
```
루트/.env.local (gitignore, 모든 로컬 설정 포함)
  ↓ (Gradle이 java-dotenv 라이브러리로 로드)
  ↓
각 서비스/application-local.yml (커밋됨, 플레이스홀더만)
  ↓
Gradle bootRun/test가 환경변수 주입
  ↓
IDE 서비스 실행 (Profile: local)
```

**특징**:
- `.env.local`에 모든 설정 포함 (비밀 + 로컬 전용 + .env.common 내용)
- Spring 직접 실행 시 `.env.local`만 읽으면 됨
- compose 실행 시에는 `.env.common` + `.env.{acceptance|demo}`로 덮어씌워짐

### 초기 설정 (신규 개발자)

**1단계: .env.local 파일 생성**
```bash
# 템플릿 복사
cp .env.local.example .env.local

# 실제 값으로 수정 (ENCRYPTION_KEY, API 키 등)
vi .env.local
```

**2단계: 인프라 실행**
```bash
# MySQL, LocalStack 실행
docker-compose up -d

# LocalStack 초기화 완료 확인 (Cognito 생성)
docker-compose logs localstack | grep "Cognito User Pool created"

# .env 파일에서 생성된 Cognito 값 확인
cat .env | grep COGNITO

# .env.local에 Cognito 값 복사
# COGNITO_USER_POOL_ID=ap-northeast-2_xxxxx
# COGNITO_CLIENT_ID=xxxxx
```

**3단계: Gradle로 서비스 실행**
```bash
# user-service 실행
cd app/backend/user-service
./gradlew bootRun

# 또는 IDE에서 Active Profile을 'local'로 설정 후 실행
# IntelliJ: Run > Edit Configurations > Active profiles: local
```

### Gradle 환경변수 로딩 (java-dotenv)

모든 서비스의 `build.gradle.kts`에 java-dotenv 라이브러리가 설정되어 있습니다:

```kotlin
import io.github.cdimascio.dotenv.Dotenv

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.github.cdimascio:java-dotenv:5.2.2")
    }
}

// 루트 디렉토리 찾기
val rootDir = projectDir.parentFile.parentFile.parentFile

// .env.local 로드 (모든 설정 포함)
val localEnv = Dotenv.configure()
    .directory(rootDir.absolutePath)
    .filename(".env.local")
    .ignoreIfMissing()
    .load()

val envMap = localEnv.entries().associate { it.key to it.value }

tasks.withType<Test> {
    useJUnitPlatform()
    environment(envMap)
}

tasks.named<BootRun>("bootRun") {
    environment(envMap)
}
```

**동작**:
- `./gradlew bootRun` 또는 `./gradlew test` 실행 시 `.env.local` 로드
- `.env.local`에 모든 설정 포함 (비밀 정보 + 로컬 전용 + 공통 설정)
- 모든 환경변수를 Spring Boot에 주입
- IDE에서도 Gradle을 통해 실행하면 동일하게 동작

**주의**:
- `.env.local` 파일이 없으면 실행 실패
- `.env.local.example`을 복사하여 `.env.local` 생성 필요
- LocalStack 재시작 시 Cognito User Pool ID 변경 → `.env.local` 업데이트 필요

### 환경변수 로드 확인

각 서비스에 환경변수 확인용 Gradle 태스크가 추가되어 있습니다:

```bash
# user-service 환경변수 확인
cd app/backend/user-service
./gradlew printEnv

# 출력 예시:
# === Loaded Environment Variables ===
# Total variables loaded: 136
#
# From .env.local:
#   - SQS_USER_TOKEN_REGISTERED_QUEUE: user-token-registered-queue
#   - USER_SERVICE_URL: http://localhost:8081
#   - CANVAS_API_BASE_URL: https://khcanvas.khu.ac.kr/api/v1
#
# Secrets (masked):
#   - ENCRYPTION_KEY: ***SET***
#   - JWT_SECRET: ***SET***
```

각 서비스별 확인 방법:
```bash
# Course Service
cd app/backend/course-service
./gradlew printEnv

# Schedule Service
cd app/backend/schedule-service
./gradlew printEnv

# API Gateway
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
# Gradle이 .env.local을 자동 로드
```

### Acceptance 테스트
```bash
# 사전 준비: .env.local 필요 (LOCALSTACK_AUTH_TOKEN)
# cp .env.local.example .env.local (없는 경우)

# 모든 서비스를 빌드하여 실행
docker-compose -f docker-compose.acceptance.yml up --build

# 정리 (볼륨 포함)
docker-compose -f docker-compose.acceptance.yml down -v
```

### Demo (전체 시스템)
```bash
# 사전 준비: .env.local 필요 (LOCALSTACK_AUTH_TOKEN)
# cp .env.local.example .env.local (없는 경우)

# DockerHub 이미지로 실행 (빌드 불필요)
docker-compose -f docker-compose.demo.yml up

# 정리
docker-compose -f docker-compose.demo.yml down
```

## DDD 구조 예시

### User Service
```
com.unisync.userservice/
├── auth/                   # 인증 도메인
│   ├── controller/         # AuthController
│   ├── service/            # AuthService, CognitoService
│   ├── dto/                # SignUpRequest, SignInRequest, AuthResponse
│   └── exception/
├── credentials/            # Canvas 토큰 관리 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
└── common/
    ├── entity/             # User, Credential
    ├── repository/         # UserRepository, CredentialRepository
    ├── config/             # AwsCognitoConfig, EncryptionConfig
    └── exception/          # GlobalExceptionHandler
```

### Course Service
```
com.unisync.courseservice/
├── course/                 # 과목 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
├── assignment/             # 과제 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
├── enrollment/             # 수강 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
└── common/
    ├── entity/             # Course, Assignment, Enrollment
    ├── repository/
    └── config/
```

### Schedule Service
```
com.unisync.scheduleservice/
├── schedule/               # 일정 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
├── todo/                   # 할일 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
├── category/               # 카테고리 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
└── common/
    ├── entity/             # Schedule, Todo, Category
    ├── repository/
    └── config/
```

## 테스트 구조

### Java 서비스 테스트
각 서비스: `app/backend/{service}/src/test/`

```bash
# 특정 서비스 테스트
cd app/backend/course-service
./gradlew test

# 모든 서비스 테스트 (루트에서)
./gradlew test
```

### 통합 테스트
서비스 간 협업 검증: `tests/integration/`
- `test_assignment_flow.py`: SQS → Service → DB
- `test_assignment_flow_with_lambda.py`
- `test_lambda_integration.py`

### E2E 테스트
전체 플로우: `tests/e2e/`
- `test_canvas_sync_e2e.py`
- `test_canvas_sync_with_jwt_e2e.py`

테스트 실행:
```bash
# 대화형 메뉴
python scripts/test/test-all.py

# E2E 테스트
bash scripts/test/test-e2e.sh
```

자세한 내용: [tests/README.md](../../tests/README.md)

## 주요 변경사항

### 최신 변경 (2025-11-20)

**환경변수 파일 역할 명확화**:
- **`.env`**: docker-compose 인프라 설정 (MySQL, LocalStack 등)
- **`.env.common`**: 컨테이너 공통 앱 설정 (Service URLs, SQS 큐, Canvas URL 등)
- **`.env.local`**: Spring 직접 실행용 모든 설정 (비밀 + 로컬 전용 + .env.common 내용)
- **`.env.acceptance`**: .env.common 오버라이드 (테스트 DB, 더미 키)
- **`.env.demo`**: .env.common 오버라이드 (데모 환경)

**핵심 원칙**:
- **IDE 직접 실행**: `.env.local`만 읽음 (Gradle이 로드, 모든 설정 포함)
- **compose 실행**: `.env.common` + `.env.{acceptance|demo}` 조합
- `.env.local`에 .env.common의 모든 내용 포함 (중복이지만 로컬 실행 시 필요)

**장점**:
- 환경변수 파일 역할 명확화
- Spring 직접 실행 시 .env.local만 있으면 됨
- compose 실행 시 .env.common 기반으로 환경별 오버라이드
- 민감 정보는 .env.local에만 존재 (gitignore)

### 이전 변경사항

**이전 구조 (삭제됨)**:
- `application-local.yml` gitignore
- `application-local.yml.example` 템플릿
- `scripts/dev/sync-local-config.py` 동기화 스크립트
- `.env` 파일 gitignore

**현재 구조**:
- `application-local.yml` 커밋 (플레이스홀더만)
- `.env.local` gitignore (민감 정보)
- `.env` 파일 커밋 (docker-compose 공통 설정)

**장점**:
- 환경변수 관리 통합
- sync 스크립트 불필요
- Gradle bootRun/test 모두 동일한 환경변수 사용
- application-local.yml 템플릿 불필요 (커밋되므로)
