# Serverless

서버리스 워크플로우 및 Lambda 함수.

## 핵심 워크플로우

### Canvas 동기화
```
EventBridge (5분마다)
  → Canvas-Sync-Workflow (Step Functions)
  → Canvas API 폴링 (Leader 토큰)
  → 새 과제 감지
     → SQS: assignment-events-queue
     → Course-Service: Assignment 저장
     → Schedule-Service:
        1. 일정(Schedule) 자동 생성 (과제 마감일)
        2. LLM-Lambda 트리거: 과제 설명 분석
        3. 할일(Todo) + 서브태스크 자동 생성
  → 제출 감지
     → SQS: submission-events-queue
     → LLM-Lambda: 제출물 검증
     → Schedule-Service: 일정/할일 상태 업데이트
```

### Google Calendar 동기화
```
EventBridge
  → Google-Calendar-Sync-Workflow (Step Functions)
  → Google Calendar API 폴링
  → 변경 감지
     → SQS: calendar-events-queue
     → Schedule-Service: User_Schedules 저장
```

## SQS 메시지 스키마

### assignment-events-queue
```json
{
  "eventType": "ASSIGNMENT_CREATED | ASSIGNMENT_UPDATED",
  "canvasAssignmentId": 123456,
  "canvasCourseId": 789,
  "title": "중간고사 프로젝트",
  "dueAt": "2025-11-15T23:59:59",
  "pointsPossible": 100,
  "submissionTypes": "online_upload"
}
```

### submission-events-queue
```json
{
  "eventType": "SUBMISSION_CREATED | SUBMISSION_UPDATED",
  "canvasAssignmentId": 123456,
  "userId": "user-uuid",
  "submittedAt": "2025-11-15T10:30:00",
  "submissionUrl": "s3://bucket/path/to/file"
}
```

### calendar-events-queue
```json
{
  "eventType": "CALENDAR_EVENT_CREATED | CALENDAR_EVENT_UPDATED | CALENDAR_EVENT_DELETED",
  "userId": "user-uuid",
  "externalEventId": "google-cal-event-id",
  "title": "회의",
  "startTime": "2025-11-15T14:00:00",
  "endTime": "2025-11-15T15:00:00"
}
```

공유 모듈 상세: [app/shared/README.md](../shared/README.md)

## Lambda 구조

### canvas-sync-lambda
- Canvas API 폴링 및 과제/제출 감지
- SQS로 이벤트 발행
- 테스트: `app/serverless/canvas-sync-lambda/tests/`

### llm-lambda
- 과제 설명 분석 (할일/서브태스크 생성)
- 제출물 검증 (완료 여부 판단)
- 테스트: `app/serverless/llm-lambda/tests/`

## 테스트

### Lambda 단위 테스트
각 Lambda: `app/serverless/{lambda}/tests/`

```bash
# Lambda 단위 테스트
bash scripts/test/test-unit.sh
```

### 통합 테스트
Lambda 배포/호출: `tests/integration/`
- `test_lambda_integration.py`: LocalStack Lambda 배포/호출
- `test_assignment_flow_with_lambda.py`: SQS → Lambda → Service

### E2E 테스트
전체 플로우: `tests/e2e/`
- `test_canvas_sync_e2e.py`: Canvas API → Lambda → SQS → Service → DB
- `test_canvas_sync_with_jwt_e2e.py`: JWT 인증 포함

테스트 실행:
```bash
# 대화형 메뉴
python scripts/test/test-all.py

# Lambda 단위 테스트
bash scripts/test/test-unit.sh

# E2E 테스트
bash scripts/test/test-e2e.sh
```

자세한 내용: [README.md](./README.md), [TESTING.md](./TESTING.md)
