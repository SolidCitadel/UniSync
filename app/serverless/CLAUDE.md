# Serverless

서버리스 워크플로우 및 Lambda 함수.

## 핵심 워크플로우

### Canvas 동기화 (✅ Phase 1 구현 완료)
```
사용자 동기화 버튼 클릭
  → API Gateway → User-Service: POST /api/v1/sync/canvas
  → User-Service: JWT에서 cognitoSub 추출
  → User-Service → Canvas-Sync-Lambda: AWS SDK로 직접 호출 {"cognitoSub": "..."}
  → Canvas-Sync-Lambda:
     1. User-Service 내부 API로 Canvas 토큰 조회 (복호화된 토큰)
     2. Canvas API 호출 (courses, assignments)
     3. SQS 메시지 발행:
        - lambda-to-courseservice-enrollments (과목 등록 정보)
        - lambda-to-courseservice-assignments (과제 정보)
     4. 동기 응답: {"statusCode": 200, "body": {"coursesCount": 5, "assignmentsCount": 23, ...}}
  → Course-Service: SQS 메시지 consume하여 DB 저장 (비동기)
  → User-Service: Lambda 응답을 클라이언트에 반환
```

**Phase 2 (계획)**: EventBridge 스케줄러로 자동 호출
**Phase 3 (선택)**: LLM 분석 기반 자동 서브태스크 생성 및 제출물 검증

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

> **전체 SQS 아키텍처 및 상세 스키마는 [docs/design/sqs-architecture.md](../../docs/design/sqs-architecture.md)를 참고하세요.**
> 아래는 빠른 참조용 요약입니다.

### lambda-to-courseservice-enrollments (Phase 1)
```json
{
  "cognitoSub": "abc-123-def-456",
  "canvasCourseId": 789,
  "courseName": "데이터구조",
  "courseCode": "CS201",
  "enrollmentState": "active",
  "canvasUserId": 12345
}
```

### lambda-to-courseservice-assignments (Phase 1)
```json
{
  "eventType": "ASSIGNMENT_CREATED",
  "canvasCourseId": 789,
  "canvasAssignmentId": 123456,
  "title": "중간고사 프로젝트",
  "description": "프로젝트 설명...",
  "dueAt": "2025-11-15T23:59:59Z",
  "pointsPossible": 100.0,
  "submissionTypes": ["online_upload"]
}
```

### submission-events-queue (Phase 2 - 계획)
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

**참고**:
- 전체 SQS 아키텍처: [docs/design/sqs-architecture.md](../../docs/design/sqs-architecture.md)
- DTO 사용법: [app/shared/README.md](../shared/README.md)

## Lambda 구조

### canvas-sync-lambda (✅ Phase 1 구현 완료)
- **입력**: `{"cognitoSub": "..."}`(Phase 1) 또는 `{"detail": {"cognitoSub": "..."}}`(Phase 2)
- **처리**:
  1. User-Service 내부 API로 Canvas 토큰 조회 (X-Api-Key 인증)
  2. Canvas API 호출 (courses, assignments)
  3. SQS 메시지 발행 (enrollments, assignments)
- **출력**: `{"statusCode": 200, "body": {"coursesCount": 5, "assignmentsCount": 23, "syncedAt": "..."}}`
- **테스트**: `app/serverless/canvas-sync-lambda/tests/` (✅ 15/15 passed)

### llm-lambda (Phase 3 - 향후 구현)
- 과제 설명 분석 (할일/서브태스크 생성)
- 제출물 검증 (완료 여부 판단)
- 현재는 미구현, 시간 여유 시 추가 예정

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
- `test_canvas_sync_integration.py`: ✅ Lambda → Canvas API → SQS → Course-Service → DB (6 tests)

### E2E 테스트
전체 플로우: `tests/e2e/`
- Canvas 동기화 E2E 테스트 (향후 추가 예정)

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
