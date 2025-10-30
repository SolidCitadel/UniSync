# UniSync Serverless Components

Canvas LMS 동기화 및 AI 분석을 위한 서버리스 컴포넌트입니다.

## 목차

1. [빠른 시작](#빠른-시작)
2. [프로젝트 구조](#프로젝트-구조)
3. [Lambda 함수](#lambda-함수)
4. [Step Functions 워크플로우](#step-functions-워크플로우)
5. [로컬 개발 환경](#로컬-개발-환경)
6. [테스트](#테스트)
7. [배포](#배포)
8. [환경 변수](#환경-변수)

---

## 빠른 시작

### 1. 통합 테스트 런처 (가장 쉬움)

```bash
# 1. venv 생성 및 활성화
python -m venv venv
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# 2. 의존성 설치
pip install -r requirements-dev.txt

# 3. 통합 테스트 런처 실행
python ../../scripts/test/test-all.py
```

**대화형 메뉴**에서 원하는 테스트 선택:
- 단위 테스트 / Canvas API / LocalStack 통합 / 모두 실행

### 2. LocalStack에 배포 (선택사항)

```bash
# LocalStack 시작
docker-compose up -d localstack

# 인프라 초기화
bash ../../scripts/infra/setup-localstack.sh

# Lambda 배포
bash ../../scripts/infra/deploy-lambda.sh local
```

### 더 알아보기

자세한 테스트 방법은 **[TESTING.md](./TESTING.md)**를 참고하세요.

---

## 프로젝트 구조

```
serverless/
├── canvas-sync-lambda/          # Canvas API 호출
│   ├── src/
│   │   └── handler.py          # Lambda 핸들러
│   ├── tests/
│   │   └── test_handler.py     # 단위 테스트 (9개)
│   └── requirements.txt
│
├── llm-lambda/                  # LLM 분석
│   ├── src/
│   │   └── handler.py
│   ├── tests/
│   │   └── test_handler.py     # 단위 테스트 (12개)
│   └── requirements.txt
│
├── step-functions/
│   └── canvas-sync-workflow.json  # Step Functions 정의
│
├── requirements-dev.txt         # 개발/테스트 의존성
├── README.md                    # 이 문서
└── TESTING.md                   # 테스트 가이드
```

---

## Lambda 함수

### 1. Canvas Sync Lambda

**역할**:
- Canvas API 호출 (과제, 공지, 제출물)
- Leader의 Canvas 토큰 조회 (User-Service)
- SQS로 이벤트 전송

**트리거**: Step Functions

**환경 변수**:
```bash
USER_SERVICE_URL=http://localhost:8081
CANVAS_API_BASE_URL=https://canvas.instructure.com/api/v1
SERVICE_AUTH_TOKEN=local-dev-token
AWS_REGION=ap-northeast-2
SQS_ENDPOINT=http://localhost:4566  # LocalStack
```

**Input 예시**:
```json
{
  "courseId": 123,
  "canvasCourseId": "canvas_456",
  "leaderUserId": 5,
  "lastSyncedAt": "2025-10-29T12:00:00Z"
}
```

**Output 예시**:
```json
{
  "statusCode": 200,
  "body": {
    "courseId": 123,
    "assignmentsCount": 10,
    "submissionsCount": 5,
    "eventsSent": 15
  }
}
```

### 2. LLM Lambda

**역할**:
- 과제 설명 분석 → Task/Subtask 생성
- 제출물 유효성 검증

**트리거**: SQS (`assignment-events-queue`, `submission-events-queue`)

**환경 변수**:
```bash
LLM_API_URL=https://api.openai.com/v1/chat/completions
LLM_API_KEY=sk-...
AWS_REGION=ap-northeast-2
SQS_ENDPOINT=http://localhost:4566
```

**Input 예시** (SQS 이벤트):
```json
{
  "Records": [
    {
      "body": "{\"eventType\":\"ASSIGNMENT_CREATED\",\"courseId\":123,...}"
    }
  ]
}
```

**Output 예시**:
```json
{
  "statusCode": 200,
  "body": "LLM 처리 완료"
}
```

---

## Step Functions 워크플로우

### 실행 주기

EventBridge로 **5분마다** 자동 실행

### 워크플로우 흐름

```
EventBridge (5분마다)
  ↓
Step Functions: canvas-sync-workflow
  ↓
1. GetLeaderCourses
   → Course-Service API: Leader 과목 목록 조회
  ↓
2. ProcessCourses (Map - 최대 5개 동시)
   ├─ FetchCanvasData (Canvas Sync Lambda)
   ├─ CheckNewAssignments
   └─ UpdateSyncStatus
  ↓
3. CompleteSyncWorkflow
```

### 데이터 흐름

```
Canvas Sync Lambda
  → SQS: assignment-events-queue
  → LLM Lambda (트리거)
  → LLM API (과제 분석)
  → SQS: task-creation-queue
  → Sync-Service (Tasks 저장)
```

---

## 로컬 개발 환경

### 요구사항

- Python 3.11+
- Docker & Docker Compose
- LocalStack Pro (Step Functions 사용 시)

### 환경 설정

```bash
# 1. venv 생성
python -m venv venv

# 2. venv 활성화
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# 3. 의존성 설치
pip install -r requirements-dev.txt
```

### LocalStack 시작

```bash
# 1. LocalStack 시작
docker-compose up -d localstack

# 2. 인프라 초기화 (SQS, IAM 역할)
bash ../../scripts/infra/setup-localstack.sh

# 3. Lambda 배포
bash ../../scripts/infra/deploy-lambda.sh local

# 4. Step Functions 생성
awslocal stepfunctions create-state-machine \
  --name canvas-sync-workflow \
  --definition file://step-functions/canvas-sync-workflow.json \
  --role-arn arn:aws:iam::000000000000:role/stepfunctions-execution-role
```

---

## 테스트

### 테스트 유형

| 테스트 | 실행 시간 | 외부 의존성 | 사용 시점 |
|--------|----------|------------|----------|
| **단위 테스트** | ~2초 | ❌ 없음 | 개발 중 (매번) |
| **Canvas API 테스트** | ~5초 | ✅ Canvas 토큰 | PR 전 |
| **LocalStack 통합** | ~30초 | ✅ Docker | 배포 전 |
| **E2E 테스트** | ~1분 | ✅ 모든 서비스 | 프로덕션 배포 전 |

### 테스트 실행

```bash
# 통합 테스트 런처 사용
python ../../scripts/test/test-all.py
```

대화형 메뉴에서 원하는 테스트를 선택하세요:
- `[1]` 단위 테스트
- `[2]` Canvas API 테스트
- `[3]` LocalStack 통합 테스트
- `[4]` 모두 실행

### 상세 가이드

모든 테스트 방법은 **[TESTING.md](./TESTING.md)**를 참고하세요:

- [빠른 시작](./TESTING.md#빠른-시작) - 1분 만에 테스트 실행
- [단위 테스트](./TESTING.md#단위-테스트) - Mock 기반 함수 테스트
- [Canvas API 테스트](./TESTING.md#canvas-api-테스트) - 실제 Canvas 연동
- [LocalStack 통합](./TESTING.md#localstack-통합-테스트) - Lambda 배포/호출
- [E2E 테스트](./TESTING.md#e2e-테스트) - 전체 워크플로우
- [문제 해결](./TESTING.md#문제-해결) - 자주 발생하는 문제

---

## 배포

### LocalStack (로컬 개발)

```bash
# 전체 배포 스크립트
bash ../../scripts/infra/deploy-lambda.sh local
```

### AWS (프로덕션)

```bash
# TODO: SAM/Terraform/CDK 사용
bash ../../scripts/infra/deploy-lambda.sh production
```

### EventBridge 스케줄링

```bash
# 5분마다 실행
awslocal events put-rule \
  --name canvas-sync-schedule \
  --schedule-expression "rate(5 minutes)"

# Step Functions 연결
awslocal events put-targets \
  --rule canvas-sync-schedule \
  --targets "Id=1,Arn=arn:aws:states:...:stateMachine:canvas-sync-workflow"
```

---

## 환경 변수

### Canvas Sync Lambda

| 변수 | 설명 | 예시 |
|------|------|------|
| `USER_SERVICE_URL` | User-Service API URL | `http://localhost:8081` |
| `CANVAS_API_BASE_URL` | Canvas API Base URL | `https://canvas.instructure.com/api/v1` |
| `SERVICE_AUTH_TOKEN` | 서비스 간 인증 토큰 | `local-dev-token` |
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `SQS_ENDPOINT` | SQS 엔드포인트 (LocalStack) | `http://localhost:4566` |

### LLM Lambda

| 변수 | 설명 | 예시 |
|------|------|------|
| `LLM_API_URL` | LLM API URL | `https://api.openai.com/v1/chat/completions` |
| `LLM_API_KEY` | LLM API Key | `sk-...` |
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `SQS_ENDPOINT` | SQS 엔드포인트 (LocalStack) | `http://localhost:4566` |

### 환경 변수 설정

`.env.example`을 복사하여 `.env.local`로 저장하고 값을 입력하세요:

```bash
cp ../../.env.example ../../.env.local
```

---

## 보안

### 서비스 간 인증

- Lambda → User-Service: `X-Service-Token` 헤더 사용
- User-Service는 서비스 토큰 검증 후 응답

### 토큰 관리

- **Canvas 토큰**: User-Service에 AES-256 암호화 저장
- **LLM API Key**: AWS Secrets Manager (프로덕션)
- **서비스 토큰**: 환경 변수 (로컬), Secrets Manager (프로덕션)

---

## TODO

- [ ] SAM/Terraform으로 IaC 구성
- [ ] LLM API 비용 최적화 (캐싱)
- [ ] Step Functions 재시도 전략 개선
- [ ] 과목별 동기화 주기 커스터마이징
- [ ] CI/CD 파이프라인 구축