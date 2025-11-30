# Testing Strategy

UniSync 시스템의 테스트 아키텍처 및 전략 설계 문서입니다.

## 설계 철학

### 왜 계층화된 테스트 전략인가?

1. **점진적 검증 (Progressive Verification)**
   - 작은 단위부터 전체 시스템까지 단계별 검증
   - 빠른 피드백 루프 (단위 테스트: 초, 시스템: 분)
   - 실패 지점을 빠르게 특정 (어느 단계에서 실패했는가?)

2. **격리와 통합의 균형**
   - Unit Tests: 개별 컴포넌트 격리 검증 (Mock 사용)
   - System Tests: docker-compose.acceptance.yml 기반 통합 환경 검증

3. **비용 효율적 테스트**
   - 테스트 피라미드: 빠르고 저렴한 테스트가 많고, 느리고 비싼 테스트는 적게
   - Unit Tests 80%, System Tests 20%

4. **엄격한 검증 (Strict Validation) - Fail Fast, Fail Loud**
   - **예상치 못한 모든 결과는 실패로 처리**: 테스트가 애매한 상황을 넘어가지 않음
   - **검증 생략 금지**: 상정외 동작을 skip하거나 검증을 건너뛰지 않음
   - **적극적 실패**: 모든 요청/응답/상태를 명시적으로 검증하여 숨겨진 버그 발견
   - **명확한 의도**: 각 테스트는 정확히 무엇을 검증하는지 명시하고, 그 외는 실패

## 테스트 아키텍처

### 2-Tier 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                     System Tests                                │
│   (docker-compose.acceptance.yml 기반, system-tests/ 폴더)       │
│                                                                 │
│   ┌─────────┐  ┌───────────┐  ┌─────────────┐  ┌───────────┐   │
│   │ Infra   │→ │ Component │→ │ Integration │→ │ Scenarios │   │
│   │ Tests   │  │   Tests   │  │    Tests    │  │   Tests   │   │
│   └─────────┘  └───────────┘  └─────────────┘  └───────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↑
                     docker-compose.acceptance.yml
                              ↑
┌─────────────────────────────────────────────────────────────────┐
│                       Unit Tests                                │
│            (각 서비스/모듈 내부, Mock 기반)                        │
│                                                                 │
│  app/backend/*/src/test/     app/serverless/lambdas/*/tests/   │
└─────────────────────────────────────────────────────────────────┘
```

### 레벨별 특징

| | Unit Tests | System Tests |
|---|---|---|
| **위치** | 각 서비스 내부 (`src/test/`) | `system-tests/` |
| **환경** | Mock, 격리된 환경 | docker-compose.acceptance.yml |
| **실행** | 서비스별 독립 실행 | 전체 스택 필요 |
| **비율** | 80% | 20% |
| **속도** | 초 단위 | 분 단위 |

## Unit Tests (서비스 내부)

### 위치

```
app/backend/{service}/src/test/java/    # Spring 단위 테스트
app/serverless/lambdas/{lambda}/tests/  # Lambda 단위 테스트
```

### 특징

- **격리**: Mock을 사용하여 외부 의존성 제거
- **빠름**: 초 단위 실행
- **응집도**: 코드와 테스트가 함께 관리
- **CI/CD**: 변경된 컴포넌트만 테스트 실행

### 실행

```bash
# Spring 서비스
cd app/backend/course-service && ./gradlew test

# Lambda
cd app/serverless/lambdas/canvas-sync-lambda && pytest tests/
```

## System Tests (docker-compose.acceptance.yml 기반)

### 전제 조건

```bash
# 모든 System Tests 실행 전 필수
docker-compose -f docker-compose.acceptance.yml up -d
```

### 폴더 구조

```
system-tests/
├── conftest.py                      # 공통 fixtures + 실행 순서 정렬
├── infra/                           # 1단계: 인프라 검증
│   └── test_infrastructure.py
├── component/                       # 2단계: 개별 서비스 API 검증
│   ├── user_service/
│   ├── course_service/
│   └── schedule_service/
├── integration/                     # 3단계: 서비스 간 연동 검증
│   ├── lambda_to_course/            # Lambda → Course-Service
│   ├── course_to_schedule/          # Course-Service → Schedule-Service
│   └── user_to_lambda/              # User-Service → Lambda
└── scenarios/                       # 4단계: E2E 시나리오 검증
    └── test_full_user_journey.py
```

### 4단계 System Tests

#### 1단계: Infra Tests (`system-tests/infra/`)

**목적**: 인프라가 정상적으로 올라왔는지 확인

**검증 대상**:
- LocalStack 정상 동작 (SQS, Lambda, S3)
- MySQL 연결 가능
- 각 서비스 Health Check
- SQS 큐 존재 여부
- Lambda 함수 배포 상태

**예시**:
```python
def test_localstack_is_healthy():
    """LocalStack이 정상 동작하는지 확인"""
    response = requests.get(f"{LOCALSTACK_URL}/_localstack/health")
    assert response.status_code == 200

def test_sqs_queues_exist():
    """필수 SQS 큐가 존재하는지 확인"""
    queues = sqs_client.list_queues()
    assert 'lambda-to-courseservice-assignments' in str(queues)
```

#### 2단계: Component Tests (`system-tests/component/`)

**목적**: 각 서비스를 외부에서 API로 검증

**검증 대상**:
- REST API 엔드포인트 정상 응답
- 기본 CRUD 동작
- 에러 핸들링
- 인증/인가 (헤더 검증)

**예시**:
```python
# system-tests/component/schedule_service/test_schedule_api.py
def test_create_schedule_returns_201():
    """일정 생성 API가 201을 반환하는지 확인"""
    response = requests.post(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules",
        headers={"X-Cognito-Sub": "test-user"},
        json={"title": "Test", "startTime": "...", "endTime": "..."}
    )
    assert response.status_code == 201
```

#### 3단계: Integration Tests (`system-tests/integration/`)

**목적**: 서비스 간 1:1 연동 검증

**폴더 네이밍**: `{producer}_to_{consumer}/`
- 데이터 흐름 방향이 명확함
- 어떤 연동을 테스트하는지 즉시 파악 가능

**검증 대상**:
- SQS 메시지 발행 → 수신 → 처리
- API 호출 체인
- 데이터 변환 정확성

**예시**:
```python
# system-tests/integration/course_to_schedule/test_assignment_to_schedule.py
def test_assignment_created_creates_schedule():
    """Assignment 생성 이벤트가 Schedule을 생성하는지 확인"""
    # SQS에 메시지 발행
    sqs_client.send_message(
        QueueUrl=ASSIGNMENT_QUEUE_URL,
        MessageBody=json.dumps(assignment_event)
    )

    # Schedule-Service API로 생성 확인
    time.sleep(10)
    response = requests.get(f"{SCHEDULE_SERVICE_URL}/v1/schedules", ...)
    assert len(response.json()) == 1
```

#### 4단계: Scenario Tests (`system-tests/scenarios/`)

**목적**: 전체 E2E 사용자 시나리오 검증

**검증 대상**:
- 회원가입 → 토큰 등록 → 동기화 → 일정 조회
- 실제 사용자 플로우 전체

**예시**:
```python
# system-tests/scenarios/test_full_user_journey.py
def test_complete_canvas_sync_flow():
    """
    전체 Canvas 동기화 플로우 검증
    1. 회원가입
    2. Canvas 토큰 등록
    3. 수동 동기화 실행
    4. Course 데이터 확인
    5. Schedule 데이터 확인
    """
    # 1. 회원가입
    user = create_test_user()

    # 2. Canvas 토큰 등록
    register_canvas_token(user, VALID_TOKEN)

    # 3. 수동 동기화
    trigger_manual_sync(user)

    # 4. Course 확인
    courses = get_user_courses(user)
    assert len(courses) > 0

    # 5. Schedule 확인
    schedules = get_user_schedules(user)
    assert len(schedules) > 0
```

### 실행 순서 강제

`conftest.py`의 `pytest_collection_modifyitems` 훅으로 전체 실행 시 순서 보장:

```python
# system-tests/conftest.py
def pytest_collection_modifyitems(items):
    """전체 실행 시 순서 강제: infra → component → integration → scenarios"""
    order = {
        "infra": 0,
        "component": 1,
        "integration": 2,
        "scenarios": 3
    }

    def get_sort_key(item):
        path = str(item.fspath)
        for folder, priority in order.items():
            if f"/{folder}/" in path or f"\\{folder}\\" in path:
                return priority
        return 100

    items.sort(key=get_sort_key)
```

### 실행 방법

> **중요**: 모든 pytest 명령어는 `poetry run`을 통해 실행합니다.
> 의존성 설치: `poetry install` (루트 디렉토리에서)

```bash
# 전체 실행 (순서 보장: infra → component → integration → scenarios)
poetry run pytest system-tests/

# 개별 단계만 실행
poetry run pytest system-tests/infra/
poetry run pytest system-tests/component/
poetry run pytest system-tests/integration/
poetry run pytest system-tests/scenarios/

# 특정 통합 테스트만 실행
poetry run pytest system-tests/integration/course_to_schedule/
```

## 테스트 데이터 관리

### 원칙

1. **독립성**: 각 테스트는 독립적으로 실행 가능
2. **격리**: 테스트 간 데이터 공유 금지
3. **정리**: 테스트 종료 시 데이터 자동 정리

### 전략

**Unit Tests**:
- Mock 데이터 (코드 내 정의)
- 실제 DB/외부 API 없음

**System Tests**:
- pytest fixture로 테스트 데이터 생성/정리
- docker-compose.acceptance.yml은 휘발성 볼륨 (tmpfs) 사용
- 각 테스트 전후 DB 정리 (TRUNCATE)

## 테스트 작성 가이드라인

### 네이밍 규칙

**형식**: `test_{대상}_{시나리오}_{예상결과}`

```python
# Good
def test_schedule_api_with_valid_request_returns_201():
    pass

def test_assignment_event_with_duplicate_id_skips_creation():
    pass

# Bad
def test_1():
    pass
```

### AAA 패턴 (Arrange-Act-Assert)

```python
def test_assignment_to_schedule_conversion():
    # Arrange (준비)
    assignment_event = create_test_assignment_event()

    # Act (실행)
    sqs_client.send_message(...)
    time.sleep(10)
    response = requests.get(...)

    # Assert (검증)
    assert response.status_code == 200
    assert len(response.json()) == 1
```

### 엄격한 검증 원칙 (Strict Validation)

테스트는 예상치 못한 모든 상황을 **실패로 처리**해야 합니다. 애매한 상황을 넘어가지 않습니다.

#### ❌ 나쁜 예: 검증 생략

```python
def test_create_schedule_bad():
    response = requests.post(...)
    # 상태 코드만 확인하고 응답 본문은 검증 안함
    assert response.status_code == 201
```

**문제점**:
- 응답 본문에 예상치 못한 필드가 있어도 통과
- 데이터 타입이 잘못되어도 통과
- 필수 필드가 누락되어도 통과

#### ✅ 좋은 예: 모든 필드 명시적 검증

```python
def test_create_schedule_with_valid_data_returns_complete_response():
    response = requests.post(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules",
        headers={"X-Cognito-Sub": "test-user-123"},
        json={
            "title": "Test Schedule",
            "startTime": "2025-01-01T10:00:00Z",
            "endTime": "2025-01-01T11:00:00Z",
            "source": "USER",
            "categoryId": 1
        }
    )

    # 1. 상태 코드 검증
    assert response.status_code == 201

    # 2. 응답 본문 존재 검증
    data = response.json()
    assert data is not None

    # 3. 모든 필수 필드 존재 및 타입 검증
    assert "id" in data
    assert isinstance(data["id"], int)
    assert data["id"] > 0

    assert "title" in data
    assert data["title"] == "Test Schedule"

    assert "startTime" in data
    assert data["startTime"] == "2025-01-01T10:00:00Z"

    assert "endTime" in data
    assert data["endTime"] == "2025-01-01T11:00:00Z"

    assert "source" in data
    assert data["source"] == "USER"

    assert "categoryId" in data
    assert data["categoryId"] == 1

    # 4. 예상치 못한 필드 발견 시 실패
    expected_fields = {"id", "title", "startTime", "endTime", "source", "categoryId", "createdAt", "updatedAt"}
    actual_fields = set(data.keys())
    unexpected_fields = actual_fields - expected_fields
    assert len(unexpected_fields) == 0, f"Unexpected fields found: {unexpected_fields}"
```

#### ❌ 나쁜 예: 예외 상황 검증 생략

```python
def test_create_schedule_with_missing_field_bad():
    response = requests.post(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules",
        headers={"X-Cognito-Sub": "test-user"},
        json={"title": "Test"}  # 필수 필드 누락
    )
    # 에러만 확인하고 구체적인 에러 메시지는 검증 안함
    assert response.status_code == 400
```

**문제점**:
- 에러 메시지가 명확한지 검증 안함
- 어떤 필드가 누락되었는지 알려주는지 확인 안함
- 에러 응답 형식이 일관적인지 검증 안함

#### ✅ 좋은 예: 에러 응답도 엄격하게 검증

```python
def test_create_schedule_with_missing_start_time_returns_detailed_error():
    response = requests.post(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules",
        headers={"X-Cognito-Sub": "test-user"},
        json={
            "title": "Test",
            # startTime 누락
            "endTime": "2025-01-01T11:00:00Z",
            "source": "USER",
            "categoryId": 1
        }
    )

    # 1. 에러 상태 코드 검증
    assert response.status_code == 400

    # 2. 에러 응답 형식 검증
    error_data = response.json()
    assert "error" in error_data
    assert "message" in error_data
    assert "field" in error_data

    # 3. 에러 메시지 구체성 검증
    assert error_data["field"] == "startTime"
    assert "required" in error_data["message"].lower() or "missing" in error_data["message"].lower()

    # 4. 예상치 못한 부작용 검증 (DB에 저장되지 않았는지)
    all_schedules = requests.get(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules",
        headers={"X-Cognito-Sub": "test-user"}
    ).json()
    assert len(all_schedules) == 0, "Failed request should not create any data"
```

#### ❌ 나쁜 예: 상태 변경 검증 생략

```python
def test_update_schedule_status_bad():
    # 상태만 바꾸고 다른 필드는 변경 안되었는지 검증 안함
    response = requests.patch(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules/1",
        headers={"X-Cognito-Sub": "test-user"},
        json={"status": "COMPLETED"}
    )
    assert response.status_code == 200
```

**문제점**:
- 다른 필드가 실수로 변경되었는지 확인 안함
- 상태 변경 히스토리가 남는지 확인 안함
- 타임스탬프가 업데이트되는지 확인 안함

#### ✅ 좋은 예: 변경 전후 상태 완전 검증

```python
def test_update_schedule_status_only_changes_status_field():
    # Arrange: 초기 일정 생성
    create_response = requests.post(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules",
        headers={"X-Cognito-Sub": "test-user"},
        json={
            "title": "Original Title",
            "startTime": "2025-01-01T10:00:00Z",
            "endTime": "2025-01-01T11:00:00Z",
            "source": "USER",
            "categoryId": 1
        }
    )
    schedule_id = create_response.json()["id"]
    original_schedule = create_response.json()

    # Act: 상태만 변경
    update_response = requests.patch(
        f"{SCHEDULE_SERVICE_URL}/v1/schedules/{schedule_id}",
        headers={"X-Cognito-Sub": "test-user"},
        json={"status": "COMPLETED"}
    )

    # Assert
    assert update_response.status_code == 200
    updated_schedule = update_response.json()

    # 1. 상태만 변경되었는지 검증
    assert updated_schedule["status"] == "COMPLETED"

    # 2. 다른 필드는 그대로인지 검증
    assert updated_schedule["title"] == original_schedule["title"]
    assert updated_schedule["startTime"] == original_schedule["startTime"]
    assert updated_schedule["endTime"] == original_schedule["endTime"]
    assert updated_schedule["source"] == original_schedule["source"]
    assert updated_schedule["categoryId"] == original_schedule["categoryId"]

    # 3. updatedAt은 변경되었는지 검증
    assert updated_schedule["updatedAt"] != original_schedule["updatedAt"]

    # 4. createdAt은 그대로인지 검증
    assert updated_schedule["createdAt"] == original_schedule["createdAt"]
```

#### 핵심 원칙 요약

1. **모든 응답 필드 검증**: 상태 코드만 보지 말고 응답 본문 전체 검증
2. **예상치 못한 필드 감지**: 추가 필드가 생기면 실패 (API 계약 위반)
3. **에러 응답 구체성 검증**: 에러 메시지가 명확하고 actionable한지 확인
4. **부작용 검증**: 실패한 요청이 DB에 흔적을 남기지 않는지 확인
5. **불변성 검증**: 변경하지 않아야 할 필드가 유지되는지 확인
6. **타입 검증**: 값뿐만 아니라 타입도 정확한지 확인
7. **범위 검증**: 숫자/날짜 값이 합리적인 범위 내에 있는지 확인

## 점진적 테스트 실행 전략

### 실행 순서

```
1. Unit Tests (각 서비스 내부, 30초)
   ↓ PASS
2. docker-compose.acceptance.yml up
   ↓
3. System Tests (infra → component → integration → scenarios, 5분)
   ↓ PASS
4. ✅ 인수 테스트 완료, 배포 준비
```

### CI/CD 통합

```yaml
name: Test Suite

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - name: Install Poetry
        uses: snok/install-poetry@v1
      - name: Install dependencies
        run: poetry install
      - name: Run Java Unit Tests
        run: ./gradlew test
      - name: Run Lambda Unit Tests
        run: poetry run pytest app/serverless/canvas-sync-lambda/tests/

  system-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - name: Install Poetry
        uses: snok/install-poetry@v1
      - name: Install dependencies
        run: poetry install
      - name: Start Acceptance Environment
        run: docker-compose -f docker-compose.acceptance.yml up -d
      - name: Wait for services
        run: sleep 60
      - name: Run System Tests
        run: poetry run pytest system-tests/ -v
```

## 배포 전 필수 조건

**main 브랜치 병합**:
- ✅ 모든 Unit Tests 통과
- ✅ 모든 System Tests 통과 (infra → component → integration → scenarios)
- ✅ 코드 리뷰 승인 (1명 이상)

---

## 테스트 커버리지 현황

### Unit Tests (총 156개)

| 서비스 | 테스트 수 | 위치 |
|--------|---------|------|
| User-Service | 30 | `app/backend/user-service/src/test/` |
| Course-Service | 39 | `app/backend/course-service/src/test/` |
| Schedule-Service | 87 | `app/backend/schedule-service/src/test/` |

### System Tests (총 86개)

| 단계 | 테스트 수 | 주요 검증 항목 |
|------|---------|---------------|
| **infra/** | 10 | LocalStack, SQS 큐, Lambda, MySQL, 서비스 Health |
| **component/** | 54 | JWT 인증, Canvas 토큰 CRUD, 전체 API CRUD |
| **integration/** | 15 | User→Lambda, Lambda→Course, Course→Schedule 연동 |
| **scenarios/** | 5 | 전체 사용자 여정, Todo 관리, 카테고리 관리 E2E |

### Component Tests 상세

| 서비스 | 테스트 파일 | 테스트 수 | 검증 항목 |
|--------|------------|----------|----------|
| API Gateway | `test_auth.py` | 7 | JWT 인증, 공개 엔드포인트, 내부 API 차단 |
| Course-Service | `test_course_api.py` | 3 | 과목/과제 조회 |
| Course-Service | `test_sync_api.py` | 6 | Canvas 동기화 트리거, 토큰 검증 |
| Schedule-Service | `test_schedule_api.py` | 5 | 일정 기본 API |
| Schedule-Service | `test_schedule_crud.py` | 8 | 일정 CRUD 완전 검증 |
| Schedule-Service | `test_category_crud.py` | 6 | 카테고리 CRUD 완전 검증 |
| Schedule-Service | `test_todo_api.py` | 11 | Todo CRUD, 상태/진행률 변경, 서브태스크 |
| User-Service | `test_credentials_api.py` | 3 | Canvas 토큰 등록/조회/삭제 |
| User-Service | `test_profile_api.py` | 5 | 사용자 프로필, 연동 상태 조회 |

### Integration Tests 상세

| 폴더 | 테스트 파일 | 테스트 수 | 검증 항목 |
|------|------------|----------|----------|
| `user_to_lambda/` | `test_canvas_sync_trigger.py` | 3 | User-Service → Lambda 호출, 에러 처리 |
| `lambda_to_course/` | `test_canvas_sync.py` | 4 | Lambda → SQS → Course-Service 전체 플로우 |
| `lambda_to_course/` | `test_assignment_event_flow.py` | 4 | 과제 이벤트 CRUD 플로우 |
| `lambda_to_course/` | `test_disabled_enrollments.py` | 1 | 활성 수강이 없으면 동기화 스킵 |
| `course_to_schedule/` | `test_assignment_to_schedule.py` | 5 | Course-Service → Schedule-Service 연동 |

### Scenario Tests 상세

| 테스트 파일 | 시나리오 | 검증 항목 |
|------------|---------|----------|
| `test_full_user_journey.py` | 전체 사용자 여정 | 회원가입 → Canvas 연동 → 동기화 → 일정 확인 |
| `test_todo_journey.py` | Todo 관리 | 카테고리 생성 → Todo/서브태스크 생성 → 진행률/상태 관리 → 정리 |
| `test_category_management.py` | 카테고리 관리 | 다중 카테고리 생성 → 일정/할일 분류 → 색상 커스터마이징 |
| `test_sync_disable_flow.py` | 비활성 과목 차단 | 과목 비활성화 후 동기화 시 스케줄/카테고리 미생성 확인 |

### 총 테스트 현황

| 구분 | 테스트 수 |
|------|---------|
| Unit Tests | 156 |
| System Tests | 129 |
| **Total** | **285** |

---

## 참고 문서

- **시스템 아키텍처**: [docs/design/system-architecture.md](./system-architecture.md)
- **SQS 아키텍처**: [docs/design/sqs-architecture.md](./sqs-architecture.md)
- **Canvas 동기화**: [docs/features/canvas-sync.md](../features/canvas-sync.md)
- **시스템 테스트 실행**: [system-tests/README.md](../../system-tests/README.md)
