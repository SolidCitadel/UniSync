# Course Service

## 서비스 책임

1. **Canvas 학업 데이터 관리** - Course, Assignment, Task (예정)
2. **SQS 이벤트 처리** - Canvas-Sync-Lambda가 발행한 assignment 이벤트 구독
3. **과제 데이터 동기화** - Canvas API 변경사항을 DB에 반영
4. **LLM 분석 결과 처리** (예정) - AI가 생성한 Task/Subtask 저장

**포트**: 8082 | **API Gateway 라우팅**: `/api/v1/courses/**`, `/api/v1/assignments/**`, `/api/v1/tasks/**`

---

## 도메인 구조

```
com.unisync.course/
├── assignment/        # 과제 관리 (SQS 이벤트 처리, Assignment CRUD)
│   ├── listener/      # AssignmentEventListener (SQS 구독)
│   ├── service/       # AssignmentService
│   ├── controller/
│   └── dto/
├── task/              # Task 관리 (예정)
└── common/
    ├── entity/        # Course, Assignment, Task (예정)
    ├── repository/
    └── config/
```

---

## 데이터 모델

### Course
Canvas 과목 정보입니다.

| 필드 | 타입 | 제약조건 |
|------|------|----------|
| id | Long | PK |
| canvas_course_id | Long | UNIQUE, NOT NULL (Canvas Course ID) |
| name | String | NOT NULL |
| course_code | String | nullable (예: "CS101") |
| start_at | LocalDateTime | nullable |
| end_at | LocalDateTime | nullable |

**Index**: `canvas_course_id`

### Assignment
Canvas 과제 정보입니다.

| 필드 | 타입 | 제약조건 |
|------|------|----------|
| id | Long | PK |
| canvas_assignment_id | Long | **UNIQUE**, NOT NULL (Canvas Assignment ID) |
| course_id | Long | FK → courses.id |
| title | String | NOT NULL |
| description | TEXT | nullable |
| due_at | LocalDateTime | nullable |
| points_possible | Integer | nullable |
| submission_types | String | nullable (예: "online_upload,online_text_entry") |

**Index**: `canvas_assignment_id`, `course_id`, `due_at`

**중요**: `canvas_assignment_id`는 UNIQUE 제약으로 중복 저장 방지

### Task (예정)
AI가 과제를 분석하여 생성한 세부 작업입니다.

| 필드 | 타입 | 제약조건 |
|------|------|----------|
| id | Long | PK |
| assignment_id | Long | FK → assignments.id |
| parent_task_id | Long | nullable, FK → tasks.id (자기참조, Subtask) |
| title | String | NOT NULL |
| description | TEXT | nullable |
| estimated_hours | Integer | nullable |
| status | Enum | TODO, IN_PROGRESS, DONE |
| is_ai_generated | Boolean | DEFAULT true |

---

## 주요 비즈니스 로직

### 1. SQS 이벤트 처리 (`AssignmentEventListener`)

**구독 큐**: `assignment-events-queue`

**처리 흐름**:
```
1. Canvas-Sync-Lambda가 SQS로 이벤트 발행
   - ASSIGNMENT_CREATED: 새 과제 생성
   - ASSIGNMENT_UPDATED: 기존 과제 수정

2. AssignmentEventListener가 메시지 수신

3. AssignmentService 호출
   - CREATED: createAssignment()
   - UPDATED: updateAssignment()

4. 성공 시 메시지 자동 삭제 (ON_SUCCESS)
   실패 시 재시도 → maxReceiveCount 초과 시 DLQ로 이동
```

**중복 방지**:
- `canvas_assignment_id`로 중복 체크
- 이미 존재하면 생성 스킵, 로그만 출력

**구현**: `app/backend/course-service/src/main/java/com/unisync/course/assignment/listener/AssignmentEventListener.java:1`

### 2. Assignment 생성/업데이트 (`AssignmentService`)

#### createAssignment()
```
1. canvas_assignment_id 중복 체크
   - 존재하면 스킵

2. Course 조회 (canvas_course_id로)
   - 없으면 IllegalArgumentException

3. Assignment 생성 및 저장
```

#### updateAssignment()
```
1. canvas_assignment_id로 기존 Assignment 조회
   - 없으면 IllegalArgumentException

2. Entity 업데이트 메서드 호출
   assignment.updateFromCanvas(...)

3. 저장 (Dirty Checking으로 UPDATE)
```

**구현**: `app/backend/course-service/src/main/java/com/unisync/course/assignment/service/AssignmentService.java:1`

---

## SQS 메시지 스키마

### assignment-events-queue

**메시지 DTO**: `com.unisync.shared.dto.sqs.AssignmentEventMessage` (공유 모듈)

```json
{
  "eventType": "ASSIGNMENT_CREATED",
  "canvasAssignmentId": 123456,
  "canvasCourseId": 789,
  "title": "중간고사 프로젝트",
  "description": "Spring Boot로 REST API 구현",
  "dueAt": "2025-11-15T23:59:59",
  "pointsPossible": 100,
  "submissionTypes": "online_upload"
}
```

---

## 외부 의존성

### 1. SQS (AWS Spring Cloud)
- **의존성**: `io.awspring.cloud:spring-cloud-aws-starter-sqs`
- **큐**: `assignment-events-queue`
- **DLQ**: `assignment-events-queue-dlq` (maxReceiveCount: 3)

### 2. 공유 모듈 (java-common)
```kotlin
// build.gradle.kts
implementation("com.unisync:java-common:1.0.0")
```

---

## 필수 환경변수

| 변수 | 설명 | 예시 |
|------|------|------|
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `AWS_ENDPOINT_OVERRIDE` | LocalStack 엔드포인트 | `http://localhost:4566` |
| `DB_HOST` | MySQL 호스트 | `localhost` |
| `DB_PORT` | MySQL 포트 | `3306` |
| `DB_NAME` | 데이터베이스 이름 | `unisync_course` |

---

## 중요 제약사항

### 절대 금지
1. `canvas_assignment_id` 중복 저장 - DB 제약으로 방지됨
2. Course 없이 Assignment 생성 - FK 제약 위반
3. SQS 메시지 처리 중 예외 삼키기 - 반드시 throw하여 재시도

### 핵심 원칙
1. **멱등성 보장**: 같은 이벤트 중복 수신 시 중복 생성 방지
2. **Course 선행 생성**: Assignment 저장 전 Course 존재 확인 필수
3. **트랜잭션 단위**: Assignment 생성/수정은 하나의 트랜잭션에서 처리

---

## 예정 기능

### 1. Task 관리 도메인
- LLM-Lambda가 발행한 `task-creation-queue` 구독
- AI 생성 Task/Subtask 저장
- 사용자 커스텀 Task 추가/수정/삭제

### 2. Schedule-Service 연동
- Assignment 저장 시 일정 이벤트 발행
- Schedule-Service가 User_Schedules 생성

### 3. 제출물 검증 처리
- `submission-events-queue` 구독
- LLM이 제출물 검증 후 Task 상태 자동 업데이트