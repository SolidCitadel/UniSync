# API Gateway

## 서비스 책임

1. **단일 진입점** - 모든 클라이언트 요청의 진입점
2. **JWT 인증** - Cognito JWT 검증 및 사용자 정보 추출
3. **라우팅** - 경로 기반 백엔드 서비스 라우팅
4. **헤더 주입** - JWT에서 추출한 사용자 정보를 백엔드 서비스로 전달

**포트**: 8080

---

## 라우팅 규칙

| 경로 | 대상 서비스 | 포트 |
|------|-------------|------|
| `/api/v1/auth/**` | User Service | 8081 |
| `/api/v1/users/**` | User Service | 8081 |
| `/api/v1/friends/**` | User Service | 8081 |
| `/api/v1/courses/**` | Course Service | 8082 |
| `/api/v1/assignments/**` | Course Service | 8082 |
| `/api/v1/tasks/**` | Course Service | 8082 |
| `/api/v1/schedules/**` | Schedule Service | 8083 |

---

## JWT 인증 처리

### 1. 인증 흐름 (`JwtAuthenticationFilter`)

```
1. Authorization 헤더 추출
   Authorization: Bearer {jwt_token}

2. JWT 검증 제외 경로 확인
   - /api/v1/auth/signup
   - /api/v1/auth/signin
   - /api/v1/auth/health
   제외 경로는 필터를 스킵하고 바로 라우팅

3. JWT 토큰 검증 (CognitoJwtVerifier)
   - 토큰 형식 검증 (3-part JWT)
   - Claims 파싱
   - LocalStack 환경: 서명 검증 스킵
   - AWS 환경: JWKS Public Key로 서명 검증 (TODO)

4. JWT Claims에서 사용자 정보 추출
   - sub: Cognito User ID (UUID)
   - email: 사용자 이메일
   - name: 사용자 이름

5. 백엔드 서비스로 전달할 헤더 추가
   - X-Cognito-Sub: {cognito_sub}
   - X-User-Email: {email}
   - X-User-Name: {name}

6. 라우팅
```

**구현**: `app/backend/api-gateway/src/main/java/com/unisync/gateway/filter/JwtAuthenticationFilter.java:1`

### 2. JWT 검증 (`CognitoJwtVerifier`)

**LocalStack 환경** (개발):
- 서명 검증 스킵
- JWT Payload만 Base64 디코딩하여 Claims 추출

**AWS 환경** (프로덕션):
- Cognito JWKS 엔드포인트에서 Public Key 다운로드
- Public Key로 JWT 서명 검증 (TODO: 구현 필요)

**구현**: `app/backend/api-gateway/src/main/java/com/unisync/gateway/service/CognitoJwtVerifier.java:1`

---

## 백엔드 서비스 통합

### 주입되는 헤더

백엔드 서비스는 다음 헤더를 통해 사용자 정보를 받습니다:

| 헤더 | 설명 | 예시 |
|------|------|------|
| `X-Cognito-Sub` | Cognito User ID (UUID) | `550e8400-e29b-41d4-a716-446655440000` |
| `X-User-Email` | 사용자 이메일 | `student@example.com` |
| `X-User-Name` | 사용자 이름 | `홍길동` |

**백엔드 서비스 사용 예시**:
```java
@GetMapping("/profile")
public ResponseEntity<UserResponse> getProfile(
    @RequestHeader("X-Cognito-Sub") String cognitoSub
) {
    // cognitoSub으로 User 조회
    User user = userRepository.findByCognitoSub(cognitoSub)
        .orElseThrow(() -> new UserNotFoundException());

    return ResponseEntity.ok(toResponse(user));
}
```

### 주의사항

1. **백엔드 서비스는 JWT를 직접 파싱하지 않음**
   - Gateway가 이미 검증하고 헤더로 전달
   - 헤더 값을 신뢰하고 사용

2. **내부 서비스 간 호출**
   - Gateway를 거치지 않는 서비스 간 호출은 `X-Api-Key` 사용
   - 예: Lambda → User Service (Canvas 토큰 조회)

---

## JWT 검증 제외 경로

인증 없이 접근 가능한 경로입니다.

```yaml
jwt:
  exclude-paths:
    - /api/v1/auth/signup
    - /api/v1/auth/signin
    - /api/v1/auth/health
    - /actuator/**
```

---

## 필수 환경변수

| 변수 | 설명 | 예시 |
|------|------|------|
| `AWS_COGNITO_USER_POOL_ID` | Cognito User Pool ID | `ap-northeast-2_xxx` |
| `AWS_COGNITO_REGION` | AWS 리전 | `ap-northeast-2` |
| `AWS_COGNITO_ENDPOINT` | LocalStack 엔드포인트 (개발용) | `http://localhost:4566` |
| `USER_SERVICE_URL` | User Service URL | `http://localhost:8081` |
| `COURSE_SERVICE_URL` | Course Service URL | `http://localhost:8082` |
| `SCHEDULE_SERVICE_URL` | Schedule Service URL | `http://localhost:8083` |

---

## 에러 응답

### 401 Unauthorized

**케이스 1**: Authorization 헤더 누락
```json
{
  "error": "Authorization 헤더가 필요합니다"
}
```

**케이스 2**: 토큰 형식 오류
```json
{
  "error": "유효하지 않은 토큰 형식입니다"
}
```

**케이스 3**: JWT 검증 실패
```json
{
  "error": "토큰 검증에 실패했습니다: {reason}"
}
```

---

## 핵심 원칙

1. **Gateway는 인증만 담당, 인가는 백엔드 서비스**
   - JWT 검증: Gateway
   - 리소스 권한 확인: 각 백엔드 서비스

2. **백엔드 서비스는 헤더를 신뢰**
   - Gateway를 통한 요청만 허용
   - 직접 백엔드 포트 노출 금지

3. **JWT 검증 제외 경로 최소화**
   - 회원가입/로그인만 허용
   - 나머지 모든 API는 인증 필수

---

## TODO

1. **프로덕션 JWT 검증 구현**
   - Cognito JWKS 엔드포인트에서 Public Key 다운로드
   - Public Key로 JWT 서명 검증
   - Key rotation 대응

2. **Rate Limiting**
   - 사용자별 요청 제한

3. **CORS 설정**
   - 프론트엔드 도메인 허용 설정