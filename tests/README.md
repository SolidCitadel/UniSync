# Integration Tests

서비스 간 E2E 통합 테스트

## 구조

```
tests/
├── integration/              # E2E 통합 테스트
│   ├── conftest.py           # pytest fixtures
│   └── test_assignment_flow.py
├── fixtures/                 # 테스트 데이터
├── requirements.txt          # 테스트 의존성
└── README.md                 # 이 파일
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

### test_assignment_flow_with_lambda.py (실제 Lambda 실행) ⭐

**1. test_lambda_canvas_to_db_flow**
- **Lambda invoke** → 실제 Canvas API 호출 → SQS 발행 → course-service → DB
- 전체 E2E 플로우 검증
- **실제 Canvas 서버 사용**

**2. test_lambda_without_course_in_db**
- DB에 Course 없을 때 Lambda 실행
- Assignment 생성 안 됨 검증

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
- `assignment_queue_url`: assignment-events-queue URL
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
awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/assignment-events-queue
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