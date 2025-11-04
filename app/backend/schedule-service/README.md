# Schedule Service

## 서비스 책임

1. **시간 기반 일정 통합** - Canvas 과제, Google Calendar, 사용자 커스텀 일정 통합
2. **외부 캘린더 동기화** - Google Calendar 양방향 동기화
3. **타임라인 뷰 제공** - 모든 일정을 시간 순으로 조회

**포트**: 8083 | **API Gateway 라우팅**: `/api/v1/schedules/**`

**현재 상태**: ⚠️ **미구현** (Phase 2 진행 예정)

---

## 데이터 모델 (예정)

### User_Schedules
사용자별 통합 일정을 저장합니다.

| 필드 | 타입 | 제약조건 |
|------|------|----------|
| id | Long | PK |
| user_id | Long | NOT NULL (User Service의 user_id) |
| source_type | Enum | CANVAS_ASSIGNMENT, TASK, GOOGLE_CALENDAR, CUSTOM |
| source_id | Long | nullable (원본 데이터 ID) |
| title | String | NOT NULL |
| description | TEXT | nullable |
| start_time | LocalDateTime | NOT NULL |
| end_time | LocalDateTime | nullable |
| is_all_day | Boolean | DEFAULT false |
| external_calendar_id | String | nullable (Google Calendar Event ID) |

**Index**: `user_id`, `start_time`, `source_type`

**설계 원칙**:
- 모든 일정 소스를 단일 테이블에 통합
- `source_type`으로 출처 구분
- `source_id`로 원본 데이터 추적 (Canvas Assignment ID, Task ID 등)

---

## 주요 비즈니스 로직 (예정)

### 1. Canvas 과제 → 일정 자동 생성

**트리거**: Course-Service에서 Assignment 저장 시

**처리 흐름**:
```
1. Course-Service가 Assignment 저장
2. SQS 이벤트 발행: schedule-events-queue
   {
     "userId": [123, 456],  // 수강생 목록
     "sourceType": "CANVAS_ASSIGNMENT",
     "sourceId": 789,
     "title": "중간고사 프로젝트",
     "dueAt": "2025-11-15T23:59:59"
   }

3. Schedule-Service가 이벤트 수신
4. 각 수강생별로 User_Schedules 생성
   - start_time: dueAt - 1 week
   - end_time: dueAt
```

### 2. Google Calendar 양방향 동기화

**트리거**: EventBridge (5분마다)

#### Google → UniSync
```
1. Google-Calendar-Sync-Workflow (Step Functions)
2. Google Calendar API 폴링 (변경 감지)
3. SQS 이벤트 발행: calendar-events-queue
4. Schedule-Service가 User_Schedules 생성/수정/삭제
```

#### UniSync → Google
```
1. 사용자가 UniSync에서 일정 생성
2. source_type=CUSTOM이고 Google 연동된 경우
3. Google Calendar API 호출하여 이벤트 생성
4. external_calendar_id 저장
```

### 3. 일정 조회 API

**주요 쿼리**:
```sql
-- 특정 기간 일정 조회
SELECT * FROM user_schedules
WHERE user_id = ?
  AND start_time BETWEEN ? AND ?
ORDER BY start_time;

-- 특정 타입 일정만 조회
SELECT * FROM user_schedules
WHERE user_id = ?
  AND source_type = 'CANVAS_ASSIGNMENT'
ORDER BY start_time;
```

---

## 외부 의존성 (예정)

### 1. SQS
- **구독**: `schedule-events-queue` (Course-Service 이벤트)
- **구독**: `calendar-events-queue` (Google-Calendar-Sync-Workflow 이벤트)

### 2. Google Calendar API
- **OAuth2 인증**: User Service의 Credentials 테이블에서 토큰 조회
- **API**: Events 생성/수정/삭제

### 3. Course-Service
- Assignment 정보 조회 (일정 상세 정보 제공)

---

## 주요 API (예정)

### GET `/api/v1/schedules` - 일정 목록 조회
```http
X-User-Id: 1
?startDate=2025-11-01&endDate=2025-11-30&sourceType=CANVAS_ASSIGNMENT
```

### POST `/api/v1/schedules` - 커스텀 일정 생성
```json
{
  "title": "팀 미팅",
  "description": "중간고사 프로젝트 논의",
  "startTime": "2025-11-10T14:00:00",
  "endTime": "2025-11-10T15:00:00",
  "syncToGoogle": true
}
```

### PUT `/api/v1/schedules/{id}` - 일정 수정
### DELETE `/api/v1/schedules/{id}` - 일정 삭제

---

## 필수 환경변수 (예정)

| 변수 | 설명 |
|------|------|
| `GOOGLE_CALENDAR_CLIENT_ID` | Google OAuth Client ID |
| `GOOGLE_CALENDAR_CLIENT_SECRET` | Google OAuth Client Secret |
| `DB_NAME` | `unisync_schedule` |

---

## 핵심 설계 원칙

### 1. 단일 소스의 진실 (Single Source of Truth)
- Canvas Assignment는 Course-Service가 소유
- Schedule-Service는 **읽기 전용 복제본**만 저장
- 원본 수정은 반드시 원본 서비스를 통해

### 2. 이벤트 기반 동기화
- Course-Service → Schedule-Service: SQS 이벤트
- Google Calendar → Schedule-Service: Lambda → SQS
- 동기 API 호출 금지

### 3. 멱등성 보장
- `(user_id, source_type, source_id)` 조합으로 중복 방지
- 같은 Assignment 이벤트 여러 번 수신 시 중복 생성 방지

---

## 구현 우선순위

1. **Phase 1** (필수): Canvas Assignment → User_Schedules 자동 생성
2. **Phase 2** (중요): 일정 조회 API (타임라인 뷰)
3. **Phase 3** (선택): Google Calendar 동기화
4. **Phase 4** (선택): 커스텀 일정 CRUD