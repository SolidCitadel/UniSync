# Serverless Lambda 테스트 가이드

Lambda 함수의 단위 테스트부터 통합 테스트까지 모든 테스트 방법을 설명합니다.

> **전체 테스트 전략은 [docs/features/testing-strategy.md](../../docs/features/testing-strategy.md)를 참고하세요.**

## 빠른 시작

환경 설정 및 실행 방법은 [README.md](./README.md#빠른-시작)를 참고하세요.

```bash
# 통합 테스트 런처 실행
python scripts/test-all.py
```

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

### 테스트 실행

```bash
python scripts/test-all.py
```

대화형 메뉴에서 **[1] 단위 테스트**를 선택하세요.

### 테스트 커버리지

#### Canvas Sync Lambda (9개 테스트)
- ✅ lambda_handler: Step Functions 이벤트 처리
- ✅ get_canvas_token: User-Service API 호출
- ✅ fetch_canvas_assignments/announcements/submissions
- ✅ send_to_sqs: SQS 메시지 전송
- ✅ 증분 동기화, 에러 핸들링

#### LLM Lambda (12개 테스트)
- ✅ lambda_handler: SQS 이벤트 배치 처리
- ✅ handle_assignment_analysis: 과제 분석
- ✅ handle_submission_validation: 제출물 검증
- ✅ call_llm: LLM API 호출
- ✅ 이벤트 타입별 라우팅, LLM 응답 파싱

---

## Canvas API 테스트

실제 Canvas API에 직접 요청하여 연동을 확인합니다.

### 준비사항

#### Canvas API 토큰 발급

1. Canvas LMS 로그인
2. **Account** → **Settings**
3. **Approved Integrations** → **+ New Access Token**
4. Purpose: "UniSync Integration"
5. **Generate Token** → 토큰 복사

#### 환경 변수 설정

프로젝트 루트의 `.env` 파일에 추가:

```bash
CANVAS_API_BASE_URL=https://canvas.instructure.com/api/v1
CANVAS_API_TOKEN=your-canvas-api-token-here
```

### 테스트 실행

```bash
python scripts/test-all.py
```

대화형 메뉴에서 **[2] Canvas API 테스트**를 선택하세요.

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
docker-compose up -d localstack

# 상태 확인
curl http://localhost:4566/_localstack/health
```

#### 2. 인프라 초기화

```bash
bash scripts/setup-localstack.sh
```

### 통합 테스트 실행

```bash
python scripts/test-all.py
```

대화형 메뉴에서 **[3] LocalStack Lambda 통합 테스트**를 선택하세요.

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
```

#### 2. Canvas 토큰 저장

```bash
# 회원가입
curl -X POST http://localhost:8081/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPassword123!","name":"테스트 사용자"}'

# 로그인 (accessToken 복사)
curl -X POST http://localhost:8081/auth/signin \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPassword123!"}'

# Canvas 토큰 저장
curl -X POST http://localhost:8081/credentials \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"provider":"CANVAS","accessToken":"YOUR_CANVAS_TOKEN"}'
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

### Q6. SQS 메시지가 없어요

**원인**:
- Canvas API 호출 실패
- 새로운 과제가 없음
- Lambda 실행 실패

**확인 방법**:
```bash
# SQS 큐 속성 확인
awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/assignment-events-queue \
  --attribute-names All
```

---

## 참고 자료

### 공식 문서
- [pytest 공식 문서](https://docs.pytest.org/)
- [LocalStack 문서](https://docs.localstack.cloud/)
- [Canvas API 문서](https://canvas.instructure.com/doc/api/)

### 프로젝트 문서
- **[테스트 전략](../../docs/features/testing-strategy.md)** - 전체 테스트 전략 및 계층 구조
- [README.md](./README.md) - 전체 개요
- [Canvas 동기화 설계](../../docs/features/canvas-sync.md) - Lambda 상세 설계
- `../../.env.local.example` - 환경 변수 설정 예시