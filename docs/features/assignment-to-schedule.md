# Assignment to Schedule 변환

Course-Service의 Canvas 과제를 Schedule-Service의 일정(Schedule) 및 할일(Todo)로 자동 변환하는 기능입니다.

## 상태

| Phase | 설명 | 상태 |
|-------|------|------|
| Phase 1 | 기본 과제 → 일정 자동 변환 | ✅ 구현 완료 |
| Phase 2 | 과제 → 할일 자동 변환 (subtask 지원) | 📋 계획 |
| Phase 3 | LLM 기반 스마트 분할 (과제 분석 → 서브태스크 자동 생성) | 💡 향후 |

## 개요

### 목적

Canvas 과제가 Course-Service에 저장되면 자동으로 Schedule-Service에 일정 및 할일로 등록하여 사용자가 수동으로 일정을 만들 필요가 없도록 합니다.

### 주요 기능

1. **과제 → 일정 변환** (Phase 1)
   - Canvas 과제의 마감일(dueAt)을 기준으로 Schedule 생성
   - 과제 제출 시간을 고려한 기본 시간 설정 (예: 23:00-23:59)

2. **과제 → 할일 변환** (Phase 2)
   - Canvas 과제를 Todo로 생성
   - 과제 기간(assignedAt ~ dueAt) 관리
   - 서브태스크 지원 (parent_todo_id)

3. **LLM 기반 자동 분할** (Phase 3 - 향후)
   - 과제 설명 분석하여 단계별 서브태스크 생성
   - 예: "중간고사 프로젝트" → ["요구사항 분석", "설계", "구현", "테스트", "문서화"]

## 아키텍처

### 전체 플로우

```
Lambda → SQS → Course-Service
                   ↓ Assignment DB 저장
                   ↓ SQS 발행 (courseservice-to-scheduleservice-assignments)
                   ↓
              Schedule-Service
                   ↓ AssignmentListener (SQS consume)
                   ↓ Schedule/Todo 생성
                   ↓ DB 저장
```

### SQS 기반 통신

**왜 SQS를 사용하는가?**
- **느슨한 결합**: Schedule-Service 장애가 Course-Service에 영향 없음
- **비동기 처리**: Assignment 저장 속도 향상
- **확장성**: 나중에 알림 서비스 등 다른 consumer 추가 가능
- **재시도 메커니즘**: 실패 시 자동 재시도 + DLQ
- **아키텍처 일관성**: Lambda → Course-Service도 SQS 사용 중

**REST API를 사용하지 않는 이유:**
- Schedule-Service 다운 시 Course-Service도 영향받음 (강한 결합)
- 동기 처리로 인한 성능 저하
- 다른 서비스가 assignment 이벤트를 받으려면 코드 수정 필요

### 컴포넌트

#### Course-Service
- **AssignmentEventListener**: Lambda → SQS 메시지 consume (기존)
- **AssignmentService**: Assignment DB 저장 후 이벤트 발행 (신규)
- **AssignmentEventPublisher**: SQS 메시지 발행 (신규)

#### Schedule-Service
- **AssignmentListener**: SQS 메시지 consume (신규)
- **AssignmentToScheduleConverter**: Assignment → Schedule/Todo 변환 로직 (신규)
- **ScheduleService**: Schedule DB 저장 (기존)
- **TodoService**: Todo DB 저장 (기존)

## SQS 메시지 스키마

### courseservice-to-scheduleservice-assignments

**큐 이름**: `courseservice-to-scheduleservice-assignments`
**DLQ**: `dlq-queue` (공통)

**메시지 형식**:
```json
{
  "eventType": "ASSIGNMENT_CREATED",
  "assignmentId": "uuid-1234-5678",
  "cognitoSub": "abc-123-def-456",
  "canvasAssignmentId": 123456,
  "canvasCourseId": 789,
  "title": "중간고사 프로젝트",
  "description": "Spring Boot 프로젝트를 작성하세요...",
  "dueAt": "2025-11-20T23:59:59Z",
  "pointsPossible": 100.0,
  "courseId": "course-uuid",
  "courseName": "데이터구조"
}
```

**필드 설명**:
- `eventType`: 이벤트 타입 (ASSIGNMENT_CREATED, ASSIGNMENT_UPDATED, ASSIGNMENT_DELETED)
- `assignmentId`: Course-Service의 Assignment UUID
- `cognitoSub`: 사용자 Cognito Sub (글로벌 식별자)
- `canvasAssignmentId`: Canvas API의 assignment ID
- `canvasCourseId`: Canvas API의 course ID
- `title`: 과제 제목
- `description`: 과제 설명 (LLM 분석용)
- `dueAt`: 마감일시 (ISO 8601)
- `pointsPossible`: 배점
- `courseId`: Course-Service의 Course UUID
- `courseName`: 과목명 (일정 표시용)

## Phase 1: 기본 과제 → 일정 변환

### 구현 범위

1. **SQS 큐 생성**
   - `courseservice-to-scheduleservice-assignments` 큐 추가

2. **Course-Service 수정**
   - AssignmentService에서 Assignment 저장 후 SQS 메시지 발행
   - SqsAsyncClient 사용하여 비동기 발행

3. **Schedule-Service 구현**
   - AssignmentListener: SQS 메시지 consume
   - AssignmentToScheduleConverter: 변환 로직
   - Schedule 생성 (start_time, end_time, source=CANVAS)

### 변환 규칙 (Phase 1)

**Assignment → Schedule 매핑**:
```
Assignment:
  - title: "중간고사 프로젝트"
  - dueAt: "2025-11-20T23:59:59Z"
  - courseName: "데이터구조"

↓ 변환

Schedule:
  - title: "[데이터구조] 중간고사 프로젝트"
  - start_time: "2025-11-20T23:00:00Z" (dueAt - 1시간)
  - end_time: "2025-11-20T23:59:59Z" (dueAt)
  - source: CANVAS
  - category_id: [Canvas 기본 카테고리]
  - cognito_sub: "abc-123-def-456"
  - canvas_assignment_id: 123456 (외래키)
```

**기본 시간 설정**:
- `start_time`: `dueAt - 1시간` (제출 시간 확보)
- `end_time`: `dueAt`
- 시간대: UTC (Canvas API 기본값)

**카테고리 전략**:
- Phase 1: "Canvas 과제" 기본 카테고리 자동 생성
- Phase 2: 과목별 카테고리 (사용자 설정 가능)

### 중복 처리

**멱등성 보장**:
- Schedule 테이블에 `canvas_assignment_id` UNIQUE 제약조건
- 동일한 과제로 여러 Schedule 생성 방지
- ASSIGNMENT_UPDATED 이벤트 시 기존 Schedule 업데이트

### 에러 처리

**실패 시나리오**:
1. **Category 없음**: 기본 "Canvas 과제" 카테고리 자동 생성
2. **중복 Schedule**: 기존 Schedule 업데이트 (title, start_time, end_time)
3. **Invalid dueAt**: 로그 기록 후 Skip (DLQ 전송 안 함)
4. **DB 저장 실패**: 재시도 (SQS 기본 재시도 정책)

**DLQ 전송 조건**:
- 3회 재시도 후에도 실패
- Unknown Exception

## Phase 2: 과제 → 할일 변환 (계획)

### 추가 구현 범위

1. **Todo 생성**
   - Assignment → Todo 변환
   - `start_date`: assignedAt
   - `due_date`: dueAt
   - `schedule_id`: 생성된 Schedule FK

2. **서브태스크 지원**
   - `parent_todo_id` 활용
   - 사용자가 수동으로 서브태스크 추가 가능

### 변환 규칙 (Phase 2)

**Assignment → Todo 매핑**:
```
Assignment:
  - title: "중간고사 프로젝트"
  - description: "Spring Boot 프로젝트..."
  - dueAt: "2025-11-20T23:59:59Z"

↓ 변환

Todo:
  - title: "중간고사 프로젝트"
  - description: "Spring Boot 프로젝트..."
  - start_date: "2025-11-15" (dueAt - 5일, 기본값)
  - due_date: "2025-11-20"
  - schedule_id: [생성된 Schedule ID]
  - parent_todo_id: NULL
  - status: TODO
  - cognito_sub: "abc-123-def-456"
```

## Phase 3: LLM 기반 자동 분할 (향후)

### 개념

과제 설명(description)을 LLM이 분석하여 자동으로 서브태스크 생성:

**예시**:
```
Assignment: "중간고사 프로젝트 - REST API 서버 구현"

↓ LLM 분석

Parent Todo: "중간고사 프로젝트 - REST API 서버 구현"
  ├─ Subtask 1: "요구사항 분석 및 API 명세 작성"
  ├─ Subtask 2: "데이터베이스 스키마 설계"
  ├─ Subtask 3: "Spring Boot 프로젝트 초기 설정"
  ├─ Subtask 4: "REST API 엔드포인트 구현"
  ├─ Subtask 5: "단위 테스트 작성"
  └─ Subtask 6: "통합 테스트 및 문서화"
```

### 구현 방식 (향후)

1. Schedule-Service → LLM Lambda 호출
2. LLM Lambda: Assignment description 분석
3. LLM Lambda → Schedule-Service: 서브태스크 목록 반환
4. Schedule-Service: parent_todo_id 활용하여 계층 구조 생성

## 데이터 모델

### Schedules 테이블 (Schedule-Service)

```sql
CREATE TABLE schedules (
    id BINARY(16) PRIMARY KEY,
    cognito_sub VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    location VARCHAR(255),
    category_id BINARY(16) NOT NULL,
    source ENUM('CANVAS', 'GOOGLE', 'USER') NOT NULL,
    canvas_assignment_id BIGINT UNIQUE,  -- Canvas 과제 연동 (NULL 가능)
    google_event_id VARCHAR(255) UNIQUE, -- Google Calendar 연동 (NULL 가능)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_cognito_sub (cognito_sub),
    INDEX idx_canvas_assignment (canvas_assignment_id)
);
```

**중요 컬럼**:
- `canvas_assignment_id`: Course-Service의 Assignment.canvas_assignment_id와 매핑
- `source`: CANVAS로 설정하여 자동 생성된 일정 구분
- UNIQUE 제약조건으로 중복 생성 방지

### Todos 테이블 (Phase 2)

```sql
CREATE TABLE todos (
    id BINARY(16) PRIMARY KEY,
    cognito_sub VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    due_date DATE NOT NULL,
    schedule_id BINARY(16),              -- 연관된 일정 (NULL 가능)
    parent_todo_id BINARY(16),           -- 부모 할일 (서브태스크용, NULL 가능)
    category_id BINARY(16) NOT NULL,
    status ENUM('TODO', 'IN_PROGRESS', 'DONE') DEFAULT 'TODO',
    priority ENUM('LOW', 'MEDIUM', 'HIGH') DEFAULT 'MEDIUM',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (schedule_id) REFERENCES schedules(id),
    FOREIGN KEY (parent_todo_id) REFERENCES todos(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_cognito_sub (cognito_sub),
    INDEX idx_parent_todo (parent_todo_id)
);
```

## 구현 파일

### LocalStack

- `localstack-init/01-create-queues.sh`: `courseservice-to-scheduleservice-assignments` 큐 추가

### Course-Service

- `com.unisync.course.assignment.service.AssignmentService`: SQS 메시지 발행 로직 추가
- `com.unisync.course.assignment.publisher.AssignmentEventPublisher` (신규): SQS 발행
- `com.unisync.course.common.config.SqsPublisherConfig` (신규): SqsAsyncClient Bean

### Schedule-Service

- `com.unisync.schedule.assignment.listener.AssignmentListener` (신규): SQS consume
- `com.unisync.schedule.assignment.converter.AssignmentToScheduleConverter` (신규): 변환 로직
- `com.unisync.schedule.assignment.dto.AssignmentEventDto` (신규): SQS 메시지 DTO
- `com.unisync.schedule.schedule.service.ScheduleService`: Schedule 저장 로직 (기존)
- `com.unisync.schedule.category.service.CategoryService`: 기본 카테고리 생성 (기존)

### 환경변수

`.env.common`, `.env.local`:
```bash
SQS_ASSIGNMENT_TO_SCHEDULE_QUEUE=courseservice-to-scheduleservice-assignments
```

## 테스트 전략

### 단위 테스트

**Course-Service**:
- `AssignmentEventPublisherTest`: SQS 발행 검증
- `AssignmentServiceTest`: Assignment 저장 후 이벤트 발행 검증

**Schedule-Service**:
- `AssignmentListenerTest`: SQS 메시지 파싱 검증
- `AssignmentToScheduleConverterTest`: 변환 로직 검증
  - dueAt → start_time/end_time 계산
  - title 포맷 검증
  - 카테고리 자동 생성 검증

### 통합 테스트

**`tests/integration/test_assignment_to_schedule_integration.py`** (신규):

1. **test_assignment_to_schedule_flow**
   - Course-Service: Assignment 저장
   - SQS 메시지 발행 확인
   - Schedule-Service: Schedule 저장 확인
   - DB 검증

2. **test_duplicate_assignment_idempotency**
   - 동일 Assignment 2번 저장
   - Schedule 중복 생성 안 됨 검증

3. **test_assignment_update**
   - Assignment 수정 (title, dueAt 변경)
   - 기존 Schedule 업데이트 검증

4. **test_default_category_creation**
   - Category 없을 때 "Canvas 과제" 자동 생성 검증

### 테스트 실행

```bash
# Course-Service 단위 테스트
cd app/backend/course-service
./gradlew test --tests AssignmentEventPublisherTest

# Schedule-Service 단위 테스트
cd app/backend/schedule-service
./gradlew test --tests AssignmentListenerTest

# 통합 테스트
python -m pytest tests/integration/test_assignment_to_schedule_integration.py -v
```

## 구현 체크리스트

### Phase 1: 기본 과제 → 일정 변환

#### 인프라
- [ ] LocalStack: `courseservice-to-scheduleservice-assignments` 큐 생성
- [ ] 환경변수: `.env.common`, `.env.local`에 큐 이름 추가

#### Course-Service
- [ ] SqsPublisherConfig: SqsAsyncClient Bean 생성
- [ ] AssignmentEventPublisher: SQS 발행 로직
- [ ] AssignmentService: Assignment 저장 후 이벤트 발행
- [ ] AssignmentEventDto: SQS 메시지 DTO 정의
- [ ] 단위 테스트: AssignmentEventPublisherTest

#### Schedule-Service
- [ ] Schedules 테이블: `canvas_assignment_id` 컬럼 추가 (마이그레이션)
- [ ] AssignmentListener: SQS 메시지 consume
- [ ] AssignmentEventDto: SQS 메시지 DTO (Course-Service와 동일)
- [ ] AssignmentToScheduleConverter: 변환 로직
- [ ] CategoryService: "Canvas 과제" 기본 카테고리 생성
- [ ] ScheduleService: Schedule 저장 로직 (기존 활용)
- [ ] 단위 테스트: AssignmentListenerTest, ConverterTest

#### 통합 테스트
- [ ] test_assignment_to_schedule_flow
- [ ] test_duplicate_assignment_idempotency
- [ ] test_assignment_update
- [ ] test_default_category_creation

#### 문서
- [ ] 이 문서 업데이트 (구현 완료 표시)
- [ ] CLAUDE.md: Phase 1 완료 업데이트

### Phase 2: 과제 → 할일 변환 (향후)
- [ ] Todos 테이블: `schedule_id` FK 추가
- [ ] TodoService: Todo 생성 로직
- [ ] AssignmentToTodoConverter: 변환 로직
- [ ] 통합 테스트

### Phase 3: LLM 기반 자동 분할 (향후)
- [ ] LLM Lambda 설계
- [ ] Schedule-Service → LLM Lambda 연동
- [ ] 서브태스크 생성 로직
- [ ] E2E 테스트

## 참고 문서

- [Canvas 동기화](canvas-sync.md) - Canvas API → Course-Service
- [일정 관리](schedule-management.md) - Schedule-Service 상세 설계
- [시스템 아키텍처](../design/system-architecture.md) - 전체 아키텍처
- [테스트 전략](testing-strategy.md) - 테스트 계층 구조
