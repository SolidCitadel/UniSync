# Tests

UniSync 프로젝트의 통합 테스트 모음입니다.

## 테스트 구조

```
tests/
├── api/                      # 외부 API 테스트
│   └── test_canvas_api.py    # Canvas API 직접 호출 테스트
├── integration/              # 서비스 간 통합 테스트 (✅ Phase 1 완료)
│   ├── test_assignment_flow.py           # SQS → Service → DB 흐름
│   ├── test_canvas_sync_integration.py   # Lambda → Canvas API → SQS → Service → DB (6 tests)
│   └── test_lambda_integration.py        # LocalStack Lambda 배포/호출
├── e2e/                      # End-to-End 테스트 (향후 추가 예정)
├── fixtures/                 # 테스트 데이터
├── requirements.txt          # 테스트 의존성
└── README.md                 # 이 파일
```

## 테스트 실행

### 실행 스크립트 (scripts/test/)

모든 테스트는 `scripts/test/` 디렉토리의 실행 스크립트를 통해 실행합니다:

- `test-all.py` - 대화형 메뉴로 모든 테스트 실행
- `test-unit.sh/bat` - Lambda 단위 테스트 실행
- `test-e2e.sh/bat` - E2E 테스트 실행

**예시:**
```bash
# 대화형 메뉴
python scripts/test/test-all.py

# 또는 직접 실행
bash scripts/test/test-unit.sh          # Lambda 단위 테스트
bash scripts/test/test-e2e.sh           # E2E 테스트
```

## 실행 방법

### 1. 환경변수 설정

`.env` 파일에 실제 Canvas API 정보 설정:

```env
CANVAS_API_BASE_URL=https://canvas.instructure.com/api/v1
CANVAS_API_TOKEN=your_canvas_token_here
```

### 2. 의존성 설치

```bash
pip install -r tests/requirements.txt
```

### 3. 자동 실행 (권장)

스크립트가 전체 프로세스를 자동화합니다:

```bash
./scripts/run-integration-tests.sh
```

**스크립트 실행 내용:**
1. 테스트 환경 구축 (`docker-compose.test.yml`)
2. LocalStack 초기화 (SQS 큐 + **Lambda 자동 배포**)
3. 서비스 준비 대기
4. 통합 테스트 실행
5. 환경 정리 (컨테이너 삭제)

### 3. 수동 실행

직접 제어하려면:

```bash
# 1. 테스트 환경 시작
docker-compose -f docker-compose.test.yml up -d

# 2. 서비스 준비 확인
docker-compose -f docker-compose.test.yml ps

# 3. 테스트 실행
python -m pytest tests/integration/ -v

# 4. 환경 정리
docker-compose -f docker-compose.test.yml down -v
```

## 테스트 시나리오

### test_assignment_flow.py (SQS 직접 발행)

**1. test_assignment_create_flow**
- SQS 직접 발행 → course-service → DB
- Assignment 생성 검증

**2. test_assignment_update_flow**
- Assignment 수정 이벤트 처리
- 기존 데이터 업데이트 검증

**3. test_duplicate_assignment_ignored**
- 중복 Assignment 생성 방지
- 기존 데이터 보존 검증

**4. test_assignment_without_course_fails**
- Course 없는 Assignment 생성 실패
- 에러 핸들링 검증

### test_canvas_sync_integration.py (Phase 1 Canvas 동기화 통합 테스트) ⭐

**1. test_canvas_sync_full_flow**
- Lambda invoke (cognitoSub) → Canvas API → SQS 발행 → Course-Service → DB 저장
- 전체 동기화 플로우 검증 (courses + assignments)
- **실제 Canvas API 호출**

**2. test_sqs_message_format_enrollment**
- Lambda가 발행한 enrollment 메시지 형식 검증
- 필수 필드 확인 (cognitoSub, canvasCourseId, courseName 등)

**3. test_sqs_message_format_assignment**
- Lambda가 발행한 assignment 메시지 형식 검증
- 필수 필드 확인 (eventType, canvasAssignmentId, title 등)

**4. test_idempotency_duplicate_sync**
- 동일한 Lambda를 두 번 호출했을 때 중복 데이터 생성 안 됨 검증
- 멱등성 보장

**5. test_lambda_without_canvas_token**
- Canvas 토큰이 없는 사용자 시나리오
- Lambda 에러 처리 검증

**6. test_phase2_event_format_compatibility**
- Phase 2 EventBridge 이벤트 형식 호환성 테스트
- `{"detail": {"cognitoSub": "..."}}` 형식 지원 확인

## 테스트 환경

### 서비스
- **LocalStack**: SQS + Lambda 에뮬레이션
  - SQS 큐 자동 생성
  - **Lambda 자동 배포** (localstack-init/03-deploy-lambdas.sh)
- **MySQL**: 테스트용 DB
- **course-service**: 테스트 대상
- **실제 Canvas API**: `.env`에 설정된 Canvas 서버

### 포트
- LocalStack: `4566`
- MySQL: `3307`
- course-service: `8082`

### Lambda 배포 방식

**자동 배포 (권장):**
```bash
# docker-compose up 시 자동으로 배포됨
docker-compose up -d localstack
# localstack-init/03-deploy-lambdas.sh 자동 실행
```

**수동 배포 (개발 중):**
```bash
# scripts/deploy-lambda.sh 사용
./scripts/deploy-lambda.sh canvas-sync-lambda
```

## Fixtures

### conftest.py에 정의된 주요 fixtures:

- `wait_for_services`: 모든 서비스 준비 대기
- `sqs_client`: SQS 클라이언트
- `lambda_client`: Lambda 클라이언트 (Phase 1 Canvas 동기화)
- `enrollment_queue_url`: lambda-to-courseservice-enrollments URL
- `assignment_queue_url`: lambda-to-courseservice-assignments URL
- `mysql_connection`: MySQL 연결
- `clean_database`: 각 테스트 전후 DB 정리
- `clean_sqs_queue`: 각 테스트 전후 SQS 큐 비우기
- `test_course`: 테스트용 Course 데이터

## 디버깅

### 서비스 로그 확인

```bash
# 모든 서비스 로그
docker-compose -f docker-compose.test.yml logs -f

# 특정 서비스만
docker-compose -f docker-compose.test.yml logs -f course-service
```

### DB 직접 확인

```bash
docker exec -it unisync-test-mysql mysql -utest -ptest course_service_db
```

```sql
SELECT * FROM assignments;
SELECT * FROM courses;
```

### SQS 메시지 확인

```bash
# Enrollment 메시지
awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/lambda-to-courseservice-enrollments

# Assignment 메시지
awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/lambda-to-courseservice-assignments
```

## CI/CD 통합

GitHub Actions 예시:

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  integration-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Integration Tests
        run: ./scripts/run-integration-tests.sh
```

## 주의사항

- 테스트는 **항상 격리된 환경**에서 실행됩니다
- 각 테스트 후 **자동으로 정리**됩니다
- 테스트 실패 시 **로그를 확인**하세요
- **LocalStack Pro** 라이선스가 필요합니다 (`.env`의 `LOCALSTACK_AUTH_TOKEN`)