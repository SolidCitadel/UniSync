# UniSync 일정 및 할일 관리 시스템 설계

**버전**: 1.0
**작성일**: 2025-11-09
**상태**: 기획 단계

## 목차
1. [개요](#1-개요)
2. [핵심 개념](#2-핵심-개념)
3. [데이터 모델](#3-데이터-모델)
4. [상태 관리 및 워크플로우](#4-상태-관리-및-워크플로우)
5. [API 설계](#5-api-설계)
6. [UI/UX 구현 방향](#6-uiux-구현-방향)
7. [구현 우선순위](#7-구현-우선순위)

---

## 1. 개요

### 1.1 배경
UniSync는 학업 일정 관리를 위한 시스템으로, 사용자가 일정과 할일을 효율적으로 관리할 수 있도록 지원합니다. 기존의 Canvas LMS 연동을 넘어서, 사용자가 직접 생성하고 관리하는 일정 및 할일 시스템을 제공합니다.

### 1.2 목표
- **일정(Schedule)과 할일(Todo)의 명확한 분리**: 서로 다른 성격의 항목을 적절한 방식으로 관리
- **유연한 시각화**: 캘린더, 칸반보드, 간트차트 등 다양한 뷰 제공
- **협업 지원**: 그룹 기반 일정 및 할일 관리
- **자동화**: Canvas 과제 → 할일/일정 자동 생성, AI 기반 subtask 생성

### 1.3 주요 차이점
| 구분 | 일정(Schedule) | 할일(Todo) |
|------|----------------|------------|
| **시간 표현** | 시간 단위 (시작시간/종료시간) | 기간 단위 (시작일/종료일) |
| **특성** | 하루 종일 가능 | 마감일 중심 |
| **유사 서비스** | Google Calendar | Todoist |
| **UI 표시** | 캘린더 뷰 | 칸반보드, 간트차트 |
| **주요 용도** | 수업, 미팅, 이벤트 | 과제, 프로젝트 작업, 체크리스트 |

---

## 2. 핵심 개념

### 2.1 일정(Schedule)
**정의**: 특정 시간대에 발생하는 이벤트

**특징**:
- 시작 시간(`start_time`)과 종료 시간(`end_time`)을 가짐
- 하루 종일 일정(`is_all_day`) 가능
- 반복 일정(`recurrence_rule`) 지원
- 위치(`location`) 정보 포함 가능

**예시**:
- 월요일 09:00-10:30 데이터베이스 수업
- 11월 15일 14:00-16:00 팀 미팅
- 11월 20일 하루 종일 중간고사

### 2.2 할일(Todo)
**정의**: 완료해야 하는 작업 또는 과제

**특징**:
- 시작일(`start_date`)과 마감일(`due_date`)을 **모두 필수**로 가짐 (간트차트 표시를 위해 필요)
- 상태(`status`): TODO, IN_PROGRESS, DONE
- 우선순위(`priority`): LOW, MEDIUM, HIGH, URGENT
- 서브태스크(`subtasks`) 지원
- 진행률(`progress_percentage`) 추적 가능
- 일정 기반 할일인 경우 `schedule_id`로 연결 (Canvas 과제 일정 포함)

**예시**:
- 11/10-11/15 중간고사 프로젝트 (서브태스크: 요구사항 분석, 설계, 구현, 테스트)
- 11/12-11/13 과제 보고서 작성
- 11/18-11/18 논문 리뷰 제출

### 2.3 카테고리(Category)
**정의**: 일정과 할일을 분류하는 체계

**특징**:
- 모든 일정과 할일은 **반드시** 하나의 카테고리에 속함
- 사용자 정의 카테고리 생성 가능
- 색상(`color`)으로 시각적 구분
- 카테고리별 필터링 및 통계 제공

**기본 카테고리**:
- 학업 (ACADEMIC)
- 개인 (PERSONAL)
- 팀 프로젝트 (TEAM_PROJECT)
- 기타 (OTHER)

**예시**:
- "데이터베이스" 카테고리: 과목별 과제, 수업 일정
- "운동" 카테고리: 헬스 일정, 운동 루틴
- "사이드 프로젝트" 카테고리: 개발 할일, 스터디 일정

### 2.4 그룹(Group)
**정의**: 여러 사용자가 함께 일정과 할일을 관리하는 단위

**특징**:
- 그룹 관리자(OWNER, ADMIN)가 일정/할일 생성 및 관리
- 그룹 멤버(MEMBER)는 **읽기 전용** 권한
- 권한 관리: OWNER, ADMIN, MEMBER
- 그룹별 카테고리 설정 가능
- 그룹 일정/할일 알림 공유

**사용 시나리오**:
- 팀 프로젝트: 팀장이 할일 생성, 팀원들은 조회 및 본인 할일 관리
- 스터디 그룹: 스터디장이 일정 등록, 멤버들이 확인
- 동아리: 회장이 동아리 활동 일정 등록

### 2.5 Canvas 과제 → 일정 → 할일 플로우
**정의**: Canvas 과제가 자동으로 일정으로 등록되고, 사용자가 할일을 추가하는 흐름

**Canvas 과제 자동 동기화**:
1. Canvas API에서 새 과제 감지
2. **일정(Schedule)으로 자동 등록** (과제 마감일을 end_time으로 설정)
3. LLM이 과제 설명을 분석하여 **할일(Todo) 및 서브태스크 자동 생성**
4. 할일은 `schedule_id`를 통해 일정(과제)과 연결
5. 사용자는 추가로 직접 할일을 생성할 수도 있음

**일정→할일 수동 변환**:
- 일정을 할일로 변환 시, 할일 엔티티에 `schedule_id` 참조
- 일정과 할일의 연결 관계 유지
- 일정 완료 시 관련 할일도 완료 처리 가능 (선택적)

**사용 시나리오**:
- Canvas 과제 "중간고사 프로젝트" → 일정 자동 등록 → LLM이 할일 자동 생성 (서브태스크: 요구사항 분석, 설계, 구현, 테스트)
- "팀 미팅" 일정 → 사용자가 수동으로 "회의록 작성" 할일 생성

---

## 3. 데이터 모델

### 3.1 Schedules (일정)
```sql
CREATE TABLE schedules (
    schedule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,                          -- 소유자 (NULL이면 그룹 일정)
    group_id BIGINT,                                   -- 그룹 일정인 경우
    category_id BIGINT NOT NULL,                       -- 카테고리 (필수)

    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(255),

    start_time TIMESTAMP NOT NULL,                     -- 시작 시간
    end_time TIMESTAMP NOT NULL,                       -- 종료 시간
    is_all_day BOOLEAN DEFAULT FALSE,                  -- 하루 종일 여부

    status ENUM('TODO', 'IN_PROGRESS', 'DONE') DEFAULT 'TODO',

    recurrence_rule VARCHAR(255),                      -- 반복 규칙 (RFC 5545 RRULE)

    source ENUM('USER', 'CANVAS', 'GOOGLE_CALENDAR', 'TODOIST') DEFAULT 'USER',
    source_id VARCHAR(255),                            -- 외부 서비스 ID

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_group_id (group_id),
    INDEX idx_category_id (category_id),
    INDEX idx_start_time (start_time),
    INDEX idx_end_time (end_time),
    INDEX idx_status (status),
    CHECK ((user_id IS NOT NULL) OR (group_id IS NOT NULL))  -- 개인 또는 그룹 중 하나는 필수
);
```

### 3.2 Todos (할일)
```sql
CREATE TABLE todos (
    todo_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,                          -- 소유자 (NULL이면 그룹 할일)
    group_id BIGINT,                                   -- 그룹 할일인 경우
    category_id BIGINT NOT NULL,                       -- 카테고리 (필수)

    title VARCHAR(255) NOT NULL,
    description TEXT,

    start_date DATE NOT NULL,                          -- 시작일 (필수, 간트차트 표시용)
    due_date DATE NOT NULL,                            -- 마감일 (필수)

    status ENUM('TODO', 'IN_PROGRESS', 'DONE') DEFAULT 'TODO',
    priority ENUM('LOW', 'MEDIUM', 'HIGH', 'URGENT') DEFAULT 'MEDIUM',

    progress_percentage INT DEFAULT 0,                 -- 진행률 (0-100)

    parent_todo_id BIGINT,                             -- 부모 할일 (서브태스크인 경우)

    schedule_id BIGINT,                                -- 일정 기반 할일인 경우 (Canvas 과제 일정 포함)

    is_ai_generated BOOLEAN DEFAULT FALSE,             -- AI로 생성된 할일인지

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_todo_id) REFERENCES todos(todo_id) ON DELETE CASCADE,
    FOREIGN KEY (schedule_id) REFERENCES schedules(schedule_id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_group_id (group_id),
    INDEX idx_category_id (category_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_due_date (due_date),
    INDEX idx_parent_todo_id (parent_todo_id),
    INDEX idx_schedule_id (schedule_id),
    CHECK ((user_id IS NOT NULL) OR (group_id IS NOT NULL)),  -- 개인 또는 그룹 중 하나는 필수
    CHECK (progress_percentage BETWEEN 0 AND 100),
    CHECK (start_date <= due_date)                     -- 시작일은 마감일보다 빠르거나 같아야 함
);
```

### 3.3 Categories (카테고리)
```sql
CREATE TABLE categories (
    category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,                          -- 소유자 (NULL이면 그룹 카테고리)
    group_id BIGINT,                                   -- 그룹 카테고리인 경우

    name VARCHAR(100) NOT NULL,
    color VARCHAR(7) NOT NULL,                         -- HEX 색상 코드 (#RRGGBB)
    icon VARCHAR(50),                                  -- 아이콘 이름

    is_default BOOLEAN DEFAULT FALSE,                  -- 기본 카테고리 여부

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_group_id (group_id),
    UNIQUE KEY uk_user_name (user_id, name),           -- 사용자별 카테고리명 중복 방지
    CHECK ((user_id IS NOT NULL) OR (group_id IS NOT NULL))
);
```

### 3.4 Groups (그룹)
```sql
CREATE TABLE groups (
    group_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,

    owner_id BIGINT NOT NULL,                          -- 그룹 소유자

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_owner_id (owner_id)
);
```

### 3.5 Group_Members (그룹 멤버)
```sql
CREATE TABLE group_members (
    member_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    role ENUM('OWNER', 'ADMIN', 'MEMBER') DEFAULT 'MEMBER',

    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE,
    UNIQUE KEY uk_group_user (group_id, user_id),
    INDEX idx_group_id (group_id),
    INDEX idx_user_id (user_id)
);
```


---

## 4. 상태 관리 및 워크플로우

### 4.1 일정(Schedule) 상태
```
TODO → IN_PROGRESS → DONE
```

**상태 전환 규칙**:
- **TODO**: 초기 상태, 아직 시작 전
- **IN_PROGRESS**: 사용자가 수동으로 변경
- **DONE**: 사용자가 수동으로 완료 표시

**중요**: 모든 상태 전환은 **사용자가 수동으로 변경**해야 합니다. 자동 전환 없음.

### 4.2 할일(Todo) 상태
```
TODO → IN_PROGRESS → DONE
```

**상태 전환 규칙**:
- **TODO**: 초기 상태
- **IN_PROGRESS**: 사용자가 수동으로 변경
- **DONE**: 사용자가 수동으로 완료 표시

**중요**: 모든 상태 전환은 **사용자가 수동으로 변경**해야 합니다. 자동 전환 없음.

**서브태스크 처리**:
- 진행률(`progress_percentage`)은 서브태스크 완료 비율로 자동 계산 가능
- 부모 할일의 상태는 자동으로 변경되지 않으며, 사용자가 직접 관리

### 4.3 Canvas 과제 자동 동기화 플로우
```
Canvas API 폴링 (Canvas-Sync-Lambda)
  → 새 과제 감지
  → SQS: assignment-events-queue

Schedule-Service가 SQS 구독
  → 1. 일정(Schedule) 자동 생성
     - 과제 제목 → Schedule.title
     - 과제 마감일 → Schedule.end_time
     - source = 'CANVAS'
     - source_id = canvas_assignment_id

  → 2. LLM Lambda 트리거 (SQS: llm-analysis-queue)
     - 과제 설명 분석
     - 할일(Todo) 및 서브태스크 제안 생성

  → 3. LLM 결과 수신 (SQS: task-creation-queue)
     - Schedule-Service가 할일(Todo) 생성
     - schedule_id 참조 (위에서 생성한 일정)
     - 서브태스크도 함께 생성 (parent_todo_id)
     - is_ai_generated = TRUE

사용자 액션
  → 추가 할일을 직접 생성할 수 있음
  → schedule_id를 참조하여 동일한 과제에 대한 할일 추가
```

**중요**:
- Canvas 과제는 먼저 **일정(Schedule)**으로 등록
- 그 다음 LLM이 **할일(Todo)**을 자동 생성
- 할일은 `schedule_id`를 통해 일정(과제)과 연결

### 4.4 일정→할일 변환 플로우
```
사용자가 일정 선택
  → "할일 생성" 버튼 클릭
  → Frontend에서 API 호출
  → Schedule-Service: Todo 생성
     - schedule_id 참조
     - 일정 제목 → Todo 제목
     - 일정 종료 시간 → Todo 마감일
```

---

## 5. API 설계

### 5.1 Schedule-Service API

#### 일정(Schedule) 관리
- `GET /api/v1/schedules` - 일정 목록 조회
  - Query Params: `startDate`, `endDate`, `categoryId`, `groupId`, `status`
  - Response: 캘린더 뷰용 일정 목록

- `POST /api/v1/schedules` - 일정 생성
  - Request Body:
    ```json
    {
      "title": "데이터베이스 수업",
      "description": "3장 정규화 이론",
      "startTime": "2025-11-10T09:00:00",
      "endTime": "2025-11-10T10:30:00",
      "isAllDay": false,
      "location": "공학관 301호",
      "categoryId": 1,
      "groupId": null,
      "recurrenceRule": "FREQ=WEEKLY;BYDAY=MO,WE"
    }
    ```

- `PUT /api/v1/schedules/{scheduleId}` - 일정 수정

- `DELETE /api/v1/schedules/{scheduleId}` - 일정 삭제

- `PATCH /api/v1/schedules/{scheduleId}/status` - 일정 상태 변경
  - Request Body: `{ "status": "DONE" }`

- `POST /api/v1/schedules/{scheduleId}/convert-to-todo` - 일정→할일 변환
  - Request Body:
    ```json
    {
      "title": "중간고사 준비",  // 선택 (미지정 시 일정 제목 사용)
      "startDate": "2025-11-10",
      "dueDate": "2025-11-15"
    }
    ```
  - Response: 생성된 Todo ID

#### 할일(Todo) 관리
- `GET /api/v1/todos` - 할일 목록 조회
  - Query Params: `startDate`, `endDate`, `categoryId`, `groupId`, `status`, `priority`
  - Response: 칸반보드/간트차트용 할일 목록

- `POST /api/v1/todos` - 할일 생성
  - Request Body:
    ```json
    {
      "title": "중간고사 프로젝트",
      "description": "데이터베이스 설계 및 구현",
      "startDate": "2025-11-10",  // 필수
      "dueDate": "2025-11-20",    // 필수
      "categoryId": 1,
      "priority": "HIGH",
      "scheduleId": 123  // 일정 기반 할일인 경우 (Canvas 과제 일정 포함)
    }
    ```

- `PUT /api/v1/todos/{todoId}` - 할일 수정

- `DELETE /api/v1/todos/{todoId}` - 할일 삭제

- `PATCH /api/v1/todos/{todoId}/status` - 할일 상태 변경
  - Request Body: `{ "status": "IN_PROGRESS" }`

- `PATCH /api/v1/todos/{todoId}/progress` - 진행률 업데이트
  - Request Body: `{ "progressPercentage": 50 }`

- `GET /api/v1/todos/{todoId}/subtasks` - 서브태스크 목록 조회

- `POST /api/v1/todos/{todoId}/subtasks` - 서브태스크 생성
  - Request Body: `{ "title": "요구사항 분석", "dueDate": "2025-11-12" }`

#### 카테고리 관리
- `GET /api/v1/categories` - 카테고리 목록 조회
  - Query Params: `groupId` (선택)

- `POST /api/v1/categories` - 카테고리 생성
  - Request Body:
    ```json
    {
      "name": "데이터베이스",
      "color": "#FF5733",
      "icon": "book",
      "groupId": null
    }
    ```

- `PUT /api/v1/categories/{categoryId}` - 카테고리 수정

- `DELETE /api/v1/categories/{categoryId}` - 카테고리 삭제

#### 그룹 관리
- `GET /api/v1/groups` - 내 그룹 목록

- `POST /api/v1/groups` - 그룹 생성
  - Request Body:
    ```json
    {
      "name": "데이터베이스 팀 프로젝트",
      "description": "3조 팀원들"
    }
    ```

- `PUT /api/v1/groups/{groupId}` - 그룹 정보 수정

- `DELETE /api/v1/groups/{groupId}` - 그룹 삭제

- `POST /api/v1/groups/{groupId}/members` - 그룹 멤버 초대
  - Request Body: `{ "userId": 123 }`

- `DELETE /api/v1/groups/{groupId}/members/{userId}` - 그룹 멤버 제거

- `PATCH /api/v1/groups/{groupId}/members/{userId}/role` - 멤버 권한 변경
  - Request Body: `{ "role": "ADMIN" }`

- `GET /api/v1/groups/{groupId}/schedules` - 그룹 일정 목록

- `GET /api/v1/groups/{groupId}/todos` - 그룹 할일 목록

---

## 6. UI/UX 구현 방향

### 6.1 캘린더 뷰 (Calendar View)
**표시 항목**: 일정(Schedule)만 표시

**기능**:
- 월/주/일 뷰 전환
- 일정 드래그 앤 드롭으로 시간 변경
- 일정 클릭 시 상세 정보 모달
- 카테고리별 색상 구분
- 하루 종일 일정은 상단에 별도 표시

**참고 라이브러리**: FullCalendar, React Big Calendar

### 6.2 칸반보드 (Kanban Board)
**표시 항목**: 할일(Todo)만 표시

**컬럼 구성**:
- TODO
- IN_PROGRESS
- DONE

**기능**:
- 드래그 앤 드롭으로 상태 변경
- 우선순위별 색상 구분
- 마감일 임박 시 경고 표시
- 서브태스크 진행률 표시

**참고 라이브러리**: react-beautiful-dnd

### 6.3 간트차트 (Gantt Chart)
**표시 항목**: 할일(Todo)만 표시

**기능**:
- 시작일~마감일 막대 그래프
- 서브태스크 계층 구조 표시
- 진행률 바 표시
- 드래그로 일정 조정

**참고 라이브러리**: react-gantt-chart, dhtmlx-gantt

### 6.4 리스트 뷰 (List View)
**표시 항목**: 일정과 할일 모두 표시

**기능**:
- 날짜순/우선순위순 정렬
- 필터링: 카테고리, 그룹, 상태
- 빠른 상태 변경 (체크박스)
- 검색 기능

### 6.5 통합 대시보드 (Dashboard)
**표시 항목**:
- 오늘의 일정
- 다가오는 할일 (마감일 기준)
- 진행 중인 할일
- 최근 완료한 항목
- 카테고리별 통계 (차트)

---

## 7. 구현 우선순위

### Phase 1: 기본 데이터 모델 및 CRUD (2주) ✅ 완료
- [x] DB 스키마 생성 (Schedules, Todos, Categories)
- [x] JPA Entity 및 Repository 구현
- [x] Schedule CRUD API 구현
- [x] Todo CRUD API 구현
- [x] Category CRUD API 구현
- [x] 단위 테스트 작성

### Phase 2: 상태 관리 및 서브태스크 (1주)
- [ ] Todo 서브태스크 기능 구현
- [ ] 상태 전환 로직 구현
- [ ] 진행률 자동 계산 기능
- [ ] 통합 테스트 작성

### Phase 3: Canvas 과제 연동 (1주)
- [ ] Canvas 과제 → Todo 자동 생성
- [ ] LLM을 통한 서브태스크 자동 생성
- [ ] 과제 제출 시 Todo 상태 자동 업데이트
- [ ] E2E 테스트 작성

### Phase 4: 그룹 기능 (2주)
- [ ] Groups, Group_Members 테이블 구현
- [ ] 그룹 생성/수정/삭제 API
- [ ] 그룹 멤버 관리 API
- [ ] 그룹 일정/할일 공유 기능
- [ ] 권한 관리 (OWNER, ADMIN, MEMBER)

### Phase 5: 일정→할일 변환 (1주)
- [ ] 일정→할일 변환 API 구현
- [ ] schedule_id 참조 관계 처리
- [ ] Frontend UI 구현

### Phase 6: Frontend 통합 (3주)
- [ ] 캘린더 뷰 구현
- [ ] 칸반보드 구현
- [ ] 간트차트 구현
- [ ] 리스트 뷰 및 대시보드 구현

### Phase 7: 고도화 (지속)
- [ ] 반복 일정 기능
- [ ] 일정 참여자 관리
- [ ] 할일 담당자 관리
- [ ] 알림 기능 (WebSocket)
- [ ] 외부 캘린더 동기화 (Google Calendar)

---

## 8. 기존 시스템과의 통합

### 8.1 Course-Service와의 관계
- **Assignments** → **Todos**: Canvas 과제를 할일로 변환
- Course-Service는 Assignment 생성 시 SQS 이벤트 발행
- Schedule-Service가 이벤트 구독하여 Todo 생성

### 8.2 User-Service와의 관계
- 사용자 인증 및 권한 관리는 User-Service에 의존
- JWT에서 user_id 추출하여 본인 데이터만 접근

### 8.3 데이터 소유권
- **Schedules**: `user_id` 또는 `group_id` 중 하나 필수
- **Todos**: `user_id` 또는 `group_id` 중 하나 필수
- **Categories**: 개인 카테고리(`user_id`) 또는 그룹 카테고리(`group_id`)

---

## 9. 보안 및 권한 관리

### 9.1 접근 제어
- 개인 일정/할일: 본인만 조회/수정/삭제 가능
- 그룹 일정/할일:
  - MEMBER: **읽기 전용** (조회만 가능)
  - ADMIN: 조회/생성/수정/삭제 가능
  - OWNER: 전체 권한 (그룹 설정 변경, 멤버 관리 포함)

### 9.2 데이터 검증
- 할일: 시작일과 마감일은 **필수**이며, 시작일은 마감일보다 빠르거나 같아야 함 (`start_date <= due_date`)
- 일정: 종료 시간이 시작 시간보다 빠른 경우 예외 처리 (`start_time < end_time`)
- 카테고리는 반드시 지정되어야 함
- 개인 또는 그룹 중 하나는 필수 (`user_id` 또는 `group_id`)

---

## 부록

### A. 참고 자료
- [RFC 5545 - iCalendar (반복 규칙)](https://datatracker.ietf.org/doc/html/rfc5545)
- [Google Calendar API](https://developers.google.com/calendar)
- [Todoist API](https://developer.todoist.com/)

### B. 용어 정리
- **Schedule**: 특정 시간대에 발생하는 이벤트 (시간 단위)
- **Todo**: 완료해야 하는 작업 (기간 단위)
- **Subtask**: 할일의 하위 작업
- **Category**: 일정과 할일을 분류하는 체계
- **Group**: 여러 사용자가 함께 관리하는 일정/할일 공간
- **Recurrence Rule**: 반복 일정 규칙 (RFC 5545)
