# User Service

사용자 인증 및 Canvas 토큰 관리 서비스입니다.

> **상세 설계는 다음 문서를 참고하세요:**
> - [시스템 아키텍처](../../../docs/design/system-architecture.md) - 전체 데이터 모델 및 API 설계
> - [Canvas 동기화](../../../docs/features/canvas-sync.md) - Canvas 토큰 관리 및 암호화 전략

## 서비스 책임

1. **사용자 인증** - AWS Cognito 기반 회원가입/로그인, JWT 발급
2. **Canvas 토큰 관리** - Canvas API 토큰 AES-256 암호화 저장 및 조회
3. **사용자 프로필** - 사용자 기본 정보 관리
4. **Canvas 동기화** - POST /v1/sync/canvas 엔드포인트로 Lambda 직접 호출

**포트**: 8081

**API Gateway 라우팅**:
- `/api/v1/auth/**` - 인증
- `/api/v1/users/**` - 사용자 프로필
- `/api/v1/credentials/**` - Canvas 토큰
- `/api/v1/sync/**` - Canvas 동기화

---

## 빠른 시작

### 로컬 실행

```bash
cd app/backend/user-service
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 테스트

```bash
# 단위 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests CanvasSyncServiceTest
```

---

## 도메인 구조

```
com.unisync.user/
├── auth/              # 인증 (Cognito 회원가입/로그인)
├── user/              # 사용자 프로필
├── credentials/       # Canvas 토큰 관리 (등록/조회/삭제)
├── sync/              # Canvas 동기화 (Lambda 호출)
└── common/
    ├── entity/        # User, Credentials
    ├── repository/
    ├── config/        # AwsCognitoConfig, EncryptionService, AwsLambdaConfig
    └── exception/
```

---

## 필수 환경변수

환경변수 전체 목록 및 설정 방법은 [app/backend/CLAUDE.md](../CLAUDE.md)를 참고하세요.

**주요 변수**:
- `AWS_COGNITO_USER_POOL_ID` - Cognito Pool ID
- `AWS_COGNITO_CLIENT_ID` - Cognito Client ID
- `CANVAS_API_BASE_URL` - Canvas LMS URL
- `ENCRYPTION_KEY` - AES-256 암호화 키 (32 bytes, `openssl rand -base64 32`로 생성)
- `CANVAS_SYNC_LAMBDA_FUNCTION_NAME` - Canvas Sync Lambda 함수명
- `AWS_LAMBDA_ENDPOINT_URL` - Lambda 엔드포인트 (LocalStack: http://localstack:4566)

---

## 주요 API

### 인증
- `POST /api/v1/auth/signup` - 회원가입
- `POST /api/v1/auth/signin` - 로그인 (JWT 발급)

### Canvas 토큰
- `POST /api/v1/credentials/canvas` - Canvas 토큰 등록
- `GET /api/v1/credentials/canvas` - Canvas 토큰 조회 (본인)

### Canvas 동기화
- `POST /api/v1/sync/canvas` - Canvas 수동 동기화 (Lambda 호출)

**내부 API** (서비스 간 통신):
- `GET /internal/v1/credentials/canvas/by-cognito-sub/{cognitoSub}` - Canvas 토큰 조회 (X-Api-Key 인증)

---

## 참고 문서

- [전체 시스템 아키텍처](../../../docs/design/system-architecture.md)
- [Canvas 동기화 설계](../../../docs/features/canvas-sync.md)
- [백엔드 환경변수 가이드](../CLAUDE.md)
- [테스트 전략](../../../docs/features/testing-strategy.md)
