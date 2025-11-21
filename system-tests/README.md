# System Tests

UniSync 시스템 테스트 (docker-compose.acceptance.yml 기반)

## 개요

`system-tests/`는 전체 시스템 통합 환경에서 실행되는 테스트들입니다.
모든 테스트는 `docker-compose.acceptance.yml`로 실행된 환경에서 동작합니다.

## 테스트 구조 (4단계)

```
system-tests/
├── conftest.py              # 공통 fixtures + 실행 순서 강제
├── infra/                   # 1단계: 인프라 검증
│   └── test_infrastructure.py
├── component/               # 2단계: 개별 서비스 API 검증 (예정)
├── integration/             # 3단계: 서비스 간 연동 검증
│   ├── lambda_to_course/    # Lambda → Course-Service
│   │   ├── test_canvas_sync.py
│   │   └── test_assignment_event_flow.py
│   └── course_to_schedule/  # Course-Service → Schedule-Service
│       └── test_assignment_to_schedule.py
└── scenarios/               # 4단계: E2E 사용자 시나리오
    └── test_full_user_journey.py
```

## 실행 순서

전체 실행 시 `conftest.py`의 `pytest_collection_modifyitems` 훅이 다음 순서를 강제합니다:

1. **infra** - 인프라 검증 (LocalStack, MySQL, Spring 서비스 Health Check)
2. **component** - 개별 서비스 API 검증
3. **integration** - 서비스 간 연동 검증
4. **scenarios** - E2E 사용자 시나리오

## 사전 조건

### 1. 환경변수 설정

```bash
# .env.local 파일 생성 (템플릿 복사)
cp .env.local.example .env.local

# .env.local 편집
# - LOCALSTACK_AUTH_TOKEN (필수)
# - ENCRYPTION_KEY (필수)
# - CANVAS_API_TOKEN (E2E 테스트용, 선택)
```

### 2. 환경 실행

```bash
# docker-compose.acceptance.yml 실행
docker-compose -f docker-compose.acceptance.yml up -d --build

# 서비스 준비 확인 (약 1-2분 소요)
docker-compose -f docker-compose.acceptance.yml logs -f
```

## 테스트 실행 방법

### 전체 실행 (순서 보장)

```bash
# system-tests/ 전체 실행
pytest system-tests/ -v

# 순서: infra → component → integration → scenarios
```

### 단계별 실행

```bash
# 1단계: 인프라 검증만
pytest system-tests/infra/ -v

# 2단계: Component 테스트만 (예정)
pytest system-tests/component/ -v

# 3단계: Integration 테스트만
pytest system-tests/integration/ -v

# 4단계: Scenario 테스트만
pytest system-tests/scenarios/ -v
```

### 특정 통합 테스트 실행

```bash
# Lambda → Course-Service 통합만
pytest system-tests/integration/lambda_to_course/ -v

# Course-Service → Schedule-Service 통합만
pytest system-tests/integration/course_to_schedule/ -v
```

### 개별 테스트 실행

```bash
# 특정 테스트 파일만
pytest system-tests/integration/course_to_schedule/test_assignment_to_schedule.py -v

# 특정 테스트 메서드만
pytest system-tests/scenarios/test_full_user_journey.py::TestFullUserJourney::test_complete_user_journey -v
```

## 환경 정리

```bash
# 환경 중지 (볼륨 유지)
docker-compose -f docker-compose.acceptance.yml down

# 환경 중지 + 볼륨 삭제 (완전 초기화)
docker-compose -f docker-compose.acceptance.yml down -v
```

## 테스트 작성 가이드

### Integration Test 네이밍

Integration 테스트는 `{producer}_to_{consumer}/` 폴더 구조를 사용합니다:

```
integration/
├── lambda_to_course/      # Lambda가 Course-Service로 데이터 전송
├── course_to_schedule/    # Course-Service가 Schedule-Service로 데이터 전송
└── user_to_lambda/        # User-Service가 Lambda 호출 (예정)
```

**장점**:
- 데이터 흐름 방향이 명확함
- 어떤 서비스 간 통합인지 즉시 파악 가능

### Fixture 사용

`conftest.py`에서 제공하는 공통 fixtures:

```python
# AWS 클라이언트
def test_sqs_integration(sqs_client, lambda_client):
    pass

# SQS 큐 URL
def test_message_flow(assignment_queue_url, enrollment_queue_url):
    pass

# 서비스 URL
def test_api_call(schedule_service_url, course_service_url):
    pass

# DB 연결
def test_db_query(mysql_connection, schedule_db_connection):
    pass

# 데이터 정리
@pytest.mark.usefixtures("clean_database", "clean_schedule_database")
def test_with_clean_db():
    pass

# E2E 테스트용
def test_e2e(canvas_token, jwt_auth_tokens, service_urls):
    pass
```

### AAA 패턴 사용

```python
def test_assignment_to_schedule():
    # Arrange (준비)
    assignment_message = {...}

    # Act (실행)
    sqs_client.send_message(...)

    # Assert (검증)
    assert schedule_created
```

## CI/CD 통합

```yaml
# .github/workflows/test.yml
jobs:
  system-tests:
    runs-on: ubuntu-latest
    steps:
      - name: Start Acceptance Environment
        run: docker-compose -f docker-compose.acceptance.yml up -d --build

      - name: Wait for services
        run: sleep 60

      - name: Run System Tests
        run: pytest system-tests/ -v

      - name: Cleanup
        run: docker-compose -f docker-compose.acceptance.yml down -v
```

## 참고 문서

- [Testing Strategy](../docs/design/testing-strategy.md) - 전체 테스트 아키텍처
- [SQS Architecture](../docs/design/sqs-architecture.md) - SQS 메시지 스키마
- [Canvas Sync](../docs/features/canvas-sync.md) - Canvas 동기화 플로우
