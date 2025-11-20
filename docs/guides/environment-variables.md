# Environment Variables Reference

UniSync 프로젝트의 환경변수 레퍼런스 가이드입니다.

> **프로파일 및 환경변수 파일 구조는 [app/backend/CLAUDE.md](../../app/backend/CLAUDE.md#환경변수-및-프로파일-관리)를 참고하세요.**
> 이 문서는 각 환경변수의 의미와 설정 값만 다룹니다.

---

## 📋 환경변수 분류

| 분류 | 설명 | 보안 수준 |
|------|------|----------|
| **인프라** | MySQL, LocalStack, SQS 엔드포인트 | 🟢 Public |
| **인증** | Cognito, JWT | 🔴 Secret |
| **암호화** | AES-256 키 | 🔴 Secret |
| **외부 API** | Canvas, LLM | 🔴 Secret |
| **내부 API** | 서비스 간 인증 | 🔴 Secret |

---

## 🔐 Secret 환경변수 (`.env.local`, Secrets Manager)

### 인증 및 암호화

| 변수 | 설명 | 생성 방법 | 예시 |
|------|------|----------|------|
| `ENCRYPTION_KEY` | Canvas 토큰 AES-256 암호화 키 (32 bytes) | `openssl rand -base64 32` | `kJ8n3vN2...` |
| `JWT_SECRET` | JWT 서명 키 (선택, Cognito 사용 시 불필요) | `openssl rand -base64 64` | `xR9mK7...` |
| `COGNITO_USER_POOL_ID` | AWS Cognito User Pool ID | LocalStack 초기화 시 자동 생성 | `ap-northeast-2_abc123` |
| `COGNITO_CLIENT_ID` | AWS Cognito Client ID | LocalStack 초기화 시 자동 생성 | `4f8n2k...` |

### 외부 API 및 인프라

| 변수 | 설명 | 발급처 | 예시 |
|------|------|--------|------|
| `LOCALSTACK_AUTH_TOKEN` | LocalStack Pro 라이선스 토큰 | [LocalStack Dashboard](https://app.localstack.cloud/) | `ls-xxxxx-...` |
| `CANVAS_API_TOKEN` | Canvas LMS API 토큰 (테스트용) | Canvas → Settings → New Access Token | `1234~abcd...` |
| `CANVAS_SYNC_API_KEY` | Canvas Sync Lambda 호출용 API 키 | 직접 생성 (UUID 권장) | `sync-api-key-...` |
| `LLM_API_KEY` | LLM API 키 (OpenAI 등) | OpenAI Dashboard | `sk-proj-...` |

### 내부 서비스 인증

| 변수 | 설명 | 생성 방법 | 예시 |
|------|------|----------|------|
| `SERVICE_AUTH_TOKEN` | Lambda → User-Service 내부 API 인증 | 직접 생성 | `internal-service-token-...` |

### 데이터베이스 비밀번호

| 변수 | 설명 | 로컬 | 프로덕션 |
|------|------|------|----------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 비밀번호 | `root_password` | 강력한 비밀번호 |
| `MYSQL_PASSWORD` | 애플리케이션 DB 사용자 비밀번호 | `unisync_password` | 강력한 비밀번호 |

---

## 🟢 Public 환경변수 (`.env`, `.env.common`)

### 데이터베이스 설정

| 변수 | 설명 | 로컬 | 프로덕션 (ECS) |
|------|------|------|----------------|
| `MYSQL_HOST` | MySQL 호스트 | `localhost` | RDS 엔드포인트 |
| `MYSQL_PORT` | MySQL 포트 | `3306` | `3306` |
| `MYSQL_USER` | 애플리케이션 DB 사용자 | `unisync` | `unisync` |
| `USER_DB_NAME` | User-Service DB 이름 | `user_db` | `user_db` |
| `COURSE_DB_NAME` | Course-Service DB 이름 | `course_db` | `course_db` |
| `SCHEDULE_DB_NAME` | Schedule-Service DB 이름 | `schedule_db` | `schedule_db` |

### AWS 인프라 설정

| 변수 | 설명 | 로컬 | 프로덕션 |
|------|------|------|----------|
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` | `ap-northeast-2` |
| `AWS_ENDPOINT_OVERRIDE` | LocalStack 엔드포인트 | `http://localhost:4566` | (미설정) |
| `SQS_ENDPOINT` | SQS 엔드포인트 | `http://localhost:4566` | (미설정, AWS 기본값) |

### SQS 큐 이름

| 변수 | 설명 | 값 (모든 환경 동일) |
|------|------|---------------------|
| `SQS_ASSIGNMENT_EVENTS_QUEUE` | Assignment 이벤트 큐 | `assignment-events-queue` |
| `SQS_SUBMISSION_EVENTS_QUEUE` | Submission 이벤트 큐 | `submission-events-queue` |
| `SQS_TASK_CREATION_QUEUE` | Task 생성 큐 | `task-creation-queue` |
| `SQS_LLM_ANALYSIS_QUEUE` | LLM 분석 요청 큐 | `llm-analysis-queue` |
| `SQS_USER_TOKEN_REGISTERED_QUEUE` | 사용자 토큰 등록 큐 | `user-token-registered-queue` |

### 서비스 URL

| 변수 | 설명 | 로컬 | Docker Compose | ECS |
|------|------|------|----------------|-----|
| `USER_SERVICE_URL` | User-Service URL | `http://localhost:8081` | `http://user-service:8081` | Private DNS |
| `COURSE_SERVICE_URL` | Course-Service URL | `http://localhost:8082` | `http://course-service:8082` | Private DNS |
| `SCHEDULE_SERVICE_URL` | Schedule-Service URL | `http://localhost:8083` | `http://schedule-service:8083` | Private DNS |

### 외부 API 엔드포인트

| 변수 | 설명 | 값 |
|------|------|-----|
| `CANVAS_BASE_URL` | Canvas API Base URL | `https://khcanvas.khu.ac.kr/api/v1` |
| `LLM_API_URL` | LLM API URL | `https://api.openai.com/v1/chat/completions` |

---

## 🌍 환경별 설정

### 환경변수 파일 구조 개요

| 파일 | 역할 | 커밋 여부 | 사용 환경 |
|------|------|----------|----------|
| `.env` | docker-compose 인프라 설정 (MySQL, LocalStack) | ✅ 커밋 | 모든 환경 |
| `.env.common` | 컨테이너 공통 앱 설정 (Service URLs, SQS 큐 등) | ✅ 커밋 | compose 실행 시 |
| `.env.local` | 로컬 개발 모든 설정 (비밀 + 로컬 전용 + 공통) | ❌ gitignore | IDE 직접 실행 + compose |
| `.env.acceptance` | acceptance 테스트 특화 오버라이드 | ✅ 커밋 | acceptance 환경 |
| `.env.demo` | 데모 환경 특화 오버라이드 | ✅ 커밋 | demo 환경 |

**핵심 원칙:**
- **IDE 직접 실행**: Gradle이 `.env.local`만 읽음 (모든 설정 포함)
- **compose 실행**: `env_file: [.env.local, .env.common, .env.{acceptance|demo}]` 순서로 로드
- `.env.local`에 `.env.common`의 모든 내용 포함 (로컬 실행 시 필요)

### Local (IDE 개발)
**파일**: `.env.local` (단독 사용)
**프로파일**: `local`
**특징**:
- Spring 직접 실행 시 Gradle이 `.env.local`만 로드
- MySQL, LocalStack: localhost로 접속
- 모든 서비스: localhost 포트 (8080-8083)
- 상세 로깅 활성화

**`.env.local` 구성**:
```bash
# ===== 비밀 정보 =====
LOCALSTACK_AUTH_TOKEN=ls-xxxxx
JWT_SECRET=xxx
ENCRYPTION_KEY=xxx
CANVAS_API_TOKEN=xxx
CANVAS_SYNC_API_KEY=xxx

# Cognito (LocalStack이 자동 업데이트)
COGNITO_USER_POOL_ID=ap-northeast-2_xxx
COGNITO_CLIENT_ID=xxx
COGNITO_REGION=ap-northeast-2

# ===== 로컬 전용 오버라이드 =====
COGNITO_ENDPOINT=http://localhost:4566
USER_SERVICE_URL=http://localhost:8081
COURSE_SERVICE_URL=http://localhost:8082
SCHEDULE_SERVICE_URL=http://localhost:8083
AWS_SQS_ENDPOINT=http://localhost:4566

# 로컬 DB
USER_SERVICE_DATABASE_URL=jdbc:mysql://localhost:3307/user_db?...
# ... 나머지 DB URL들

# ===== 공통 설정 (.env.common과 동일) =====
SQS_USER_TOKEN_REGISTERED_QUEUE=user-token-registered-queue
CANVAS_API_BASE_URL=https://khcanvas.khu.ac.kr/api/v1
# ... 나머지 공통 설정들
```

### Acceptance (자동화 테스트)
**파일**: `.env.local` → `.env.common` → `.env.acceptance` (순서대로 덮어쓰기)
**프로파일**: `acceptance`
**특징**:
- DDL: `create-drop` (테스트 독립성)
- 휘발성 볼륨 (테스트 후 삭제)
- 컨테이너 네트워크 호스트명 사용 (`http://user-service:8081`)
- **로컬에서 실행 시**: `.env.local` 필수 (LOCALSTACK_AUTH_TOKEN, COGNITO_* 등)

**`.env.acceptance` 오버라이드 예시**:
```bash
# 테스트 DB 격리
USER_SERVICE_DB_NAME=user_db_test
USER_SERVICE_DATABASE_URL=jdbc:mysql://mysql:3306/user_db_test?...

# 테스트용 더미 키
ENCRYPTION_KEY=IKhYmJOSJU6I9wLqNhfjr06vg6PeUsXLIRpwbcw3EF8=
JWT_SECRET=test-jwt-secret-key-for-acceptance-tests
```

**실행**:
```bash
# .env.local 필수 (LOCALSTACK_AUTH_TOKEN, COGNITO_* 등)
docker-compose -f docker-compose.acceptance.yml up --build
```

### Demo (전체 시스템 데모)
**파일**: `.env.local` → `.env.common` → `.env.demo` (순서대로 덮어쓰기)
**프로파일**: `prod`
**특징**:
- DDL: `validate` (운영 모드)
- 영구 볼륨 사용
- DockerHub 이미지 실행
- **로컬에서 실행 시**: `.env.local` 필수

**`.env.demo` 오버라이드 예시**:
```bash
# 데모용 DB (컨테이너 네트워크)
USER_SERVICE_DATABASE_URL=jdbc:mysql://mysql:3306/user_db?...

# 데모용 더미 키 (실제 값으로 교체 필요)
ENCRYPTION_KEY=demo-base64-encoded-encryption-key-here
JWT_SECRET=demo-jwt-secret-key-change-this
```

**실행**:
```bash
# .env.local 필수 (LOCALSTACK_AUTH_TOKEN)
docker-compose -f docker-compose.demo.yml up
```

### Production (ECS)
**파일**: Secrets Manager + 환경변수 주입
**프로파일**: `prod`
**특징**:
- RDS 엔드포인트
- AWS 관리형 SQS
- Secrets Manager에서 Secret 주입

**ECS 태스크 정의 환경변수 예시**:
```json
{
  "environment": [
    {
      "name": "SPRING_PROFILES_ACTIVE",
      "value": "prod"
    },
    {
      "name": "MYSQL_HOST",
      "value": "unisync-mysql.xxxxx.ap-northeast-2.rds.amazonaws.com"
    },
    {
      "name": "AWS_REGION",
      "value": "ap-northeast-2"
    }
  ],
  "secrets": [
    {
      "name": "ENCRYPTION_KEY",
      "valueFrom": "arn:aws:secretsmanager:...:secret:unisync/encryption-key"
    },
    {
      "name": "MYSQL_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:...:secret:unisync/rds-password"
    }
  ]
}
```

---

## 🔍 환경변수 검증

### 로컬 환경변수 로드 테스트
각 서비스에 환경변수 로드 확인 테스트가 포함되어 있습니다:

```bash
cd app/backend/user-service
./gradlew test --tests EnvironmentVariablesTest

# 성공 시 출력:
# [OK] 환경변수 로드 성공:
#   - ENCRYPTION_KEY: ***
#   - COGNITO_USER_POOL_ID: ap-northeast-2_xxxxx
```

### 필수 환경변수 누락 시 동작
- **로컬**: Gradle bootRun 실패, 명확한 오류 메시지
- **Docker Compose**: 컨테이너 시작 실패, `docker-compose logs` 확인
- **ECS**: 태스크 시작 실패, CloudWatch Logs 확인

---

## 🛠️ 환경변수 설정 가이드

### 1단계: 템플릿 복사
```bash
# 로컬 개발용
cp .env.local.example .env.local
```

### 2단계: 필수 값 입력

**ENCRYPTION_KEY 생성**:
```bash
openssl rand -base64 32
# 출력된 값을 .env.local에 복사
```

**Cognito 값 확인** (LocalStack 실행 후):
```bash
# LocalStack 초기화 로그 확인
docker-compose logs localstack | grep "Cognito User Pool"

# .env 파일에서 생성된 값 확인
cat .env | grep COGNITO

# .env.local에 복사
```

**Canvas API 토큰 발급**:
1. Canvas LMS 로그인
2. **Account** → **Settings**
3. **Approved Integrations** → **+ New Access Token**
4. Purpose: "UniSync Development"
5. 생성된 토큰을 `.env.local`에 복사

### 3단계: 환경변수 로드 확인
```bash
# 서비스 실행하여 환경변수 로드 확인
cd app/backend/user-service
./gradlew bootRun

# 성공 시 다음과 유사한 로그 출력:
# Loaded environment variable: ENCRYPTION_KEY=***
# Loaded environment variable: COGNITO_USER_POOL_ID=ap-northeast-2_xxxxx
```

---

## ⚠️ 보안 주의사항

### 절대 금지
- ❌ `.env.local` 파일 커밋 (gitignore 확인)
- ❌ `application-local.yml`에 실제 값 하드코딩 (플레이스홀더만)
- ❌ Secret 환경변수를 로그에 출력
- ❌ 프로덕션 Secret을 로컬/테스트 환경에서 사용

### 권장사항
- ✅ Secret 환경변수는 Secrets Manager 사용 (프로덕션)
- ✅ 환경별로 다른 API 키 사용 (로컬/테스트/프로덕션 분리)
- ✅ ENCRYPTION_KEY 정기적 로테이션
- ✅ `.env.local.example`에는 플레이스홀더만 작성

---

## 📚 참고 문서

- [Backend 환경 설정](../../app/backend/CLAUDE.md#환경변수-및-프로파일-관리) - 프로파일 및 파일 구조 상세
- [배포 가이드](./deployment.md) - 프로덕션 환경변수 설정
- [Serverless 환경변수](../../app/serverless/README.md#환경-변수) - Lambda 함수 환경변수
