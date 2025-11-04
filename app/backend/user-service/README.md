# User Service

## 서비스 책임

1. **사용자 인증** - AWS Cognito 기반 회원가입/로그인, JWT 발급
2. **Canvas 토큰 관리** - Canvas API 토큰 AES-256 암호화 저장 및 조회
3. **사용자 프로필** - 사용자 기본 정보 관리

**포트**: 8081 | **API Gateway 라우팅**: `/api/v1/auth/**`, `/api/v1/users/**`

---

## 도메인 구조

```
com.unisync.user/
├── auth/              # 인증 (Cognito 회원가입/로그인)
├── user/              # 사용자 프로필
├── credentials/       # Canvas 토큰 관리 (등록/조회/삭제)
└── common/
    ├── entity/        # User, Credentials
    ├── repository/
    ├── config/        # AwsCognitoConfig, EncryptionService
    └── exception/
```

---

## 데이터 모델

### User
| 필드 | 타입 | 제약조건 |
|------|------|----------|
| id | Long | PK |
| email | String | UNIQUE, NOT NULL |
| name | String | NOT NULL |
| cognito_sub | String | UNIQUE, NOT NULL (Cognito User ID) |
| is_active | Boolean | DEFAULT true |

### Credentials
Canvas API 토큰을 **AES-256으로 암호화**하여 저장합니다.

| 필드 | 타입 | 제약조건 |
|------|------|----------|
| id | Long | PK |
| user_id | Long | FK → users.id |
| provider | Enum | CANVAS, GOOGLE_CALENDAR |
| encrypted_token | String | NOT NULL (AES-256 암호화) |
| last_validated_at | LocalDateTime | nullable |

**UNIQUE**: `(user_id, provider)`

---

## 주요 비즈니스 로직

### 1. Canvas 토큰 등록 (`CredentialsService`)

**처리 흐름**:
```
1. Canvas API 호출하여 토큰 유효성 검증
   GET https://canvas.instructure.com/api/v1/users/self
   Authorization: Bearer {token}

2. 검증 성공 시 AES-256-GCM으로 암호화
   - 키: 환경변수 CANVAS_TOKEN_ENCRYPTION_KEY (32 bytes)
   - IV: 랜덤 생성 (각 토큰마다)

3. Credentials 테이블에 저장 (provider=CANVAS)
```

**실패 케이스**:
- 토큰 무효: `InvalidCanvasTokenException`
- 중복 등록: 기존 레코드 UPDATE

**구현**: `app/backend/user-service/src/main/java/com/unisync/user/credentials/service/CredentialsService.java:1`

### 2. Canvas 토큰 조회 (내부 API)

Lambda/Service가 사용자의 Canvas 토큰을 조회할 때 사용합니다.

**권한 검증**:
- **사용자**: `X-User-Id` 헤더로 본인 확인
- **내부 서비스**: `X-Api-Key` 헤더로 서비스 인증 (ServiceAuthValidator)

**응답**: 복호화된 평문 토큰 반환

---

## 주요 API

### POST `/api/v1/credentials/canvas` - Canvas 토큰 등록
```http
X-User-Id: 1
{
  "canvasToken": "1234~abcdefghijklmnopqrstuvwxyz"
}
```

### GET `/api/v1/credentials/{userId}/canvas` - Canvas 토큰 조회
```http
X-Api-Key: service-internal-key
```

**Response**:
```json
{
  "userId": 1,
  "canvasToken": "1234~abcdefghijklmnopqrstuvwxyz",
  "lastValidatedAt": "2025-11-05T10:30:00"
}
```

---

## 외부 의존성

### 1. AWS Cognito
- 회원가입: `SignUp`
- 로그인: `InitiateAuth` → JWT 발급

### 2. Canvas LMS API
- 토큰 검증: `GET /api/v1/users/self`
- Base URL: `CANVAS_BASE_URL` 환경변수

---

## 필수 환경변수

| 변수 | 설명 | 예시 |
|------|------|------|
| `AWS_COGNITO_USER_POOL_ID` | Cognito Pool ID | `ap-northeast-2_xxx` |
| `AWS_COGNITO_CLIENT_ID` | Cognito Client ID | `1234567890abcd...` |
| `CANVAS_BASE_URL` | Canvas LMS URL | `https://khcanvas.khu.ac.kr` |
| `CANVAS_TOKEN_ENCRYPTION_KEY` | AES-256 키 (32 bytes) | `0123456789abcdef...` (64 hex chars) |

**키 생성**:
```bash
openssl rand -hex 32
```

---

## 중요 제약사항

### 절대 금지
1. Canvas 토큰 평문 저장 - 반드시 AES-256 암호화
2. 다른 사용자 Credentials 접근 - 권한 검증 필수
3. 암호화 키 하드코딩 - 환경변수로만 관리

### 핵심 원칙
1. Canvas 토큰 등록 시 **반드시 Canvas API 호출하여 검증**
2. 내부 API 호출 시 `X-Api-Key` 헤더 필수
3. JWT `X-User-Id`와 요청 파라미터 `userId` 일치 여부 확인