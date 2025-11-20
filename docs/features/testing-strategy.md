# UniSync 테스트 전략

**버전**: 1.0
**작성일**: 2025-11-20
**상태**: 진행 중

## 목차
1. [개요](#1-개요)
2. [테스트 계층 구조](#2-테스트-계층-구조)
3. [테스트 환경](#3-테스트-환경)
4. [점진적 테스트 실행](#4-점진적-테스트-실행)
5. [테스트 작성 가이드](#5-테스트-작성-가이드)
6. [CI/CD 통합](#6-cicd-통합)

---

## 1. 개요

### 1.1 배경

UniSync는 마이크로서비스 + 서버리스 하이브리드 아키텍처로 구성되어 있어, 각 컴포넌트의 독립성과 전체 시스템의 통합을 모두 검증해야 합니다.

**테스트 철학**:
- **점진적 검증**: 작은 단위부터 전체 시스템까지 단계별 검증
- **격리와 통합의 균형**: 개별 컴포넌트는 격리 테스트, 상호작용은 통합 테스트
- **실제 환경 근접**: 가능한 한 실제 배포 환경과 유사한 조건에서 테스트
- **자동화 우선**: 모든 테스트는 자동화되어 반복 실행 가능해야 함

### 1.2 목표

1. **신뢰성**: 각 배포가 기존 기능을 망가뜨리지 않음을 보장
2. **개발 속도**: 빠른 피드백 루프로 버그 조기 발견
3. **문서화**: 테스트 자체가 기능 명세 역할 수행
4. **회귀 방지**: 수정된 코드가 다른 부분에 영향 주지 않음을 확인

---

## 2. 테스트 계층 구조

### 2.1 테스트 피라미드

```
                 ┌─────────────┐
                 │   E2E/      │  ← 적은 수, 느림, 높은 신뢰도
                 │ Acceptance  │     (전체 시나리오)
                 └─────────────┘
              ┌──────────────────┐
              │   Integration    │  ← 중간 수, 중간 속도
              │     Tests        │     (컴포넌트 간 상호작용)
              └──────────────────┘
         ┌──────────────────────────┐
         │      Unit Tests          │  ← 많은 수, 빠름, 낮은 신뢰도
         │  (개별 컴포넌트 격리)     │     (함수/클래스 단위)
         └──────────────────────────┘
```

### 2.2 계층별 특징

#### Level 1: Unit Tests (단위 테스트)

**목적**: 개별 컴포넌트의 논리적 정확성 검증

**범위**:
- Lambda 핸들러 함수 (입력 → 출력 검증)
- Spring Service 클래스 (비즈니스 로직)
- Utility 함수 (데이터 변환, 검증 등)
- DTO 직렬화/역직렬화

**특징**:
- **격리**: 외부 의존성 모킹 (DB, SQS, Lambda 등)
- **빠름**: 초 단위 실행
- **많은 수**: 전체 테스트의 70-80%
- **로컬 실행**: docker-compose 없이 실행 가능

**기술 스택**:
- Java: JUnit 5, Mockito, AssertJ
- Python: pytest, unittest.mock

**예시**:
```python
# Lambda 핸들러 단위 테스트
def test_lambda_handler_extracts_cognito_sub():
    event = {"cognitoSub": "user-123"}
    result = extract_cognito_sub(event)
    assert result == "user-123"
```

```java
// Spring Service 단위 테스트
@Test
void syncCanvas_success() {
    // Given
    when(lambdaClient.invoke(any())).thenReturn(mockResponse);

    // When
    CanvasSyncResponse response = canvasSyncService.syncCanvas("user-123");

    // Then
    assertThat(response.getCoursesCount()).isEqualTo(5);
}
```

---

#### Level 2: Integration Tests (통합 테스트)

**목적**: 여러 컴포넌트가 함께 동작하는지 검증

**범위**:
- Lambda → SQS → Spring Service → DB
- Spring Service → Lambda → 외부 API
- SQS 메시지 발행 → 구독 → 처리

**특징**:
- **실제 의존성**: LocalStack, Testcontainers 사용
- **중간 속도**: 분 단위 실행
- **중간 수**: 전체 테스트의 15-20%
- **Docker 필요**: docker-compose 기반

**기술 스택**:
- Testcontainers (MySQL, LocalStack)
- Docker Compose (acceptance.yml)
- Spring Boot Test (@SpringBootTest)

**예시**:
```java
@SpringBootTest
@ActiveProfiles("integration")
class CanvasSyncIntegrationTest {

    @Test
    void syncCanvas_storesCoursesInDatabase() {
        // Given: Lambda deployed, SQS configured

        // When: POST /v1/sync/canvas
        CanvasSyncResponse response = syncController.syncCanvas(jwt);

        // Then: Wait for SQS processing
        await().atMost(10, SECONDS).untilAsserted(() -> {
            List<Course> courses = courseRepository.findAll();
            assertThat(courses).hasSize(5);
        });
    }
}
```

---

#### Level 3: E2E / Acceptance Tests (인수 테스트)

**목적**: 실제 사용자 시나리오 전체 플로우 검증

**범위**:
- 회원가입 → 로그인 → Canvas 토큰 등록 → 수동 동기화 → 조회
- 과제 생성 → 일정 변환 → 할일 생성 → 상태 업데이트
- 그룹 생성 → 멤버 초대 → 공유 일정 조회

**특징**:
- **End-to-End**: 프론트엔드 제외한 전체 백엔드 스택
- **느림**: 분-시간 단위 실행
- **적은 수**: 전체 테스트의 5-10%
- **실제 환경**: docker-compose.acceptance.yml 사용

**기술 스택**:
- pytest (Python)
- requests (HTTP 클라이언트)
- docker-compose.acceptance.yml

**예시**:
```python
def test_canvas_sync_end_to_end():
    # 1. 회원가입
    signup_response = requests.post(f"{API_URL}/auth/signup", json={
        "email": "test@example.com",
        "password": "password123",
        "name": "Test User"
    })
    assert signup_response.status_code == 200

    # 2. 로그인 (JWT 획득)
    signin_response = requests.post(f"{API_URL}/auth/signin", json={
        "email": "test@example.com",
        "password": "password123"
    })
    jwt = signin_response.json()["accessToken"]

    # 3. Canvas 토큰 등록
    token_response = requests.post(
        f"{API_URL}/credentials/canvas",
        headers={"Authorization": f"Bearer {jwt}"},
        json={"canvasToken": CANVAS_TOKEN}
    )
    assert token_response.status_code == 200

    # 4. 수동 동기화
    sync_response = requests.post(
        f"{API_URL}/sync/canvas",
        headers={"Authorization": f"Bearer {jwt}"}
    )
    assert sync_response.status_code == 200
    assert sync_response.json()["coursesCount"] > 0

    # 5. Course 조회
    time.sleep(5)  # SQS 처리 대기
    courses_response = requests.get(
        f"{API_URL}/courses",
        headers={"Authorization": f"Bearer {jwt}"}
    )
    assert len(courses_response.json()) > 0
```

---

## 3. 테스트 환경

### 3.1 환경별 구성

| 환경 | 목적 | 사용 계층 | 특징 |
|------|------|-----------|------|
| **로컬 (단위)** | 개발자 PC | Unit Tests | 외부 의존성 모킹 |
| **로컬 (통합)** | docker-compose | Integration Tests | LocalStack + MySQL |
| **CI (Acceptance)** | GitHub Actions | E2E Tests | docker-compose.acceptance.yml |
| **Staging** | 배포 전 검증 | Manual E2E | 실제 AWS 인프라 |

### 3.2 테스트 데이터 관리

**원칙**:
- 각 테스트는 독립적으로 실행 가능해야 함
- 테스트 간 데이터 공유 금지
- 테스트 종료 시 데이터 정리 (cleanup)

**전략**:
- **Unit**: Mock 데이터 (코드 내 정의)
- **Integration**: Testcontainers (휘발성 DB)
- **E2E**: 전용 테스트 계정 (자동 생성/삭제)

**예시**:
```python
# pytest fixture로 테스트 데이터 관리
@pytest.fixture
def test_user(db_session):
    """테스트용 사용자 생성 및 자동 정리"""
    user = User(email="test@example.com", name="Test User")
    db_session.add(user)
    db_session.commit()

    yield user

    # 테스트 종료 후 자동 삭제
    db_session.delete(user)
    db_session.commit()
```

---

## 4. 점진적 테스트 실행

### 4.1 단계별 검증 전략

**목표**: 각 레벨을 통과한 후 다음 레벨로 진행

```
┌─────────────────────────────────────────────────────┐
│ Level 1: Unit Tests                                 │
│ ✓ Lambda 함수 논리 검증                              │
│ ✓ Service 클래스 비즈니스 로직 검증                   │
│ ✓ DTO 직렬화/역직렬화 검증                           │
└─────────────────────────────────────────────────────┘
                      ↓ PASS
┌─────────────────────────────────────────────────────┐
│ Level 2: Integration Tests                          │
│ ✓ Lambda + SQS + Service 통합 검증                   │
│ ✓ SQS 메시지 발행/구독 검증                          │
│ ✓ DB 저장 및 조회 검증                               │
└─────────────────────────────────────────────────────┘
                      ↓ PASS
┌─────────────────────────────────────────────────────┐
│ Level 3: E2E / Acceptance Tests                     │
│ ✓ 전체 사용자 시나리오 검증                          │
│ ✓ 회원가입 → 토큰 등록 → 동기화 → 조회               │
│ ✓ 실제 Canvas API 연동 검증                          │
└─────────────────────────────────────────────────────┘
                      ↓ PASS
┌─────────────────────────────────────────────────────┐
│ ✅ 배포 준비 완료                                    │
│ 프론트엔드 연동 및 실제 환경 배포 가능                │
└─────────────────────────────────────────────────────┘
```

### 4.2 테스트 실행 스크립트

**개별 실행**:
```bash
# Level 1: Unit Tests (빠름, 30초)
./scripts/test/test-unit.sh

# Level 2: Integration Tests (중간, 5분)
./scripts/test/test-integration.sh

# Level 3: E2E Tests (느림, 10분)
./scripts/test/test-e2e.sh
```

**점진적 실행**:
```bash
# 모든 레벨 순차 실행 (실패 시 중단)
./scripts/test/test-progressive.sh
```

**All-in-One 실행** (향후 구현):
```bash
# 모든 테스트 한 번에 실행 + 리포트 생성
./scripts/test/test-all.sh
```

### 4.3 출력 예시

```
========================================
UniSync Progressive Test Suite
========================================

[1/3] Running Unit Tests...
  ✓ Lambda handler tests (10 tests, 2s)
  ✓ Service layer tests (25 tests, 5s)
  ✓ DTO serialization tests (8 tests, 1s)
  → Unit Tests PASSED (43 tests, 8s)

[2/3] Running Integration Tests...
  ⏳ Starting LocalStack...
  ⏳ Starting MySQL...
  ✓ Lambda + SQS integration (3 tests, 45s)
  ✓ Course-Service integration (5 tests, 30s)
  ✓ Database operations (4 tests, 20s)
  → Integration Tests PASSED (12 tests, 2m 15s)

[3/3] Running E2E Tests...
  ⏳ Starting docker-compose.acceptance.yml...
  ✓ User signup and login (1 test, 10s)
  ✓ Canvas token registration (1 test, 5s)
  ✓ Manual sync end-to-end (1 test, 30s)
  ✓ Course and assignment retrieval (1 test, 10s)
  → E2E Tests PASSED (4 tests, 5m 30s)

========================================
✅ ALL TESTS PASSED
Total: 59 tests, 8m 15s
Ready for deployment!
========================================
```

---

## 5. 테스트 작성 가이드

### 5.1 네이밍 규칙

**원칙**: 테스트 이름만 보고도 무엇을 테스트하는지 알 수 있어야 함

**형식**: `{메서드명}_{시나리오}_{예상결과}`

**예시**:
```java
// Good
@Test
void syncCanvas_withValidCognitoSub_returnsCoursesCount() { }

@Test
void syncCanvas_withInvalidToken_throwsCanvasSyncException() { }

// Bad
@Test
void test1() { }

@Test
void testSync() { }
```

### 5.2 AAA 패턴 (Arrange-Act-Assert)

```java
@Test
void syncCanvas_withValidCognitoSub_returnsCoursesCount() {
    // Arrange (준비)
    String cognitoSub = "user-123";
    when(lambdaClient.invoke(any())).thenReturn(mockLambdaResponse);

    // Act (실행)
    CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub);

    // Assert (검증)
    assertThat(response.getSuccess()).isTrue();
    assertThat(response.getCoursesCount()).isEqualTo(5);
    assertThat(response.getAssignmentsCount()).isEqualTo(23);
}
```

### 5.3 Given-When-Then (BDD 스타일)

```python
def test_lambda_handler_with_valid_event():
    # Given
    event = {
        "cognitoSub": "user-123"
    }
    mock_canvas_api.return_value = [
        {"id": 1, "name": "Course 1"},
        {"id": 2, "name": "Course 2"}
    ]

    # When
    response = lambda_handler(event, None)

    # Then
    assert response["statusCode"] == 200
    assert response["body"]["coursesCount"] == 2
```

### 5.4 테스트 커버리지 목표

| 계층 | 목표 커버리지 |
|------|---------------|
| Lambda 핸들러 | 80% 이상 |
| Service 레이어 | 90% 이상 |
| Controller | 70% 이상 |
| DTO | 100% |
| 전체 | 80% 이상 |

---

## 6. CI/CD 통합

### 6.1 GitHub Actions 워크플로우

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
      - uses: actions/checkout@v3
      - name: Run Unit Tests
        run: ./scripts/test/test-unit.sh

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v3
      - name: Start LocalStack
        run: docker-compose up -d localstack mysql
      - name: Run Integration Tests
        run: ./scripts/test/test-integration.sh

  e2e-tests:
    runs-on: ubuntu-latest
    needs: integration-tests
    steps:
      - uses: actions/checkout@v3
      - name: Run E2E Tests
        run: ./scripts/test/test-e2e.sh
```

### 6.2 배포 전 필수 조건

**main 브랜치 병합 조건**:
- ✅ 모든 Unit Tests 통과
- ✅ 모든 Integration Tests 통과
- ✅ 모든 E2E Tests 통과
- ✅ 코드 리뷰 승인 (1명 이상)
- ✅ 커버리지 80% 이상

---

## 참고 문서

- [Canvas 동기화 테스트](./canvas-sync.md#테스트-계획) - Canvas-sync 기능별 테스트 상세
- [인수 테스트](./acceptance-test.md) - 기존 E2E 테스트 현황
- [테스트 실행 가이드](../../tests/README.md) - 실행 방법 및 트러블슈팅
