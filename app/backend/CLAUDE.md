# Backend Services

백엔드 마이크로서비스 공통 설정 및 구조.

## 서비스 포트
- **API Gateway**: 8080 (모든 요청 진입점, JWT 인증)
- User-Service: 8081
- Course-Service: 8082
- Schedule-Service: 8083

## API Gateway 라우팅
API Gateway는 `/api/v1` prefix 제거 후 백엔드 서비스로 전달:

```
# User-Service
/api/v1/auth/**        → /auth/**
/api/v1/users/**       → /users/**
/api/v1/friends/**     → /friends/**
/api/v1/groups/**      → /groups/**

# Course-Service
/api/v1/courses/**     → /courses/**
/api/v1/assignments/** → /assignments/**
/api/v1/notices/**     → /notices/**

# Schedule-Service
/api/v1/schedules/**   → /schedules/**
/api/v1/todos/**       → /todos/**
/api/v1/categories/**  → /categories/**
```

인증 예외 (JWT 불필요): `/api/v1/auth/register`, `/api/v1/auth/login`

백엔드 서비스 엔드포인트는 환경변수로 주입 (로컬/Docker/ECS 환경별 상이).

## 환경변수 및 프로파일 관리

### 프로파일 구분
- **`local`**: 로컬 개발용, `application-local.yml` 하드코딩 (gitignored)
- **`docker`**: Docker Compose, 환경변수 주입
- **`test`**: 테스트용, H2 인메모리 DB

### 로컬 개발 환경 설정

**구조**:
```
루트/.env (gitignored)
  ↓ (LocalStack 초기화 스크립트 자동 업데이트)
  ↓ (sync-local-config.py로 동기화)
  ↓
각 서비스/application-local.yml (gitignored, 하드코딩)
  ↓
IDE 서비스 실행 (Profile: local)
```

**초기 설정** (신규 개발자):
```bash
# 1. 각 서비스별 application-local.yml 생성
cd app/backend/{service}/src/main/resources
cp application-local.yml.example application-local.yml

# 2. LocalStack 실행
docker-compose up -d

# 3. 환경변수 동기화
python scripts/dev/sync-local-config.py

# 4. IDE에서 Active Profile을 'local'로 설정 후 서비스 실행
```

**동기화 스크립트** (`sync-local-config.py`):
- 루트 `.env` → 각 서비스 `application-local.yml` 자동 업데이트
- Cognito User Pool ID/Client ID, MySQL 비밀번호, 암호화 키, SQS 큐 이름, API 키, Canvas Base URL
- YAML 형식과 주석 유지

**주의**:
- `application-local.yml`은 gitignored (커밋 안됨)
- LocalStack 재시작시 User Pool ID 변경될 수 있음 → `sync-local-config.py` 재실행
- `.env` 파일도 gitignored (민감 정보 포함)

### Docker/배포 환경
환경변수로 주입:
```yaml
# docker-compose.app.yml
user-service:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - USER_SERVICE_DATABASE_URL=jdbc:mysql://mysql:3306/user_db?...
    - COGNITO_USER_POOL_ID=${COGNITO_USER_POOL_ID}
```

## DDD 구조 예시

### User Service
```
com.unisync.user/
├── auth/                   # 인증 도메인
│   ├── controller/         # AuthController
│   ├── service/            # AuthService, CognitoService
│   ├── dto/                # SignUpRequest, SignInRequest, AuthResponse
│   └── exception/
├── profile/                # 프로필 도메인
├── credentials/            # Canvas 토큰 관리 도메인
└── common/
    ├── entity/             # User
    ├── repository/         # UserRepository
    └── config/             # AwsCognitoConfig
```

### Course Service
```
com.unisync.course/
├── course/                 # 과목 도메인
│   ├── controller/
│   ├── service/
│   └── dto/
├── assignment/             # 과제 도메인
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
com.unisync.schedule/
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

# 모든 서비스 테스트
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
