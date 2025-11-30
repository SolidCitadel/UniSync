# SQS Architecture

UniSync 시스템의 SQS 기반 메시지 아키텍처 설계 문서입니다.

## 설계 철학

### 왜 SQS를 사용하는가?

1. **느슨한 결합 (Loose Coupling)**
   - 서비스 간 직접 의존성 제거
   - 한 서비스 장애가 다른 서비스에 영향 없음
   - 독립적인 배포 및 확장 가능

2. **비동기 처리**
   - 응답 시간 단축 (클라이언트는 즉시 응답 받음)
   - 백그라운드 작업 처리 (DB 저장, 외부 API 호출)

3. **확장성**
   - 새로운 Consumer 추가 용이
   - 메시지 기반이므로 여러 서비스가 동일 이벤트 구독 가능

4. **안정성**
   - 자동 재시도 메커니즘
   - Dead Letter Queue (DLQ)로 실패 메시지 격리
   - At-least-once delivery 보장

5. **추적 가능성**
   - 메시지 로그를 통한 이벤트 흐름 추적
   - 디버깅 및 모니터링 용이

## 큐 네이밍 규칙

**형식**: `{source}-to-{destination}-{purpose}`

**예시**:
- `lambda-to-courseservice-enrollments`
- `courseservice-to-scheduleservice-assignments`

**장점**:
- 메시지 흐름 명확 (어디서 → 어디로)
- 목적 명시 (무엇을 위한 큐인가)
- 검색 및 관리 용이

## 전체 SQS 큐 목록

| 큐 이름 | Publisher | Consumer(s) | 용도 | 상태 | Schema |
|---------|-----------|-------------|------|------|--------|
| `lambda-to-courseservice-sync` | Canvas-Sync-Lambda | Course-Service | Canvas 동기화 통합 메시지 (courses/assignments) | ✅ Phase 1.1 | [sync-messages](#sync-messages) |
| `courseservice-to-scheduleservice-assignments` | Course-Service | Schedule-Service | 과제 → 일정/할일 변환 | ✅ Phase 1 | [assignment-to-schedule](#assignment-to-schedule) |
| `courseservice-to-scheduleservice-courses` | Course-Service | Schedule-Service | 과목 비활성화 이벤트 (`COURSE_DISABLED`) | ✅ Phase 1.1 | [course-events](#course-events) |
| `dlq-queue` | - | Manual Review | Dead Letter Queue (모든 큐 공용) | ✅ 공통 | - |

**향후 추가 예정**:
- `scheduleservice-to-notificationservice-reminders` (Phase 2): 일정 알림
- `lambda-to-scheduleservice-google-events` (Phase 2): Google Calendar 동기화

## 메시지 흐름

### Canvas 동기화 플로우 (Phase 1 완료)

```
사용자 (동기화 버튼 클릭)
    ↓
API Gateway → User-Service
    ↓ POST /v1/sync/canvas (JWT)
User-Service
    ↓ Lambda invoke (AWS SDK)
Canvas-Sync-Lambda
    ↓ Course-Service: 활성 enrollment 조회
    ↓ Canvas API 조회 (courses/assignments)
    ↓ SQS: lambda-to-courseservice-sync
          ↓
       Course-Service (Course + Enrollment + Assignment 처리)
          ├─→ SQS: courseservice-to-scheduleservice-assignments
          │       ↓
          │    Schedule-Service (Schedule 생성)
          └─→ SQS: courseservice-to-scheduleservice-courses (COURSE_DISABLED)
                  ↓
               Schedule-Service (해당 과목 일정 삭제)
```

### Assignment → Schedule 변환 플로우 (Phase 1.1 - User Batch)

```
Course-Service (CanvasSyncListener)
    ↓ CANVAS_SYNC_COMPLETED 수신
    ↓ 모든 Assignments DB 저장 완료
    ↓ Enabled Enrollment별 그룹핑
    ↓ 사용자당 1개 batch 메시지 발행 (N명 = N개 메시지)
SQS: courseservice-to-scheduleservice-assignments
    ↓ USER_ASSIGNMENTS_CREATED
Schedule-Service (AssignmentBatchListener)
    ↓ 사용자의 모든 assignments 수신
    ↓ Course별 Category 생성/조회 (한번에)
AssignmentToScheduleConverter
    ↓ Batch 처리 (assignments 배열 순회)
    ↓ dueAt null인 항목은 스킵
ScheduleService
    ↓ 모든 Schedules 한번에 저장
Schedule/Todo DB 저장 완료
```

**효율성 개선**:
- **이전**: Assignment마다 개별 메시지 × 사용자 수 = 401 × N개
- **현재**: 사용자당 1개 batch 메시지 = **N개만**
- **예시**: 401개 assignments, 10명 사용자 → 4,010개 → **10개** (99.75% 감소)

## 메시지 스키마

### Sync Messages

**큐**: `lambda-to-courseservice-sync`
**Publisher**: Canvas-Sync-Lambda
**Consumer**: Course-Service

**메시지 형식**:
```json
{
  "eventType": "CANVAS_SYNC_COMPLETED",
  "syncMode": "assignments",
  "cognitoSub": "abc-123-def-456",
  "syncedAt": "2025-11-30T12:00:00Z",
  "courses": [
    {
      "canvasCourseId": 789,
      "courseName": "데이터구조",
      "courseCode": "CS201",
      "workflowState": "available",
      "assignments": [
        {
          "canvasAssignmentId": 123456,
          "title": "중간고사 프로젝트",
          "dueAt": "2025-11-20T23:59:59Z",
          "pointsPossible": 100.0
        }
      ]
    }
  ]
}
```

- `eventType`: `CANVAS_COURSES_SYNCED`(courses) | `CANVAS_SYNC_COMPLETED`(assignments)
- `syncMode`: `courses` | `assignments`
- assignments는 courses 모드에서 생략, `dueAt`가 null인 항목은 제외

### Assignment to Schedule (User Batch)

**큐**: `courseservice-to-scheduleservice-assignments`
**Publisher**: Course-Service
**Consumer**: Schedule-Service

**설계 원칙**:
- **사용자 단위 batch**: 메시지 수를 Assignment 수가 아닌 **사용자 수에만 비례**하도록 최적화
- **효율성**: 401개 assignments × 10명 사용자 = 4,010개 메시지 → **10개 메시지**로 감소
- **일관성**: 한 사용자의 모든 assignments를 한번에 처리하여 일정 생성 일관성 보장

**메시지 형식**:
```json
{
  "eventType": "USER_ASSIGNMENTS_CREATED",
  "cognitoSub": "abc-123-def-456",
  "syncedAt": "2025-11-30T12:00:00Z",
  "assignments": [
    {
      "assignmentId": "uuid-1234-5678",
      "canvasAssignmentId": 123456,
      "canvasCourseId": 789,
      "courseId": "course-uuid",
      "courseName": "데이터구조",
      "title": "중간고사 프로젝트",
      "description": "Spring Boot 프로젝트를 작성하세요...",
      "dueAt": "2025-11-20T23:59:59Z",
      "pointsPossible": 100.0
    },
    {
      "assignmentId": "uuid-2345-6789",
      "canvasAssignmentId": 123457,
      "canvasCourseId": 790,
      "courseId": "course-uuid-2",
      "courseName": "알고리즘",
      "title": "기말고사 프로젝트",
      "description": "정렬 알고리즘 구현...",
      "dueAt": "2025-12-15T23:59:59Z",
      "pointsPossible": 150.0
    }
  ]
}
```

**필드 설명**:
- `eventType`: `USER_ASSIGNMENTS_CREATED` (사용자별 assignments batch)
- `cognitoSub`: 사용자 Cognito Sub
- `syncedAt`: 동기화 완료 시각 (ISO 8601)
- `assignments`: 해당 사용자의 모든 enabled course assignments 배열
  - `assignmentId`: Course-Service의 Assignment UUID
  - `canvasAssignmentId`: Canvas API의 assignment ID
  - `canvasCourseId`: Canvas API의 course ID
  - `courseId`: Course-Service의 Course UUID
  - `courseName`: 과목명 (일정 제목 및 카테고리 생성용)
  - `title`: 과제 제목
  - `description`: 과제 설명
  - `dueAt`: 마감일시 (ISO 8601, null이면 schedule 생성 스킵)
  - `pointsPossible`: 배점

**발행 시점**: Course-Service가 CANVAS_SYNC_COMPLETED 메시지를 처리하여 모든 assignments를 저장한 후, enabled enrollment별로 그룹핑하여 사용자당 1개의 batch 메시지 발행

**JSON Schema**: `app/shared/message-schemas/user-assignments-batch.schema.json`

### Course Events

**큐**: `courseservice-to-scheduleservice-courses`
**Publisher**: Course-Service
**Consumer**: Schedule-Service

**이벤트 타입**:
- `COURSE_DISABLED`: 해당 과목의 모든 Schedule 삭제

**JSON Schema**: `app/shared/message-schemas/course-events.schema.json`

## 재시도 및 DLQ 전략

### 재시도 정책

**기본 설정** (모든 큐 공통):
- **Maximum Receives**: 3회
- **Visibility Timeout**: 30초
- **Message Retention**: 14일

**재시도 시나리오**:
1. 임시 장애 (DB connection timeout, 네트워크 오류)
2. 트랜잭션 충돌
3. 외부 API 일시 장애

### Dead Letter Queue (DLQ)

**DLQ 전송 조건**:
- 3회 재시도 후에도 실패
- Unknown Exception 발생

**DLQ 처리 절차**:
1. CloudWatch 알림 발생
2. 개발자 수동 조사
3. 원인 파악 후:
   - 버그 수정 → 메시지 재전송
   - 데이터 오류 → 메시지 폐기 + 로그 기록

**모니터링**:
- DLQ 메시지 수 > 0 → 알림 발생
- 주간 DLQ 메시지 리뷰

## 멱등성 보장

### 중복 메시지 처리

SQS는 **At-least-once delivery**를 보장하므로 동일 메시지가 여러 번 전달될 수 있습니다.

**멱등성 전략**:

1. **UNIQUE 제약조건 활용**
   ```sql
   -- Course-Service
   UNIQUE KEY uk_canvas_assignment (canvas_assignment_id)

   -- Schedule-Service
   UNIQUE KEY uk_canvas_assignment (canvas_assignment_id)
   ```

2. **INSERT ... ON DUPLICATE KEY UPDATE**
   ```java
   // 중복 시 업데이트
   assignmentRepository.save(assignment); // JPA가 merge 처리
   ```

3. **로그 기록**
   ```java
   log.info("Duplicate assignment ignored: {}", canvasAssignmentId);
   ```

### 이벤트 순서

SQS는 **메시지 순서를 보장하지 않습니다** (Standard Queue).

**순서 무관 설계**:
- 각 메시지는 독립적으로 처리 가능해야 함
- `updatedAt` 타임스탬프로 최신 상태 판단
- FIFO Queue는 성능 이슈로 사용 안 함

## 보안

### 접근 제어

**IAM Policy** (LocalStack 및 AWS):
- Lambda → SQS SendMessage 권한
- Course-Service → SQS ReceiveMessage, DeleteMessage 권한
- Schedule-Service → SQS ReceiveMessage, DeleteMessage 권한

### 메시지 암호화

**현재**: 암호화 없음 (로컬 개발 환경)
**운영**: SQS Server-Side Encryption (SSE) 활성화 예정

### 민감 정보 처리

**금지 사항**:
- Canvas API 토큰을 메시지에 포함하지 말 것
- 사용자 비밀번호 등 민감 정보 전송 금지

**허용**:
- `cognitoSub`: 공개 식별자 (민감 정보 아님)
- 과제 제목, 설명: 공개 정보

## 모니터링 및 로깅

### 메트릭

**CloudWatch 메트릭** (주요 모니터링 항목):
- `ApproximateNumberOfMessagesVisible`: 처리 대기 메시지 수
- `ApproximateAgeOfOldestMessage`: 가장 오래된 메시지 나이
- `NumberOfMessagesSent`: 발행된 메시지 수
- `NumberOfMessagesReceived`: 소비된 메시지 수
- `NumberOfMessagesDeleted`: 처리 완료된 메시지 수

**알림 임계값**:
- DLQ 메시지 > 0
- 대기 메시지 > 100
- 가장 오래된 메시지 > 1시간

### 로깅

**Publisher 로그**:
```java
log.info("Publishing to SQS: queue={}, eventType={}, id={}",
    queueUrl, eventType, assignmentId);
```

**Consumer 로그**:
```java
log.info("Received from SQS: queue={}, eventType={}, id={}",
    queueName, eventType, assignmentId);
log.info("Processed successfully: assignmentId={}", assignmentId);
```

**에러 로그**:
```java
log.error("Failed to process message: {}, retryCount={}",
    message, retryCount, exception);
```

## 테스트 전략

### 단위 테스트

**Publisher 테스트**:
- SQS 메시지 발행 검증
- 메시지 형식 검증
- Mocking: SqsAsyncClient

**Consumer 테스트**:
- 메시지 파싱 검증
- 비즈니스 로직 실행 검증
- Mocking: SqsListener

### 통합 테스트

**LocalStack 활용**:
```python
# tests/integration/test_sqs_flow.py
def test_assignment_sqs_flow(sqs_client, queue_url):
    # 1. 메시지 발행
    sqs_client.send_message(...)

    # 2. Consumer 처리 대기
    time.sleep(2)

    # 3. DB 검증
    assert assignment exists in DB
```

### E2E 테스트

**전체 플로우**:
- Lambda → SQS → Course-Service → DB
- Course-Service → SQS → Schedule-Service → DB

## 운영 가이드

### 큐 추가 절차

1. **설계 문서 작성**
   - 이 문서에 새 큐 추가
   - Feature 문서에 상세 설계

2. **JSON Schema 작성**
   - `app/shared/message-schemas/` 추가
   - 필수 필드 정의

3. **DTO 구현**
   - `app/shared/java-common/` Java DTO
   - `app/shared/python-common/` Python DTO

4. **인프라 생성**
   - `localstack-init/01-create-queues.sh` 업데이트
   - 환경변수 추가 (`.env.common`, `.env.local`)

5. **Publisher/Consumer 구현**
   - 각 서비스에 코드 추가
   - 단위 테스트 작성

6. **통합 테스트**
   - E2E 플로우 검증

### 큐 삭제 절차

**주의**: 기존 큐는 절대 삭제하지 말 것 (호환성 유지)

**Deprecated 처리**:
1. 문서에 `[Deprecated]` 표시
2. Consumer 코드 제거
3. 3개월 후 큐 삭제

### 트러블슈팅

**증상**: 메시지가 처리되지 않음
- CloudWatch에서 DLQ 확인
- Consumer 로그 확인 (에러 발생 여부)
- DB 제약조건 위반 확인

**증상**: 중복 메시지 발생
- UNIQUE 제약조건 확인
- 멱등성 로직 검증

**증상**: 메시지 처리 지연
- ApproximateNumberOfMessagesVisible 확인
- Consumer 인스턴스 수 증가 고려

## 참고 문서

- [시스템 아키텍처](system-architecture.md) - 전체 시스템 구조
- [Canvas 동기화](../features/canvas-sync.md) - Phase 1 구현 완료
- [Assignment → Schedule 변환](../features/assignment-to-schedule.md) - Phase 1 구현 예정
- [Shared Modules](../../app/shared/README.md) - DTO 사용법
- [테스트 전략](../features/testing-strategy.md) - 테스트 계층 구조
