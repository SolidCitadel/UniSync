# System Tests

UniSync 시스템 테스트 (docker-compose.acceptance.yml 기반)

## 개요

`system-tests/`는 전체 시스템 통합 환경에서 실행되는 테스트들입니다.
모든 테스트는 `docker-compose.acceptance.yml`로 실행된 환경에서 동작합니다.

## 테스트 구조 (4단계)

```
system-tests/
├── conftest.py                          # 공통 fixtures + 실행 순서 강제
├── infra/                               # 1단계: 인프라 검증 (10개)
│   └── test_infrastructure.py
├── component/                           # 2단계: 개별 서비스 API 검증 (54개)
│   ├── api_gateway/
│   │   └── test_auth.py                 # JWT 인증, 공개 엔드포인트
│   ├── course_service/
│   │   ├── test_course_api.py           # 과목/과제 조회
│   │   └── test_sync_api.py             # Canvas 동기화 트리거
│   ├── schedule_service/
│   │   ├── test_schedule_api.py         # 일정 기본 API
│   │   ├── test_schedule_crud.py        # 일정 CRUD 완전 검증
│   │   ├── test_category_crud.py        # 카테고리 CRUD 완전 검증
│   │   └── test_todo_api.py             # Todo CRUD, 서브태스크
│   └── user_service/
│       ├── test_credentials_api.py      # Canvas 토큰 관리
│       └── test_profile_api.py          # 사용자 프로필, 연동 상태
├── integration/                         # 3단계: 서비스 간 연동 검증 (15개)
│   ├── user_to_lambda/                  # User-Service → Lambda
│   │   └── test_canvas_sync_trigger.py  # 동기화 API → Lambda 호출
│   ├── lambda_to_course/                # Lambda → Course-Service
│   │   ├── test_canvas_sync.py
│   │   └── test_assignment_event_flow.py
│   └── course_to_schedule/              # Course-Service → Schedule-Service
│       └── test_assignment_to_schedule.py
└── scenarios/                           # 4단계: E2E 사용자 시나리오 (5개)
    ├── test_full_user_journey.py        # 전체 사용자 여정
    ├── test_todo_journey.py             # Todo 관리 워크플로우
    └── test_category_management.py      # 카테고리 관리 워크플로우
```

**총 테스트 수: 86개** (Unit Tests 156개 별도)

## 실행 순서

전체 실행 시 `conftest.py`의 `pytest_collection_modifyitems` 훅이 다음 순서를 강제합니다:

1. **infra** - 인프라 검증 (LocalStack, MySQL, Spring 서비스 Health Check)
2. **component** - 개별 서비스 API 검증
3. **integration** - 서비스 간 연동 검증
4. **scenarios** - E2E 사용자 시나리오

## 사전 조건

### 1. Python 의존성 설치 (Poetry)

모든 Python 의존성(pytest, boto3, requests 등)은 **루트 `pyproject.toml`**에서 Poetry로 관리됩니다.

```bash
# Poetry 설치 (최초 1회)
# macOS/Linux
curl -sSL https://install.python-poetry.org | python3 -
# Windows
(Invoke-WebRequest -Uri https://install.python-poetry.org -UseBasicParsing).Content | py -

# 의존성 설치 (루트 디렉토리에서)
poetry install
```

> **참고**: 자세한 Poetry 사용법은 [docs/guides/development-setup.md](../docs/guides/development-setup.md#poetry-설치) 참고

### 2. 환경변수 설정

```bash
# .env.local 파일 생성 (템플릿 복사)
cp .env.local.example .env.local

# .env.local 편집
# - LOCALSTACK_AUTH_TOKEN (필수)
# - ENCRYPTION_KEY (필수)
# - CANVAS_API_TOKEN (E2E 테스트용, 선택)
```

### 3. 환경 실행

```bash
# docker-compose.acceptance.yml 실행
docker-compose -f docker-compose.acceptance.yml up -d --build

# 서비스 준비 확인 (약 1-2분 소요)
docker-compose -f docker-compose.acceptance.yml logs -f
```

## 테스트 실행 방법

> **중요**: 모든 pytest 명령어는 `poetry run`을 통해 실행해야 합니다.
> Poetry가 관리하는 가상환경에 pytest, boto3 등 필요한 의존성이 설치되어 있습니다.

### 전체 실행 (순서 보장)

```bash
# system-tests/ 전체 실행
poetry run pytest system-tests/ -v

# 순서: infra → component → integration → scenarios
```

### 단계별 실행

```bash
# 1단계: 인프라 검증만
poetry run pytest system-tests/infra/ -v

# 2단계: Component 테스트만
poetry run pytest system-tests/component/ -v

# 3단계: Integration 테스트만
poetry run pytest system-tests/integration/ -v

# 4단계: Scenario 테스트만
poetry run pytest system-tests/scenarios/ -v
```

### 특정 통합 테스트 실행

```bash
# Lambda → Course-Service 통합만
poetry run pytest system-tests/integration/lambda_to_course/ -v

# Course-Service → Schedule-Service 통합만
poetry run pytest system-tests/integration/course_to_schedule/ -v
```

### 개별 테스트 실행

```bash
# 특정 테스트 파일만
poetry run pytest system-tests/integration/course_to_schedule/test_assignment_to_schedule.py -v

# 특정 테스트 메서드만
poetry run pytest system-tests/scenarios/test_full_user_journey.py::TestFullUserJourney::test_complete_user_journey -v
```

### Poetry Shell 사용 (선택)

Poetry 가상환경에 진입하면 `poetry run` 없이 직접 명령어를 실행할 수 있습니다:

```bash
# Poetry shell 진입
poetry shell

# 이후 pytest 직접 실행 가능
pytest system-tests/ -v

# shell 종료
exit
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
      - name: Install Poetry
        uses: snok/install-poetry@v1

      - name: Install dependencies
        run: poetry install

      - name: Start Acceptance Environment
        run: docker-compose -f docker-compose.acceptance.yml up -d --build

      - name: Wait for services
        run: sleep 60

      - name: Run System Tests
        run: poetry run pytest system-tests/ -v

      - name: Cleanup
        run: docker-compose -f docker-compose.acceptance.yml down -v
```

## 참고 문서

- [Testing Strategy](../docs/design/testing-strategy.md) - 전체 테스트 아키텍처
- [SQS Architecture](../docs/design/sqs-architecture.md) - SQS 메시지 스키마
- [Canvas Sync](../docs/features/canvas-sync.md) - Canvas 동기화 플로우
