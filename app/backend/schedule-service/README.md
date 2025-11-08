# Schedule Service

## 서비스 책임

Schedule-Service는 **일정(Schedule)**과 **할일(Todo)**을 통합 관리합니다.

### 주요 기능
1. **일정 관리** - 시간 단위 이벤트 (캘린더 뷰)
   - Canvas 과제 일정 자동 등록
   - Google Calendar 동기화
   - 사용자 커스텀 일정

2. **할일 관리** - 기간 단위 작업 (칸반보드, 간트차트)
   - LLM 자동 생성 할일 및 서브태스크
   - 사용자 직접 생성 할일
   - 진행률 및 우선순위 관리

3. **카테고리 관리** - 일정/할일 분류 체계

4. **공강 시간 찾기** - 여러 사용자의 공통 빈 시간대 계산

**포트**: 8083 | **API Gateway 라우팅**: `/api/v1/schedules/**`, `/api/v1/todos/**`, `/api/v1/categories/**`

**현재 상태**: ⚠️ **기획 완료, 구현 대기** (Phase 2)

---

## 핵심 개념

### 일정(Schedule) vs 할일(Todo)

| 구분 | 일정(Schedule) | 할일(Todo) |
|------|----------------|------------|
| **시간 표현** | 시간 단위 (start_time/end_time) | 기간 단위 (start_date/due_date) |
| **특성** | 하루 종일 가능 | 마감일 중심 |
| **유사 서비스** | Google Calendar | Todoist |
| **UI 표시** | 캘린더 뷰 | 칸반보드, 간트차트 |
| **주요 용도** | 수업, 미팅, 이벤트 | 과제, 프로젝트, 체크리스트 |

### Canvas 과제 처리 플로우
```
Canvas 과제 감지
  → 1. 일정(Schedule) 자동 생성 (과제 마감일)
  → 2. LLM이 과제 설명 분석
  → 3. 할일(Todo) 및 서브태스크 자동 생성
  → 사용자는 추가 할일을 직접 생성 가능
```

**핵심**: Canvas 과제 = 일정(Schedule), 이를 달성하기 위한 계획 = 할일(Todo)

---

## 데이터 모델

자세한 데이터 모델은 [일정_및_할일_관리_설계.md](../../../일정_및_할일_관리_설계.md) 참고

### Schedules (일정)
| 필드 | 타입 | 설명 |
|------|------|------|
| schedule_id | BIGINT | PK |
| user_id | BIGINT | 소유자 (NULL이면 그룹 일정) |
| group_id | BIGINT | 그룹 일정인 경우 |
| category_id | BIGINT | 카테고리 (필수) |
| title | VARCHAR(255) | 제목 |
| start_time | TIMESTAMP | 시작 시간 |
| end_time | TIMESTAMP | 종료 시간 |
| is_all_day | BOOLEAN | 하루 종일 여부 |
| status | ENUM | TODO, IN_PROGRESS, DONE |
| source | ENUM | USER, CANVAS, GOOGLE_CALENDAR |
| source_id | VARCHAR(255) | 외부 서비스 ID |

**Index**: `user_id`, `group_id`, `category_id`, `start_time`, `status`

### Todos (할일)
| 필드 | 타입 | 설명 |
|------|------|------|
| todo_id | BIGINT | PK |
| user_id | BIGINT | 소유자 (NULL이면 그룹 할일) |
| group_id | BIGINT | 그룹 할일인 경우 |
| category_id | BIGINT | 카테고리 (필수) |
| title | VARCHAR(255) | 제목 |
| start_date | DATE | 시작일 (필수) |
| due_date | DATE | 마감일 (필수) |
| status | ENUM | TODO, IN_PROGRESS, DONE |
| priority | ENUM | LOW, MEDIUM, HIGH, URGENT |
| progress_percentage | INT | 진행률 (0-100) |
| parent_todo_id | BIGINT | 부모 할일 (서브태스크) |
| schedule_id | BIGINT | 연결된 일정 |
| is_ai_generated | BOOLEAN | AI 생성 여부 |

**Index**: `user_id`, `group_id`, `category_id`, `status`, `priority`, `due_date`, `parent_todo_id`, `schedule_id`

**제약조건**: `start_date <= due_date`

### Categories (카테고리)
| 필드 | 타입 | 설명 |
|------|------|------|
| category_id | BIGINT | PK |
| user_id | BIGINT | 소유자 (NULL이면 그룹 카테고리) |
| group_id | BIGINT | 그룹 카테고리인 경우 |
| name | VARCHAR(100) | 카테고리명 |
| color | VARCHAR(7) | HEX 색상 코드 |
| icon | VARCHAR(50) | 아이콘 이름 |
| is_default | BOOLEAN | 기본 카테고리 여부 |

**참고**: `group_id`는 User-Service의 Groups 테이블을 참조 (애플리케이션 레벨 FK)

---

## 주요 비즈니스 로직

### 1. Canvas 과제 → 일정 → 할일 자동 생성

**트리거**: SQS `assignment-events-queue` 이벤트 수신

**처리 흐름**:
```
1. Assignment 이벤트 수신
   {
     "eventType": "ASSIGNMENT_CREATED",
     "canvasAssignmentId": 789,
     "canvasCourseId": 123,
     "title": "중간고사 프로젝트",
     "dueAt": "2025-11-20T23:59:59",
     "description": "데이터베이스 설계 및 구현..."
   }

2. 일정(Schedule) 생성
   - title: 과제 제목
   - end_time: 과제 마감일
   - source: CANVAS
   - source_id: canvasAssignmentId
   - 수강생 전원에게 생성

3. LLM Lambda 트리거 (SQS: llm-analysis-queue)
   - 과제 설명 분석
   - 할일 및 서브태스크 제안 생성

4. LLM 결과 수신 (SQS: task-creation-queue)
   - Todo 생성 (schedule_id 참조)
   - Subtask 생성 (parent_todo_id 참조)
   - is_ai_generated = TRUE
```

### 2. 일정→할일 변환 (사용자 액션)

**API**: `POST /schedules/{scheduleId}/convert-to-todo`

```
1. 사용자가 일정 선택 후 "할일 생성" 클릭
2. 할일 생성
   - schedule_id: 선택한 일정 ID
   - start_date: 일정 시작일 (또는 사용자 지정)
   - due_date: 일정 종료일 (또는 사용자 지정)
3. 할일과 일정 연결 유지
```

### 3. 진행률 자동 계산

```java
// 서브태스크가 있는 경우 자동 계산
int totalSubtasks = subtaskRepository.countByParentTodoId(todoId);
int completedSubtasks = subtaskRepository.countByParentTodoIdAndStatus(todoId, "DONE");
int percentage = (completedSubtasks * 100) / totalSubtasks;

todo.setProgressPercentage(percentage);
```

### 4. 상태 관리

**중요**: 모든 상태 전환은 **사용자가 수동으로 변경** (자동 전환 없음)
- TODO → IN_PROGRESS → DONE

---

## 주요 API

### 일정(Schedule) 관리

#### `GET /schedules` - 일정 목록 조회
```http
GET /schedules?startDate=2025-11-01&endDate=2025-11-30&categoryId=1&groupId=5
Authorization: Bearer {JWT}
```

**Response**:
```json
[
  {
    "scheduleId": 1,
    "title": "중간고사 프로젝트",
    "startTime": "2025-11-10T00:00:00",
    "endTime": "2025-11-20T23:59:59",
    "isAllDay": false,
    "status": "TODO",
    "source": "CANVAS",
    "categoryId": 1,
    "categoryName": "데이터베이스",
    "categoryColor": "#FF5733"
  }
]
```

#### `POST /schedules` - 일정 생성
```json
{
  "title": "팀 미팅",
  "description": "중간고사 프로젝트 논의",
  "startTime": "2025-11-10T14:00:00",
  "endTime": "2025-11-10T15:00:00",
  "location": "공학관 301호",
  "categoryId": 2,
  "groupId": null,
  "recurrenceRule": "FREQ=WEEKLY;BYDAY=MO"
}
```

#### `PATCH /schedules/{scheduleId}/status` - 상태 변경
```json
{
  "status": "DONE"
}
```

#### `POST /schedules/{scheduleId}/convert-to-todo` - 일정→할일 변환
```json
{
  "title": "중간고사 준비",
  "startDate": "2025-11-10",
  "dueDate": "2025-11-20"
}
```

### 할일(Todo) 관리

#### `GET /todos` - 할일 목록 조회
```http
GET /todos?status=TODO&priority=HIGH&categoryId=1
Authorization: Bearer {JWT}
```

**Response**:
```json
[
  {
    "todoId": 1,
    "title": "요구사항 분석",
    "startDate": "2025-11-10",
    "dueDate": "2025-11-12",
    "status": "TODO",
    "priority": "HIGH",
    "progressPercentage": 0,
    "scheduleId": 1,
    "isAiGenerated": true,
    "subtasksCount": 3,
    "completedSubtasksCount": 0
  }
]
```

#### `POST /todos` - 할일 생성
```json
{
  "title": "데이터베이스 ERD 작성",
  "description": "테이블 설계 및 관계 정의",
  "startDate": "2025-11-10",
  "dueDate": "2025-11-15",
  "categoryId": 1,
  "priority": "HIGH",
  "scheduleId": 1
}
```

#### `GET /todos/{todoId}/subtasks` - 서브태스크 목록
```http
GET /todos/1/subtasks
```

#### `POST /todos/{todoId}/subtasks` - 서브태스크 생성
```json
{
  "title": "User 테이블 정의",
  "dueDate": "2025-11-11"
}
```

### 카테고리 관리

#### `GET /categories` - 카테고리 목록
```http
GET /categories?groupId=5
```

#### `POST /categories` - 카테고리 생성
```json
{
  "name": "데이터베이스",
  "color": "#FF5733",
  "icon": "book",
  "groupId": null
}
```

---

## 외부 의존성

### 1. SQS (구독)
- **assignment-events-queue**: Canvas 과제 이벤트
- **task-creation-queue**: LLM 생성 할일 데이터
- **calendar-events-queue**: Google Calendar 동기화 이벤트

### 2. SQS (발행)
- **llm-analysis-queue**: 과제 설명 분석 요청

### 3. User-Service
- 그룹 정보 조회 (group_id 유효성 검증)
- 그룹 멤버십 확인 (권한 검증)

### 4. Course-Service
- Assignment 상세 정보 조회 (일정 상세 정보 제공)

---

## 필수 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `SCHEDULE_SERVICE_DATABASE_URL` | MySQL 연결 URL | jdbc:mysql://localhost:3306/schedule_db |
| `SCHEDULE_SERVICE_DB_USER` | DB 사용자 | unisync |
| `SCHEDULE_SERVICE_DB_PASSWORD` | DB 비밀번호 | - |
| `AWS_SQS_ENDPOINT` | SQS 엔드포인트 | http://localhost:4566 |
| `USER_SERVICE_URL` | User Service URL | http://localhost:8081 |
| `COURSE_SERVICE_URL` | Course Service URL | http://localhost:8082 |

---

## 핵심 설계 원칙

### 1. 일정과 할일의 명확한 분리
- 일정: 시간 단위 이벤트
- 할일: 기간 단위 작업
- 서로 다른 테이블, 서로 다른 UI

### 2. 그룹 데이터는 User-Service 소유
- Schedule-Service는 `group_id`만 참조
- 그룹 멤버십 검증은 User-Service API 호출
- FK 제약조건은 애플리케이션 레벨에서 관리

### 3. 이벤트 기반 동기화
- Canvas → SQS → Schedule-Service
- LLM → SQS → Schedule-Service
- 동기 API 호출 금지

### 4. 상태 관리는 사용자 주도
- 모든 상태 전환은 사용자가 수동으로 변경
- 자동 전환 없음 (진행률 계산만 자동)

### 5. 멱등성 보장
- Canvas 과제: `(source='CANVAS', source_id=canvasAssignmentId)` 중복 방지
- 같은 이벤트 여러 번 수신 시 중복 생성 방지

---

## 구현 우선순위

### Phase 1 (필수)
- [ ] Schedules, Todos, Categories 테이블 생성
- [ ] 기본 CRUD API 구현
- [ ] Canvas 과제 → 일정 자동 생성
- [ ] LLM 할일 자동 생성

### Phase 2 (중요)
- [ ] 서브태스크 기능
- [ ] 진행률 자동 계산
- [ ] 일정→할일 변환
- [ ] 카테고리 관리

### Phase 3 (선택)
- [ ] Google Calendar 동기화
- [ ] 반복 일정
- [ ] 공강 시간 찾기

---

## 참고 문서

- [일정_및_할일_관리_설계.md](../../../일정_및_할일_관리_설계.md) - 상세 설계 및 데이터 모델
- [설계서.md](../../../설계서.md) - 전체 시스템 아키텍처
- [CLAUDE.md](../../../CLAUDE.md) - 프로젝트 개요 및 가이드
