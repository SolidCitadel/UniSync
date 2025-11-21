# UniSync 시스템 설계서

**개발 현황**: Phase 2 진행 중 (Canvas 동기화 및 SQS 통합)

## 구현 현황 요약

### ✅ 완료
- 기본 인프라 및 서비스 구조
- **API Gateway (Spring Cloud Gateway + JWT 인증 + Cognito 연동)**
- **Internal API 분리 (/v1/* vs /internal/v1/*)** - 외부/내부 API 명확한 구분
- **User-Service 인증 및 토큰 관리** - Cognito 통합, Canvas 토큰 암호화 저장
- **Canvas Sync API 통합 (Phase 1: 수동 호출)** - POST /v1/sync/canvas 엔드포인트
- Canvas Sync Lambda + SQS 통합
- Course-Service의 SQS 구독 및 Assignment 처리
- **Assignment → Schedule 자동 변환 (Phase 1)** - SQS 기반 비동기 처리
- 공유 모듈 기반 DTO 표준화
- E2E 통합 테스트 환경

### 🚧 진행 중
- Schedule-Service 일정 통합 (기본 CRUD 및 카테고리 관리)
- Assignment → Todo 자동 변환 (Phase 2)

## 1. 시스템 아키텍처

### 1.1 전체 구조도
```
[Client - React]
       |
       | HTTPS
       |
[API Gateway - ALB + Cognito]
       |
       |--- [User-Service]        --- [MySQL - Users DB]
       |--- [Course-Service]      --- [MySQL - Courses DB]
       |--- [Schedule-Service]    --- [MySQL - Schedules DB]
       |
       |--- [Canvas-Sync-Lambda] --- [SQS]
       |--- [Google-Calendar-Sync-Workflow - Step Functions + Lambda] --- [SQS]
```

### 1.2 서비스 구성
- **API Gateway**: ALB + AWS Cognito (인증/인가)
- **Backend Services**: Spring Boot 기반 마이크로서비스 (3개)
  - User-Service: 사용자/인증/소셜
  - Course-Service: Canvas 학업 데이터
  - Schedule-Service: 시간 기반 일정 통합
- **Serverless Sync Components**: Lambda
  - Canvas-Sync-Lambda: Canvas API 동기화 ([상세 설계](../features/canvas-sync.md) - Phase 1: 수동 동기화, Phase 2: EventBridge 자동화)
  - Google-Calendar-Sync-Workflow: Google Calendar 동기화
- **Data Layer**: MySQL (RDS) - 서비스별 DB 분리 (3개)
- **Message Queue**: AWS SQS
- **Event Bus**: AWS EventBridge

---

## 2. 데이터 모델

### 2.1 User-Service

#### Users
```sql
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    canvas_user_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email)
);
```

#### Credentials
```sql
CREATE TABLE credentials (
    credential_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider ENUM('CANVAS', 'GOOGLE', 'TODOIST') NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT,
    token_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_provider (user_id, provider),
    INDEX idx_user_id (user_id)
);
```

#### Friendships
```sql
CREATE TABLE friendships (
    friendship_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'BLOCKED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_friend (user_id, friend_id),
    INDEX idx_user_id (user_id),
    INDEX idx_friend_id (friend_id),
    INDEX idx_status (status),
    CHECK (user_id != friend_id)
);
```

#### Groups
```sql
CREATE TABLE groups (
    group_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_owner_id (owner_id)
);
```

#### Group_Members
```sql
CREATE TABLE group_members (
    member_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('OWNER', 'ADMIN', 'MEMBER') DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uk_group_user (group_id, user_id),
    INDEX idx_group_id (group_id),
    INDEX idx_user_id (user_id)
);
```

### 2.2 Course-Service

#### Courses
```sql
CREATE TABLE courses (
    course_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canvas_course_id VARCHAR(100) UNIQUE NOT NULL,
    course_code VARCHAR(50),
    course_name VARCHAR(255) NOT NULL,
    semester VARCHAR(50),
    instructor VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_canvas_course_id (canvas_course_id)
);
```

#### Enrollments
```sql
CREATE TABLE enrollments (
    enrollment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    canvas_enrollment_id VARCHAR(100),
    role ENUM('STUDENT', 'TA', 'INSTRUCTOR') DEFAULT 'STUDENT',
    is_sync_leader BOOLEAN DEFAULT FALSE,
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_course (user_id, course_id),
    INDEX idx_user_id (user_id),
    INDEX idx_course_id (course_id),
    INDEX idx_sync_leader (course_id, is_sync_leader)
);
```

#### Assignments
```sql
CREATE TABLE assignments (
    assignment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canvas_assignment_id VARCHAR(100) UNIQUE NOT NULL,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_date TIMESTAMP,
    points_possible DECIMAL(10, 2),
    submission_types VARCHAR(255),
    canvas_url VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    INDEX idx_canvas_assignment_id (canvas_assignment_id),
    INDEX idx_course_id (course_id),
    INDEX idx_due_date (due_date)
);
```

#### Notices
```sql
CREATE TABLE notices (
    notice_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canvas_announcement_id VARCHAR(100) UNIQUE NOT NULL,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    posted_at TIMESTAMP,
    canvas_url VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    INDEX idx_canvas_announcement_id (canvas_announcement_id),
    INDEX idx_course_id (course_id)
);
```


#### Sync_Status
```sql
CREATE TABLE sync_status (
    sync_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider ENUM('CANVAS', 'GOOGLE_CALENDAR', 'TODOIST') NOT NULL,
    last_synced_at TIMESTAMP,
    sync_state ENUM('IDLE', 'IN_PROGRESS', 'FAILED') DEFAULT 'IDLE',
    error_message TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_provider (user_id, provider),
    INDEX idx_user_id (user_id)
);
```

### 2.3 Schedule-Service

**중요**: Schedule-Service는 일정(Schedule)과 할일(Todo)을 모두 관리합니다.
- **일정(Schedule)**: 시간 단위 이벤트 (캘린더 뷰)
- **할일(Todo)**: 기간 단위 작업 (칸반보드, 간트차트)
- **그룹 참조**: `group_id`는 User-Service의 Groups 테이블을 FK로 참조

자세한 데이터 모델 및 설계는 [schedule-management.md](../features/schedule-management.md) 참고

#### Schedules (일정)
```sql
CREATE TABLE schedules (
    schedule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    group_id BIGINT,
    category_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(255),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    is_all_day BOOLEAN DEFAULT FALSE,
    status ENUM('TODO', 'IN_PROGRESS', 'DONE') DEFAULT 'TODO',
    recurrence_rule VARCHAR(255),
    source ENUM('USER', 'CANVAS', 'GOOGLE_CALENDAR', 'TODOIST') DEFAULT 'USER',
    source_id VARCHAR(255),
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
    CHECK ((user_id IS NOT NULL) OR (group_id IS NOT NULL))
);
```

#### Todos (할일)
```sql
CREATE TABLE todos (
    todo_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    group_id BIGINT,
    category_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status ENUM('TODO', 'IN_PROGRESS', 'DONE') DEFAULT 'TODO',
    priority ENUM('LOW', 'MEDIUM', 'HIGH', 'URGENT') DEFAULT 'MEDIUM',
    progress_percentage INT DEFAULT 0,
    parent_todo_id BIGINT,
    schedule_id BIGINT,
    is_ai_generated BOOLEAN DEFAULT FALSE,
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
    CHECK ((user_id IS NOT NULL) OR (group_id IS NOT NULL)),
    CHECK (progress_percentage BETWEEN 0 AND 100),
    CHECK (start_date <= due_date)
);
```

#### Categories (카테고리)
```sql
CREATE TABLE categories (
    category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    group_id BIGINT,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(7) NOT NULL,
    icon VARCHAR(50),
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_group_id (group_id),
    UNIQUE KEY uk_user_name (user_id, name),
    CHECK ((user_id IS NOT NULL) OR (group_id IS NOT NULL))
);
```

**참고**: `group_id`는 User-Service의 Groups 테이블을 참조합니다 (FK 제약조건은 애플리케이션 레벨에서 관리).

---

## 3. API 설계

### 3.0 API 엔드포인트 구조

**외부 API (프론트엔드용)**:
- 클라이언트에서 `/api/v1/*` 경로로 요청
- API Gateway가 JWT 인증 후 `/api` prefix 제거하여 백엔드로 전달
- 백엔드는 `/v1/*` 경로로 수신

**내부 API (서비스 간 통신용)**:
- Lambda 등 내부 서비스가 백엔드에 직접 `/internal/v1/*` 경로로 요청
- API Gateway를 거치지 않음
- X-Api-Key 헤더로 인증
- `/api/internal/**` 경로는 API Gateway에서 403 Forbidden 차단

**예시**:
```
# 외부 API (프론트엔드)
클라이언트 → API Gateway → 백엔드
/api/v1/users/me  →  /v1/users/me

# 내부 API (서비스 간 직접 호출)
Lambda → 백엔드
/internal/v1/credentials/canvas/by-cognito-sub/{cognitoSub}
```

### 3.1 User-Service API

#### 인증 및 회원 (외부 API)
- `POST /api/v1/auth/signup` - 회원가입
- `POST /api/v1/auth/signin` - 로그인
- `GET /api/v1/users/me` - 내 프로필 조회
- `PUT /api/v1/users/me` - 프로필 수정
- `DELETE /api/v1/users/me` - 계정 삭제

#### 외부 계정 연동 (외부 API)
- `POST /api/v1/credentials/canvas` - Canvas API 토큰 저장
  - Request Body: `{ "canvasToken": "string" }`
- `GET /api/v1/credentials/canvas` - Canvas 토큰 조회 (본인)
- `DELETE /api/v1/credentials/canvas` - Canvas 토큰 삭제
- `GET /api/v1/integrations/status` - 연동 상태 조회

#### 내부 API (서비스 간 통신)
- `GET /internal/v1/credentials/canvas/by-cognito-sub/{cognitoSub}` - Canvas 토큰 조회 (X-Api-Key)
  - Lambda에서 사용자의 Canvas 토큰 조회 시 사용
  - 응답: 복호화된 토큰 및 메타데이터

### 3.2 Course-Service API

#### 수강 과목 (외부 API)
- `GET /api/v1/courses` - 내 수강 과목 목록
- `GET /api/v1/courses/{courseId}` - 과목 상세 정보
- `POST /api/v1/courses/sync` - Canvas에서 과목 동기화
- `GET /api/v1/courses/{courseId}/students` - 과목 수강생 목록

#### 과제 관리 (외부 API)
- `GET /api/v1/assignments` - 과제 목록 (필터링: 과목, 마감일, 상태)
- `GET /api/v1/assignments/{assignmentId}` - 과제 상세
- `GET /api/v1/assignments/canvas/{canvasAssignmentId}` - Canvas ID로 과제 조회

#### 동기화 (외부 API)
- `POST /api/v1/sync/canvas/trigger` - Canvas 수동 동기화 트리거
- `GET /api/v1/sync/status` - 동기화 상태 조회

#### 공지사항 (외부 API)
- `GET /api/v1/notices` - 공지사항 목록
- `GET /api/v1/notices/{noticeId}` - 공지 상세

### 3.3 Schedule-Service API

#### 일정(Schedule) 관리 (외부 API)
- `GET /api/v1/schedules` - 일정 목록 (날짜 범위, 카테고리, 그룹 필터)
- `POST /api/v1/schedules` - 일정 생성
- `PUT /api/v1/schedules/{scheduleId}` - 일정 수정
- `DELETE /api/v1/schedules/{scheduleId}` - 일정 삭제
- `PATCH /api/v1/schedules/{scheduleId}/status` - 일정 상태 변경
- `POST /api/v1/schedules/{scheduleId}/convert-to-todo` - 일정→할일 변환

#### 할일(Todo) 관리 (외부 API)
- `GET /api/v1/todos` - 할일 목록 (날짜 범위, 카테고리, 그룹, 상태, 우선순위 필터)
- `POST /api/v1/todos` - 할일 생성
- `PUT /api/v1/todos/{todoId}` - 할일 수정
- `DELETE /api/v1/todos/{todoId}` - 할일 삭제
- `PATCH /api/v1/todos/{todoId}/status` - 할일 상태 변경
- `PATCH /api/v1/todos/{todoId}/progress` - 진행률 업데이트
- `GET /api/v1/todos/{todoId}/subtasks` - 서브태스크 목록
- `POST /api/v1/todos/{todoId}/subtasks` - 서브태스크 생성

#### 카테고리 관리 (외부 API)
- `GET /api/v1/categories` - 카테고리 목록 (개인 + 내가 속한 그룹 카테고리)
  - Query Params: `groupId` (선택)
- `POST /api/v1/categories` - 카테고리 생성
  - Request Body: `{ "name": "데이터베이스", "color": "#FF5733", "icon": "book", "groupId": null }`
- `PUT /api/v1/categories/{categoryId}` - 카테고리 수정
- `DELETE /api/v1/categories/{categoryId}` - 카테고리 삭제

#### 공강 찾기 (외부 API)
- `POST /api/v1/schedules/find-free-slots` - 여러 사용자 공강 시간 계산
  - Request Body: `{ userIds: [1, 2, 3], startDate, endDate, minDuration }`
  - Response: 겹치지 않는 시간대 목록


---

## 4. 서비스 간 통신

### 4.1 동기 통신 (REST API)

#### 외부 API (클라이언트 → 백엔드)
- API Gateway를 통한 클라이언트 요청은 동기 처리
- JWT 인증 후 `/api` prefix 제거하여 백엔드로 전달
- 백엔드는 `/v1/*` 경로로 요청 수신

#### 내부 API (서비스 간 직접 통신)
- Lambda 등 내부 서비스는 백엔드에 직접 `/internal/v1/*` 경로로 호출
- API Gateway를 거치지 않음
- X-Api-Key 헤더로 인증
- 예시:
  - Canvas-Sync-Lambda → User-Service: `/internal/v1/credentials/canvas/by-cognito-sub/{cognitoSub}`
  - 복호화된 Canvas 토큰 및 메타데이터 반환

### 4.2 비동기 통신 (Event-Driven)

#### SQS 큐 구성
1. **assignment-events-queue**
   - Producer: Canvas-Sync-Lambda
   - Consumer: Course-Service, Schedule-Service
   - Message: 새로운 과제 정보

2. **task-creation-queue**
   - Producer: Canvas-Sync-Lambda
   - Consumer: Schedule-Service
   - Message: 과제 기반 할일(Todo) 생성 요청

3. **submission-events-queue**
   - Producer: Canvas-Sync-Lambda
   - Consumer: Schedule-Service
   - Message: 과제 제출물 정보

4. **calendar-events-queue**
   - Producer: Google-Calendar-Sync-Lambda
   - Consumer: Schedule-Service
   - Message: Google Calendar 동기화 이벤트

#### EventBridge 이벤트
- `AssignmentCreated` - 새 과제 생성 시
- `AssignmentUpdated` - 과제 수정 시
- `TaskCompleted` - Task 완료 시
- `SyncFailed` - 동기화 실패 시

---

## 5. 동기화 전략

### 5.1 Canvas API 동기화

#### 폴링 주기
- **실시간 모드**: 5분마다 폴링 (EventBridge Scheduler)
- **절전 모드**: 사용자 비활성 시 30분마다
- **수동 트리거**: 사용자 요청 시 즉시

#### Leader 선출 방식
- 과목당 한 명의 Leader만 Canvas API 호출 (API 비용 절감)
- Course-Service가 Enrollment 테이블에서 `is_sync_leader` 관리
- Leader 선출 조건:
  1. 해당 과목의 첫 번째 연동 사용자
  2. 기존 Leader가 연동 해제 시 다음 사용자로 자동 이관
- Leader가 조회한 데이터는 모든 수강생에게 공유

#### 변경 감지
- Canvas API의 `updated_at` 필드로 증분 동기화
- 마지막 동기화 시각 이후 변경된 항목만 조회
- Step Functions에서 처리:
  1. Canvas API 호출
  2. 변경 사항 비교
  3. 변경된 항목만 SQS로 전송

### 5.2 외부 서비스 양방향 동기화

#### Google Calendar
- **UniSync → Google**: 과제/일정 생성 시 Google Calendar API 호출
- **Google → UniSync**: Google Calendar Webhook 구독
  - Push Notification 수신
  - 변경 사항 Schedule-Service에 반영

#### Todoist
- **UniSync → Todoist**: Task 생성/수정 시 Todoist API 호출
- **Todoist → UniSync**: Webhook 구독 또는 주기적 폴링

#### 충돌 해결 정책
- **Last-Write-Wins**: 최신 수정 시각 기준
- **User Preference**: 설정에서 우선 소스 지정 (UniSync 우선 또는 외부 서비스 우선)
- **Manual Resolution**: 충돌 발생 시 사용자에게 선택 UI 제공

---

## 6. Sync-Workflow 설계 (Step Functions)

### 6.1 Canvas Sync Workflow
```
Start
  ↓
Get All Active Courses with Sync Leader
  ↓
For Each Course (Leader 토큰 사용)
  ↓
  Canvas-Sync-Lambda: Fetch Canvas Assignments, Announcements & Submissions
  ↓
  Compare with DB (check updated_at)
  ↓
  New Assignment Detected?
    ↓ Yes
    1. Send to SQS (assignment-events-queue)
       → Course-Service: Assignment 저장
       → Schedule-Service: 일정(Schedule) 생성

    2. Send to SQS (task-creation-queue)
       → Schedule-Service: 할일(Todo) 생성 (과제 기반)
  ↓
  Submission Detected?
    ↓ Yes
    Send to SQS (submission-events-queue)
    → Schedule-Service: 일정/할일 상태 업데이트
  ↓
Continue to Next Course
  ↓
End

---
핵심 플로우 (Phase 1):
Canvas 과제 → 일정(Schedule) + 할일(Todo) 자동 생성

Phase 3 (향후): LLM 분석 → 서브태스크 자동 생성 + 제출물 검증
```

### 6.2 External Sync Workflow (Google Calendar, Todoist)
```
Start
  ↓
Receive Event from SQS (external-sync-queue)
  ↓
Lambda: Call External API (create/update/delete)
  ↓
Success?
  ↓ Yes
  Update Sync Status
  ↓ No
  Retry (최대 3회)
  ↓
  Still Failed? → Send Alert
  ↓
End
```

---

## 7. LLM 서비스 설계 (Phase 3 - 향후 구현)

**현재 상태**: 미구현. Phase 1에서는 과제를 기반으로 단순 할일(Todo) 생성.

### 7.1 Lambda 함수 구성 (계획)
- **Runtime**: Python 3.11
- **Timeout**: 30초
- **Memory**: 512MB

### 7.2 기능 (계획)

#### 1) 할일 및 서브태스크 생성 (AI 기반)
- **Input**: 과제 설명 (텍스트), 과제 마감일
- **Output**: JSON 형태의 할일(Todo) 및 서브태스크 목록 (title, description, start_date, due_date, priority)
- SQS: `llm-analysis-queue`로 전송 → LLM Lambda 분석 → Schedule-Service가 서브태스크 생성

#### 2) 제출물 유효성 검사
- **Input**: 과제 설명 + 제출물 메타데이터 (파일명, 확장자, 파일 크기)
- **Output**: 유효성 검증 결과 (is_valid, issues, warnings)
- 검증 항목: 파일 형식, 파일명 규칙, 제출물 개수

### 7.3 비용 관리 (계획)
- 사용자당 월 LLM API 호출 제한
- 캐싱: 동일 과제에 대한 분석 결과 캐시

---

## 8. 보안 설계

### 8.1 인증/인가
- **AWS Cognito**: 사용자 인증 및 JWT 발급
- **JWT 검증**: API Gateway에서 자동 검증
- **Role-Based Access Control**:
  - 본인 데이터만 접근 가능
  - 친구 데이터는 공강 찾기 등 제한적 접근

### 8.2 데이터 암호화
- **전송 중**: HTTPS (TLS 1.2+)
- **저장 중**:
  - RDS 암호화 활성화
  - access_token, refresh_token은 AES-256 암호화 후 저장

### 8.3 API 키 관리
- **AWS Secrets Manager**:
  - Canvas API Token
  - Google OAuth Client Secret
  - Todoist API Key
- Lambda 및 Spring Boot 서비스에서 런타임 시 조회

### 8.4 Rate Limiting
- API Gateway에서 사용자별 Rate Limit 설정
  - 일반 API: 100 req/min
  - Canvas Sync API: 10 req/min

---

## 9. 배포 아키텍처

### 9.1 AWS 인프라

#### Compute
- **ECS Fargate**: Spring Boot 마이크로서비스 컨테이너 실행
  - User-Service: 2 tasks (CPU: 0.5 vCPU, Memory: 1GB)
  - Course-Service: 2 tasks
  - Sync-Service: 2 tasks
  - Schedule-Service: 2 tasks
  - Social-Service: 1 task
- **Lambda**: Sync-Workflow, LLM Service

#### Data
- **RDS MySQL**: 서비스별 DB (Multi-AZ 구성)
- **SQS**: 메시지 큐
- **EventBridge**: 이벤트 스케줄링

#### Networking
- **ALB**: 로드 밸런싱 및 HTTPS 종료
- **VPC**: Private Subnet (ECS, RDS), Public Subnet (ALB)
- **Security Groups**: 서비스별 최소 권한 원칙

#### Monitoring
- **CloudWatch**: 로그 수집, 메트릭 모니터링
- **CloudWatch Alarms**: 에러율, 응답 시간 알림

### 9.2 CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/deploy.yml
on:
  push:
    branches: [main]

jobs:
  build:
    - Checkout code
    - Build Spring Boot JAR
    - Build Docker image
    - Push to ECR

  deploy:
    - Update ECS Task Definition
    - Deploy to ECS Fargate
    - Run health check
    - Rollback if failed
```

---

## 10. 개발 환경 설정 (docker-compose + LocalStack)

### 10.1 주요 구성
- **LocalStack**: SQS, Lambda, EventBridge, Cognito 에뮬레이션 (포트: 4566)
- **MySQL**: 서비스별 DB (포트: 3306)
- **Spring Boot Services**: User(8081), Course(8082), Schedule(8083)
- 자세한 구성은 [docker-compose.yml](./docker-compose.yml) 참고

### 10.2 로컬 개발 플로우
1. `docker-compose up` - 전체 환경 실행
2. LocalStack에서 SQS 큐, EventBridge 규칙 자동 생성
3. MySQL 스키마 자동 생성 (Flyway/Liquibase)
4. Swagger UI에서 API 테스트 (http://localhost:808x/swagger-ui.html)

---

## 11. 기술 스택 정리

### Frontend
- **React** 18+
- **TypeScript**
- **React Query** (서버 상태 관리)
- **Zustand** (클라이언트 상태 관리)
- **TailwindCSS** (스타일링)
- **FullCalendar** (캘린더 UI)

### Backend
- **Spring Boot** 3.x
- **Spring Cloud** (Service Discovery)
- **Spring Data JPA**
- **Swagger/OpenAPI** 3.0
- **AWS SDK for Java** 2.x

### Infrastructure
- **Docker** & **docker-compose**
- **LocalStack** (로컬 AWS 에뮬레이션)
- **MySQL** 8.0
- **AWS**: ECS, Fargate, Lambda, SQS, EventBridge, RDS, ALB, Cognito, Secrets Manager

### DevOps
- **GitHub Actions** (CI/CD)
- **AWS ECR** (컨테이너 레지스트리)
- **CloudWatch** (모니터링)

---

## 12. 개발 단계별 우선순위

**Phase 1** (완료): User-Service, Course-Service 기본 구조
**Phase 2** (진행 중): Canvas 동기화, SQS 이벤트 처리
**Phase 3** (예정): LLM Lambda, Task 자동 생성
**Phase 4** (예정): 소셜 기능, 공강 찾기
**Phase 5** (예정): 외부 서비스 연동, 고도화

현재 상태는 [CLAUDE.md](./CLAUDE.md#프로젝트-개요) 참고
