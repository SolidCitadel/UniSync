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

### Spring 프로파일 (application-*.yml)

각 서비스의 `src/main/resources/`에 다음 프로파일 파일들이 있습니다:

1. **`application.yml`** (공통)
   - 모든 프로파일이 공유하는 기본값
   - 민감 정보는 `${...}` 플레이스홀더로 정의
   - 커밋됨 ✓

2. **`application-local.yml`** (로컬 개발용)
   - IDE에서 main()을 직접 실행할 때 사용
   - `docker-compose up`으로 띄운 인프라(MySQL, LocalStack)에 localhost로 접속
   - `ddl-auto: update`, 상세 SQL 로깅, 민감 정보 하드코딩
   - **반드시 .gitignore에 추가됨** (.gitignore ✓)

3. **`application-acceptance.yml`** (인수 테스트용)
   - `docker-compose.acceptance.yml`로 실행되는 앱 전용
   - `ddl-auto: create-drop`, 테스트 독립성 보장
   - 환경 변수로 설정 주입받음
   - 커밋됨 ✓

4. **`application-prod.yml`** (운영용)
   - `docker-compose.yml` (로컬 운영환경) 및 실제 ECS 배포 시 사용
   - `ddl-auto: validate`, `logging.level.root: INFO`, `shutdown: graceful`
   - 환경 변수로 설정 주입받음
   - 커밋됨 ✓

**테스트 프로파일** (`src/test/resources/`):
- **`application-integration.yml`** (통합 테스트용)
  - `@ActiveProfiles("integration")` 사용하는 Spring 통합 테스트용
  - Testcontainers 또는 H2 인메모리 DB 사용
  - docker-compose와 무관
  - 커밋됨 ✓

### Docker Compose 파일

1. **`docker-compose.yml`** (기본 로컬 운영 환경)
   - `docker-compose up` 실행 시 사용
   - 모든 서비스 정의 (LocalStack, MySQL, 백엔드 서비스들)
   - `SPRING_PROFILES_ACTIVE=prod` 주입
   - Docker 내부 네트워크용 엔드포인트 및 민감 정보를 환경 변수로 주입
   - 커밋됨 ✓

2. **`docker-compose.override.yml`** (개인화)
   - `docker-compose up` 시 자동으로 병합되어 기본 설정 덮어쓰기
   - 개인용 포트 변경, 로컬 코드 볼륨 마운트 등
   - **반드시 .gitignore에 추가됨** (.gitignore ✓)

3. **`docker-compose.acceptance.yml`** (인수 테스트 환경)
   - `docker-compose -f docker-compose.acceptance.yml up`으로 실행
   - 자동화된 인수/E2E 테스트 환경
   - `SPRING_PROFILES_ACTIVE=acceptance` 주입
   - 테스트용 휘발성 DB (tmpfs 볼륨 권장)
   - 커밋됨 ✓

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
# 1. 인프라 실행 (MySQL, LocalStack)
docker-compose up -d mysql localstack

# 2. 환경변수 동기화 (application-local.yml 자동 생성/업데이트)
python scripts/dev/sync-local-config.py

# 3. IDE에서 Active Profile을 'local'로 설정 후 서비스 실행
```

**동기화 스크립트** (`sync-local-config.py`):
- 루트 `.env` → 각 서비스 `application-local.yml` 자동 업데이트
- Cognito User Pool ID/Client ID, MySQL 비밀번호, 암호화 키, SQS 큐 이름, API 키, Canvas Base URL
- YAML 형식과 주석 유지

**주의**:
- `application-local.yml`은 gitignored (커밋 안됨)
- LocalStack 재시작시 User Pool ID 변경될 수 있음 → `sync-local-config.py` 재실행
- `.env` 파일도 gitignored (민감 정보 포함)

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
