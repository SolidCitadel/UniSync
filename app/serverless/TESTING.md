# Serverless Lambda 테스트 가이드

Lambda 함수의 단위 테스트부터 전체 워크플로우 E2E 테스트까지 모든 테스트 방법을 설명합니다.

## 목차

1. [빠른 시작](#빠른-시작) - 1분 만에 테스트 실행
2. [테스트 유형 개요](#테스트-유형-개요) - 어떤 테스트를 언제 실행할까?
3. [단위 테스트](#단위-테스트) - Mock을 사용한 빠른 함수 테스트
4. [Canvas API 테스트](#canvas-api-테스트) - 실제 Canvas 연동 확인
5. [LocalStack 통합 테스트](#localstack-통합-테스트) - Lambda 배포 및 호출
6. [E2E 테스트](#e2e-테스트) - 전체 워크플로우 검증
7. [문제 해결](#문제-해결) - 자주 발생하는 문제와 해결 방법

---

## 빠른 시작

### 통합 테스트 런처 사용

```bash
# 1. 프로젝트 루트에서 venv 생성 (한 번만)
python -m venv venv

# 2. venv 활성화
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# 3. 의존성 설치 (한 번만)
pip install -r app/serverless/requirements-dev.txt

# 4. 통합 테스트 런처 실행
python scripts/test-all.py
```

**대화형 메뉴**에서 원하는 테스트를 선택하세요:
- `[1]` 단위 테스트 - 모든 Lambda 함수의 단위 테스트 실행 (~2초)
- `[2]` Canvas API 테스트 - 실제 Canvas API 연동 확인 (~5초)
- `[3]` LocalStack 통합 테스트 - Lambda 배포 및 호출 테스트 (~30초)
- `[4]` 모두 실행 - 1 → 2 → 3 순서로 모든 테스트 실행

**예상 결과**: 21개 테스트 모두 통과 ✅

---

## 테스트 유형 개요

| 테스트 유형 | 목적 | 실행 시간 | 외부 의존성 | 사용 시점 |
|------------|------|----------|------------|----------|
| **단위 테스트** | 함수 로직 검증 | ~2초 | ❌ 없음 (Mock) | 개발 중 (매번) |
| **Canvas API 테스트** | 실제 Canvas 연동 확인 | ~5초 | ✅ Canvas 토큰 | PR 전 |
| **LocalStack 통합** | Lambda 배포/호출 검증 | ~30초 | ✅ Docker | 배포 전 |
| **E2E 테스트** | 전체 워크플로우 검증 | ~1분 | ✅ 모든 서비스 | 프로덕션 배포 전 |

### 권장 워크플로우

```
개발 중:     단위 테스트 (빠른 피드백)
           ↓
PR 전:      Canvas API 테스트 (실제 연동 확인)
           ↓
배포 전:     LocalStack 통합 테스트 (Lambda 검증)
           ↓
프로덕션:    E2E 테스트 (전체 시나리오)
```

---

## 단위 테스트

Mock을 사용하여 외부 의존성 없이 함수 로직만 빠르게 테스트합니다.

### 환경 설정

```bash
# 1. 프로젝트 루트에서 venv 생성
cd C:\Users\teddy\Documents\Workspace\UniSync
python -m venv venv

# 2. venv 활성화
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# 3. 의존성 설치
pip install -r app/serverless/requirements-dev.txt
```

#### 설치되는 패키지

- `boto3`: AWS SDK (SQS, Lambda 등)
- `requests`: HTTP 클라이언트
- `pytest`: 테스트 프레임워크
- `pytest-mock`: Mocking 라이브러리
- `pytest-cov`: 코드 커버리지 측정
- `moto`: AWS 서비스 mocking

### 테스트 실행

통합 테스트 런처를 사용하여 단위 테스트를 실행합니다:

```bash
# venv 활성화 후
python scripts/test-all.py
```

대화형 메뉴에서 **[1] 단위 테스트**를 선택하세요.

### 테스트 커버리지

#### Canvas Sync Lambda (9개 테스트)
- ✅ `lambda_handler`: Step Functions 이벤트 처리
- ✅ `get_canvas_token`: User-Service API 호출
- ✅ `fetch_canvas_assignments`: Canvas 과제 조회
- ✅ `fetch_canvas_announcements`: Canvas 공지 조회
- ✅ `fetch_canvas_submissions`: Canvas 제출물 조회
- ✅ `send_to_sqs`: SQS 메시지 전송
- ✅ 증분 동기화 (`updated_since` 파라미터)
- ✅ 에러 핸들링

#### LLM Lambda (12개 테스트)
- ✅ `lambda_handler`: SQS 이벤트 배치 처리
- ✅ `handle_assignment_analysis`: 과제 분석
- ✅ `handle_submission_validation`: 제출물 검증
- ✅ `call_llm`: LLM API 호출
- ✅ `send_to_sqs`: SQS 메시지 전송
- ✅ 이벤트 타입별 라우팅
- ✅ LLM 응답 파싱

### Mock 전략

모든 외부 의존성은 Mock으로 대체됩니다:

1. **HTTP 요청**: `requests.get/post` mock
2. **AWS 서비스**: `boto3.client` mock
3. **환경 변수**: `pytest` fixture로 자동 주입

**Mock 예시**:
```python
@patch('src.handler.requests.get')
def test_get_canvas_token_success(mock_requests_get):
    # Given: User-Service API mock
    mock_response = MagicMock()
    mock_response.json.return_value = {'accessToken': 'token123'}
    mock_requests_get.return_value = mock_response

    # When: 토큰 조회
    token = get_canvas_token(user_id=10)

    # Then: 올바른 토큰 반환
    assert token == 'token123'
```

---

## Canvas API 테스트

실제 Canvas API에 직접 요청하여 연동을 확인합니다.

### 준비사항

#### 1. Canvas API 토큰 발급

1. Canvas LMS 로그인
2. **Account** → **Settings**
3. **Approved Integrations** → **+ New Access Token**
4. Purpose: "UniSync Integration"
5. **Generate Token** → 토큰 복사

#### 2. 환경 변수 설정

**프로젝트 루트의 `.env` 파일 사용 (추천)**

`.env` 파일에 Canvas 토큰을 추가하세요:

```bash
# C:\Users\teddy\Documents\Workspace\UniSync\.env

# Canvas API (개발/테스트용)
CANVAS_API_BASE_URL=https://canvas.instructure.com/api/v1
CANVAS_API_TOKEN=your-canvas-api-token-here  # 실제 토큰으로 교체
```

테스트 스크립트가 자동으로 `.env` 파일을 읽습니다 (`python-dotenv` 사용).

**또는 직접 환경 변수로 설정**:

```bash
# Windows (PowerShell)
$env:CANVAS_API_TOKEN="your-canvas-token-here"

# Linux/Mac
export CANVAS_API_TOKEN="your-canvas-token-here"
```

### 테스트 실행

통합 테스트 런처를 사용하여 Canvas API 테스트를 실행합니다:

```bash
# venv 활성화 후
python scripts/test-all.py
```

대화형 메뉴에서 **[2] Canvas API 테스트**를 선택하세요.

### 예상 출력

```
==============================================================
  Canvas API 연동 테스트
==============================================================

>>> Step 1: Canvas API 인증 테스트
  ✅ 인증 성공!
  - User ID: 12345
  - Name: 홍길동

>>> Step 2: 과목 목록 조회
  ✅ 3개의 과목을 찾았습니다:
  [1] 웹 프로그래밍 (Course ID: 101)

>>> Step 3: 과제 목록 조회
  ✅ 5개의 과제를 찾았습니다:
  [1] 중간고사 프로젝트 (Due: 2025-11-15)

>>> Step 4: 공지사항 조회
  ✅ 2개의 공지사항을 찾았습니다

>>> Step 5: 제출물 조회
  ✅ 3개의 제출물을 찾았습니다

==============================================================
  테스트 결과 요약
==============================================================
  ✅ 인증: 성공
  ✅ 과목 수: 3개
  ✅ 과제 수: 5개
  ✅ 공지 수: 2개
  ✅ 제출물 수: 3개

  💾 결과 저장됨: canvas-api-test-result.json
==============================================================
```

### 검증 항목

- Canvas API 인증 성공
- 과목 목록 조회
- 과제 목록 조회 (assignments)
- 공지사항 조회 (announcements)
- 제출물 조회 (submissions)
- 응답 데이터 구조 확인

---

## LocalStack 통합 테스트

LocalStack에 Lambda를 배포하고 실제로 호출하여 통합 테스트를 수행합니다.

### 준비사항

#### 1. LocalStack 시작

```bash
# Docker Compose로 LocalStack 시작
docker-compose up -d localstack

# LocalStack 상태 확인
curl http://localhost:4566/_localstack/health
```

#### 2. 인프라 초기화

```bash
# SQS 큐, IAM 역할 생성
bash scripts/setup-localstack.sh
```

**예상 출력**:
```
🚀 LocalStack 초기화 시작...
📦 SQS 큐 생성 중...
✅ SQS 큐 생성 완료
🔧 IAM 역할 생성 중...
✅ IAM 역할 생성 완료

SQS 큐 목록:
  - assignment-events-queue
  - submission-events-queue
  - task-creation-queue

✨ LocalStack 초기화 완료!
```

### 통합 테스트 실행

통합 테스트 런처를 사용하여 LocalStack 통합 테스트를 실행합니다:

```bash
# venv 활성화 후
python scripts/test-all.py
```

대화형 메뉴에서 **[3] LocalStack Lambda 통합 테스트**를 선택하세요.

### 예상 출력

```
======================================================================
  LocalStack Lambda 통합 테스트
======================================================================

>>> Step 1: LocalStack 상태 확인
  ✅ LocalStack 실행 중: http://localhost:4566

>>> Step 2: SQS 큐 확인
  ✅ 4개의 SQS 큐를 찾았습니다

>>> Step 3: Lambda 함수 배포
  📦 Lambda 배포 중... (약 30초 소요)
  ✅ Lambda 배포 완료

>>> Step 4: Canvas Sync Lambda 호출 테스트
  📤 Lambda 호출 중...
  📥 Lambda 응답:
    - Status Code: 200
    - Payload: { ... }

  ⚠️  Lambda 실행 중 에러 발생
  💡 User-Service가 실행되지 않았거나 Canvas 토큰이 없기 때문 (정상)

>>> Step 5: SQS 메시지 확인
  ℹ️  메시지가 없습니다 (정상)

>>> Step 6: LLM Lambda 호출 테스트
  ✅ Lambda 실행 성공!

======================================================================
  테스트 결과 요약
======================================================================
  ✅ LocalStack: 정상
  ✅ SQS 큐: 생성됨
  ⚠️  Canvas Sync Lambda: 에러 발생 (정상)
  ✅ LLM Lambda: 성공

  💡 다음 단계:
     1. User-Service를 시작하세요
     2. Canvas 토큰을 User-Service에 저장하세요
     3. 다시 테스트하여 전체 워크플로우 검증
======================================================================
```

### 검증 항목

- LocalStack 정상 실행
- SQS 큐 생성 확인
- Lambda 함수 배포 성공
- Lambda 직접 호출 성공
- SQS 메시지 전송 확인

### 수동 Lambda 호출

```bash
# Canvas Sync Lambda 호출
awslocal lambda invoke \
  --function-name canvas-sync-lambda \
  --payload '{"courseId":123,"canvasCourseId":"test_456","leaderUserId":5}' \
  response.json

# 응답 확인
cat response.json

# SQS 메시지 확인
awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/assignment-events-queue
```

---

## E2E 테스트

전체 워크플로우 (User-Service + Lambda + Step Functions)를 검증합니다.

### 준비사항

#### 1. 모든 서비스 시작

```bash
# LocalStack
docker-compose up -d localstack

# User-Service (별도 터미널)
cd app/backend/user-service
./gradlew bootRun

# Course-Service (별도 터미널)
cd app/backend/course-service
./gradlew bootRun

# Sync-Service (별도 터미널)
cd app/backend/sync-service
./gradlew bootRun
```

#### 2. Canvas 토큰 저장

```bash
# 회원가입
curl -X POST http://localhost:8081/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPassword123!",
    "name": "테스트 사용자"
  }'

# 로그인
curl -X POST http://localhost:8081/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPassword123!"
  }'
# 응답에서 accessToken 복사

# Canvas 토큰 저장
curl -X POST http://localhost:8081/credentials \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "provider": "CANVAS",
    "accessToken": "YOUR_CANVAS_TOKEN"
  }'
```

### Step Functions 워크플로우 테스트

```bash
# 1. Step Functions 상태 머신 생성
awslocal stepfunctions create-state-machine \
  --name canvas-sync-workflow \
  --definition file://app/serverless/step-functions/canvas-sync-workflow.json \
  --role-arn arn:aws:iam::000000000000:role/stepfunctions-execution-role

# 2. 워크플로우 실행
awslocal stepfunctions start-execution \
  --state-machine-arn arn:aws:states:ap-northeast-2:000000000000:stateMachine:canvas-sync-workflow \
  --input '{}'

# 3. 실행 상태 확인
awslocal stepfunctions list-executions \
  --state-machine-arn arn:aws:states:ap-northeast-2:000000000000:stateMachine:canvas-sync-workflow

# 4. 상세 실행 기록 조회
awslocal stepfunctions describe-execution \
  --execution-arn arn:aws:states:...:execution:canvas-sync-workflow:xxx
```

### EventBridge 스케줄링 테스트

```bash
# EventBridge 규칙 생성 (5분마다 실행)
awslocal events put-rule \
  --name canvas-sync-schedule \
  --schedule-expression "rate(5 minutes)"

# EventBridge → Step Functions 연결
awslocal events put-targets \
  --rule canvas-sync-schedule \
  --targets "Id=1,Arn=arn:aws:states:ap-northeast-2:000000000000:stateMachine:canvas-sync-workflow"

# 규칙 확인
awslocal events list-rules
```

---

## 문제 해결

### Q1. venv를 만들었는데 pytest를 찾을 수 없어요

```bash
# venv 활성화 확인
which python  # Linux/Mac
where python  # Windows

# venv 활성화
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# 의존성 재설치
pip install -r app/serverless/requirements-dev.txt
```

### Q2. Canvas API 테스트에서 401 에러

**원인**: 토큰이 잘못되었거나 만료됨

**해결**:
- Canvas 토큰 재발급
- 토큰이 올바르게 환경 변수에 설정되었는지 확인
- Canvas 도메인 확인 (학교별로 다를 수 있음)

```bash
# 토큰 테스트
curl https://canvas.instructure.com/api/v1/users/self \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Q3. LocalStack이 시작되지 않아요

```bash
# Docker 확인
docker --version

# LocalStack 로그 확인
docker-compose logs localstack

# LocalStack 재시작
docker-compose restart localstack

# 포트 충돌 확인
netstat -ano | findstr 4566  # Windows
lsof -i :4566  # Linux/Mac
```

### Q4. Lambda 배포가 실패해요

**Windows**: Git Bash 필요

```bash
# Git Bash 설치
https://git-scm.com/downloads

# 또는 WSL 사용
wsl bash scripts/deploy-lambda.sh local
```

### Q5. ImportError: No module named 'src'

**원인**: PYTHONPATH가 설정되지 않았거나 잘못된 경로에서 실행

**해결**:
```bash
# 프로젝트 루트에서 통합 테스트 런처 실행
cd C:\Users\teddy\Documents\Workspace\UniSync
python scripts/test-all.py
```

pytest.ini에 PYTHONPATH가 이미 설정되어 있으므로, 통합 테스트 런처를 사용하면 자동으로 해결됩니다.

### Q6. SQS 메시지가 없어요

**원인**:
- Canvas API 호출 실패
- 새로운 과제가 없음
- Lambda 실행 실패

**확인 방법**:
```bash
# Lambda 로그 확인 (LocalStack Pro 필요)
awslocal logs tail /aws/lambda/canvas-sync-lambda

# SQS 큐 속성 확인
awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/assignment-events-queue \
  --attribute-names All
```

---

## 참고 자료

### 공식 문서
- [pytest 공식 문서](https://docs.pytest.org/)
- [unittest.mock 가이드](https://docs.python.org/3/library/unittest.mock.html)
- [moto (AWS mocking)](https://docs.getmoto.org/)
- [pytest-cov 사용법](https://pytest-cov.readthedocs.io/)
- [LocalStack 문서](https://docs.localstack.cloud/)
- [Canvas API 문서](https://canvas.instructure.com/doc/api/)

### 프로젝트 문서
- [README.md](./README.md) - 전체 개요
- `.env.example` - 환경 변수 설정 예시