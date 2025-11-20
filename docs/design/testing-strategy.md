# Testing Strategy

UniSync 시스템의 테스트 아키텍처 및 전략 설계 문서입니다.

## 설계 철학

### 왜 계층화된 테스트 전략인가?

1. **점진적 검증 (Progressive Verification)**
   - 작은 단위부터 전체 시스템까지 단계별 검증
   - 빠른 피드백 루프 (단위 테스트: 초, 통합: 분, E2E: 시간)
   - 실패 지점을 빠르게 특정 (어느 계층에서 실패했는가?)

2. **격리와 통합의 균형**
   - 단위 테스트: 개별 컴포넌트 격리 검증 (Mock 사용)
   - 통합 테스트: 컴포넌트 간 상호작용 검증 (실제 의존성)
   - E2E 테스트: 실제 사용자 시나리오 검증 (전체 스택)

3. **비용 효율적 테스트**
   - 테스트 피라미드: 빠르고 저렴한 테스트가 많고, 느리고 비싼 테스트는 적게
   - 단위 테스트 70-80%, 통합 15-20%, E2E 5-10%
   - CI/CD에서 빠른 실행 시간 유지

4. **실제 환경 근접**
   - Testcontainers: 실제 DB, SQS 환경 시뮬레이션
   - docker-compose.acceptance.yml: 배포 환경과 동일한 구성
   - LocalStack: AWS 서비스 로컬 에뮬레이션

## 테스트 아키텍처

### 계층 구조 (Test Pyramid)

```
                 ┌─────────────┐
                 │   E2E       │  ← 5-10% (느림, 높은 신뢰도)
                 │ (시나리오)   │
                 └─────────────┘
              ┌──────────────────┐
              │  Integration     │  ← 15-20% (중간 속도)
              │ (컴포넌트 간)     │
              └──────────────────┘
         ┌──────────────────────────┐
         │    Unit Tests            │  ← 70-80% (빠름)
         │   (개별 함수/클래스)      │
         └──────────────────────────┘
```

### 레벨별 특징 비교

| | Unit | Integration | E2E |
|---|---|---|---|
| **실행 위치** | 컴포넌트 내부 | `tests/integration/` | `tests/e2e/` |
| **의존성** | Mock | LocalStack, Testcontainers | docker-compose.acceptance.yml |
| **실행 시간** | 초 단위 | 분 단위 | 분-시간 단위 |
| **비율** | 70-80% | 15-20% | 5-10% |
| **격리** | 완전 격리 | 부분 격리 | 통합 환경 |
| **도구** | JUnit, pytest + Mock | pytest + LocalStack | pytest + requests |

## 테스트 위치 전략

### 단위 테스트: 컴포넌트 내부

**원칙**: 각 컴포넌트는 자신의 단위 테스트를 포함

**위치**:
```
app/backend/{service}/src/test/java/           # Spring 단위 테스트
app/serverless/lambdas/{lambda}/tests/         # Lambda 단위 테스트
```

**이유**:
- 응집도: 코드와 테스트가 함께 관리
- 독립성: 컴포넌트 단독으로 개발/테스트 가능
- 일관성: 모든 컴포넌트가 동일한 구조
- CI/CD: 변경된 컴포넌트만 테스트 실행

**예시**:
```
app/backend/course-service/
├── src/main/java/
│   └── com/unisync/course/
│       └── assignment/service/AssignmentService.java
└── src/test/java/
    └── com/unisync/course/
        └── assignment/service/AssignmentServiceTest.java  ← 단위 테스트

app/serverless/lambdas/canvas-sync-lambda/
├── handler.py
└── tests/
    └── test_handler.py  ← 단위 테스트
```

### 통합 테스트: tests/integration/

**목적**: 여러 컴포넌트 간 상호작용 검증

**구조**:
```
tests/integration/
├── sqs/                          # SQS 메시지 플로우
│   ├── test_assignment_event_flow.py
│   └── test_enrollment_event_flow.py
├── lambda/                       # Lambda 통합
│   └── test_canvas_sync_lambda.py
└── services/                     # 서비스 간 통합
    └── test_course_to_schedule.py
```

**요구사항**: LocalStack, MySQL (docker-compose로 제공)

**검증 대상**:
- Lambda → SQS → Service → DB 플로우
- SQS 메시지 발행/구독
- 서비스 간 API 호출
- DB 트랜잭션

### E2E 테스트: tests/e2e/

**목적**: 실제 사용자 시나리오 전체 플로우 검증

**구조**:
```
tests/e2e/
├── canvas_sync/
│   ├── test_manual_sync_e2e.py
│   └── test_sync_with_jwt_e2e.py
└── assignment_to_schedule/
    └── test_assignment_to_schedule_e2e.py
```

**요구사항**: docker-compose.acceptance.yml (전체 스택)

**검증 시나리오**:
- 회원가입 → 로그인 → Canvas 토큰 등록 → 동기화 → 조회
- 과제 생성 → 일정 변환 → 할일 생성 → 상태 업데이트
- 전체 비즈니스 플로우

## 테스트 환경 구성

### 환경별 설정

| 환경 | 용도 | 사용 계층 | 실행 방법 |
|------|------|-----------|-----------|
| **로컬 (단위)** | 개발 중 빠른 검증 | Unit | `./gradlew test`, `pytest tests/` |
| **로컬 (통합)** | 컴포넌트 간 검증 | Integration | `docker-compose up && pytest tests/integration` |
| **CI (인수)** | PR 검증 | E2E | `docker-compose -f acceptance.yml up && pytest tests/e2e` |
| **Staging** | 배포 전 검증 | Manual E2E | 실제 AWS 환경 |

### Testcontainers vs LocalStack vs docker-compose

**Testcontainers** (Spring 통합 테스트 - 향후 구현):
- Spring Boot 테스트에서 사용
- MySQL, LocalStack 자동 시작/종료
- 테스트마다 격리된 환경

**LocalStack** (Python 통합 테스트):
- `docker-compose up localstack` 수동 실행
- SQS, Lambda, S3 등 AWS 서비스 에뮬레이션
- `tests/integration/` 에서 사용

**docker-compose.acceptance.yml** (E2E):
- 전체 시스템 스택 (모든 서비스 + 인프라)
- 배포 환경과 동일한 구성
- `tests/e2e/` 에서 사용

## 테스트 데이터 관리

### 원칙

1. **독립성**: 각 테스트는 독립적으로 실행 가능
2. **격리**: 테스트 간 데이터 공유 금지
3. **정리**: 테스트 종료 시 데이터 자동 정리

### 전략

**Unit Tests**:
- Mock 데이터 (코드 내 정의)
- 실제 DB/외부 API 없음

**Integration Tests**:
- pytest fixture로 테스트 데이터 생성/정리
- 각 테스트마다 독립된 데이터셋

**E2E Tests**:
- 전용 테스트 계정 자동 생성/삭제
- docker-compose.acceptance.yml은 휘발성 볼륨 사용

### 예시

```python
# pytest fixture로 테스트 데이터 관리
@pytest.fixture
def test_assignment_event():
    """테스트용 Assignment 이벤트 생성"""
    return {
        "eventType": "ASSIGNMENT_CREATED",
        "canvasAssignmentId": 12345,
        "title": "Test Assignment"
    }

@pytest.fixture(scope="function")
def clean_sqs_queue():
    """각 테스트 전후로 SQS 큐 비우기"""
    sqs = boto3.client('sqs', endpoint_url=LOCALSTACK_URL)
    queue_url = sqs.get_queue_url(QueueName='test-queue')['QueueUrl']

    # 테스트 전 정리
    sqs.purge_queue(QueueUrl=queue_url)

    yield

    # 테스트 후 정리
    sqs.purge_queue(QueueUrl=queue_url)
```

## 테스트 작성 가이드라인

### 네이밍 규칙

**형식**: `test_{대상}_{시나리오}_{예상결과}`

**예시**:
```python
# Good
def test_lambda_handler_with_valid_event_returns_200():
    pass

def test_assignment_service_with_duplicate_canvas_id_skips_save():
    pass

# Bad
def test_1():
    pass

def test_handler():
    pass
```

### AAA 패턴 (Arrange-Act-Assert)

```python
def test_assignment_to_schedule_conversion():
    # Arrange (준비)
    assignment_event = create_test_assignment_event()

    # Act (실행)
    response = process_assignment_event(assignment_event)

    # Assert (검증)
    assert response['statusCode'] == 200
    assert 'scheduleId' in response
```

### 테스트 커버리지 목표

| 계층 | 목표 |
|------|------|
| Lambda 핸들러 | 80% 이상 |
| Spring Service | 90% 이상 |
| Spring Controller | 70% 이상 |
| DTO | 100% |
| **전체** | **80% 이상** |

## 점진적 테스트 실행 전략

### 실행 순서

```
1. Unit Tests (30초)
   ↓ PASS
2. Integration Tests (5분)
   ↓ PASS
3. E2E Tests (10분)
   ↓ PASS
4. ✅ 배포 준비 완료
```

### 테스트 스크립트

**개별 실행**:
```bash
# 단위 테스트 (각 컴포넌트 내부)
cd app/backend/course-service && ./gradlew test
cd app/serverless/lambdas/canvas-sync-lambda && pytest tests/

# 통합 테스트 (LocalStack 필요)
docker-compose up -d localstack mysql
pytest tests/integration/

# E2E 테스트 (전체 스택)
docker-compose -f docker-compose.acceptance.yml up -d
pytest tests/e2e/
```

**점진적 실행** (향후 구현):
```bash
# 모든 레벨 순차 실행 (실패 시 중단)
./scripts/test/test-progressive.sh
```

## CI/CD 통합

### GitHub Actions 전략

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
      - name: Run Java Unit Tests
        run: ./gradlew test
      - name: Run Lambda Unit Tests
        run: pytest app/serverless/lambdas/*/tests/

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - name: Start LocalStack
        run: docker-compose up -d localstack mysql
      - name: Run Integration Tests
        run: pytest tests/integration/

  e2e-tests:
    runs-on: ubuntu-latest
    needs: integration-tests
    steps:
      - name: Run E2E Tests
        run: |
          docker-compose -f docker-compose.acceptance.yml up -d
          pytest tests/e2e/
```

### 배포 전 필수 조건

**main 브랜치 병합**:
- ✅ 모든 Unit Tests 통과
- ✅ 모든 Integration Tests 통과
- ✅ 모든 E2E Tests 통과
- ✅ 코드 리뷰 승인 (1명 이상)
- ✅ 커버리지 80% 이상

## 참고 문서

- **시스템 아키텍처**: [docs/design/system-architecture.md](./system-architecture.md) - 전체 시스템 구조
- **SQS 아키텍처**: [docs/design/sqs-architecture.md](./sqs-architecture.md) - 통합 테스트에서 검증할 SQS 플로우
- **Canvas 동기화**: [docs/features/canvas-sync.md](../features/canvas-sync.md) - Canvas 동기화 기능 명세
- **테스트 실행 가이드**: [tests/README.md](../../tests/README.md) - 실제 테스트 실행 방법
