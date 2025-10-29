# UniSync Serverless Components

Canvas LMS 동기화 및 AI 분석을 위한 서버리스 컴포넌트

## 구조

```
serverless/
├── canvas-sync-lambda/      # Canvas API 호출 Lambda
├── llm-lambda/              # LLM 분석 Lambda
└── step-functions/          # Step Functions 워크플로우
```

## 1. Canvas Sync Lambda

### 역할
- Canvas API를 호출하여 과제/공지/제출물 조회
- Leader의 토큰을 User-Service에서 조회
- SQS로 이벤트 전송

### 환경 변수
```bash
USER_SERVICE_URL=http://localhost:8081
CANVAS_API_BASE_URL=https://canvas.instructure.com/api/v1
SERVICE_AUTH_TOKEN=local-dev-token
AWS_REGION=ap-northeast-2
SQS_ENDPOINT=http://localhost:4566  # LocalStack 사용 시
```

### 로컬 테스트
```bash
# 의존성 설치
cd canvas-sync-lambda
pip install -r requirements.txt

# 테스트 실행
python -c "from src.handler import lambda_handler; print(lambda_handler({...}, None))"
```

## 2. LLM Lambda

### 역할
- SQS 이벤트 트리거로 실행
- 과제 설명 분석 → Task/Subtask 생성
- 제출물 유효성 검증

### 환경 변수
```bash
LLM_API_URL=https://api.openai.com/v1/chat/completions
LLM_API_KEY=sk-...
AWS_REGION=ap-northeast-2
SQS_ENDPOINT=http://localhost:4566
```

### SQS 트리거
- `assignment-events-queue`: 새 과제 분석
- `submission-events-queue`: 제출물 검증

## 3. Step Functions Workflow

### 실행 주기
EventBridge로 5분마다 자동 실행

### 워크플로우 흐름
```
1. GetLeaderCourses
   → Course-Service API 호출
   → Leader 과목 목록 조회

2. ProcessCourses (Map)
   → 각 과목별 병렬 처리 (최대 5개 동시)

   2.1. FetchCanvasData
        → Canvas Sync Lambda 호출
        → Canvas API 데이터 조회
        → SQS로 이벤트 전송

   2.2. CheckNewAssignments
        → 새 과제 있으면 LLM 트리거 (SQS)

   2.3. UpdateSyncStatus
        → Sync-Service에 완료 상태 업데이트

3. CompleteSyncWorkflow
   → 전체 완료
```

## 배포

### LocalStack (로컬 개발)

```bash
# 1. LocalStack 시작 (docker-compose)
docker-compose up -d localstack

# 2. 인프라 초기화 (SQS, IAM)
bash scripts/setup-localstack.sh

# 3. Lambda 배포
bash scripts/deploy-lambda.sh local

# 4. Step Functions 생성
aws --endpoint-url=http://localhost:4566 stepfunctions create-state-machine \
  --name canvas-sync-workflow \
  --definition file://app/serverless/step-functions/canvas-sync-workflow.json \
  --role-arn arn:aws:iam::000000000000:role/stepfunctions-execution-role

# 5. EventBridge 규칙 생성 (5분마다 실행)
aws --endpoint-url=http://localhost:4566 events put-rule \
  --name canvas-sync-schedule \
  --schedule-expression "rate(5 minutes)"

# 6. EventBridge → Step Functions 연결
aws --endpoint-url=http://localhost:4566 events put-targets \
  --rule canvas-sync-schedule \
  --targets "Id"="1","Arn"="arn:aws:states:ap-northeast-2:000000000000:stateMachine:canvas-sync-workflow"
```

### AWS (프로덕션)

```bash
# SAM/Terraform/CDK 사용 (TODO)
bash scripts/deploy-lambda.sh production
```

## 데이터 흐름

```
EventBridge (5분마다)
  ↓
Step Functions: canvas-sync-workflow
  ↓
Canvas Sync Lambda
  ↓ User-Service API 호출
  └→ Canvas Token 조회 (AES-256 복호화)
  ↓ Canvas API 호출
  └→ Assignments, Announcements, Submissions
  ↓
SQS: assignment-events-queue
  ↓
LLM Lambda (트리거)
  ↓ OpenAI API 호출
  └→ Task/Subtask 생성
  ↓
SQS: task-creation-queue
  ↓
Sync-Service (컨슈머)
  └→ Tasks 테이블 저장
```

## 보안

### 서비스 간 인증
- Lambda → User-Service: `X-Service-Token` 헤더
- User-Service는 서비스 토큰 검증 후 응답

### 토큰 관리
- Canvas 토큰: User-Service에 AES-256 암호화 저장
- LLM API Key: AWS Secrets Manager (프로덕션)
- 서비스 토큰: 환경 변수 (로컬), Secrets Manager (프로덕션)

## 모니터링

### CloudWatch Logs
- Lambda 실행 로그
- Step Functions 실행 기록

### CloudWatch Metrics
- Lambda 실행 시간, 에러율
- SQS 메시지 수, Age

## TODO

- [ ] SAM/Terraform으로 IaC 구성
- [ ] LLM API 비용 최적화 (캐싱)
- [ ] Step Functions 재시도 전략 개선
- [ ] 과목별 동기화 주기 커스터마이징