# 그룹 일정 조율

**버전**: 1.0
**작성일**: 2025-11-22
**최종 수정**: 2025-11-22
**상태**: 📋 설계 단계

## 목차
1. [개요](#1-개요)
2. [일정 조율 알고리즘](#2-일정-조율-알고리즘)
3. [API 설계](#3-api-설계)
4. [프론트엔드 연동](#4-프론트엔드-연동)
5. [그룹 일정 생성](#5-그룹-일정-생성)
6. [구현 파일](#6-구현-파일)
7. [테스트 전략](#7-테스트-전략)

---

## 1. 개요

### 1.1 배경

팀 프로젝트나 스터디 그룹에서 **공통 가능 시간**을 찾는 것은 중요한 협업 기능입니다. UniSync는 그룹 멤버들의 개인 일정을 분석하여 **모든 멤버가 비어있는 시간대(공강)**를 자동으로 계산하고, 프론트엔드에서 시간 블록을 선택하여 그룹 일정을 생성할 수 있습니다.

### 1.2 목표

- **공강 시간 계산**: 그룹 멤버들의 일정을 분석하여 겹치지 않는 시간대 추출
- **유연한 검색**: 기간, 최소 지속 시간, 멤버 선택 가능
- **시간 블록 반환**: 프론트엔드에서 시각화할 수 있는 형태로 반환
- **그룹 일정 생성**: 선택된 시간 블록으로 그룹 일정 생성 (기존 API 재사용)

### 1.3 주요 기능

1. **공강 시간 조회**
   - 그룹 전체 또는 선택된 멤버들의 공강 시간 계산
   - 날짜 범위, 최소 지속 시간, 시간대(근무 시간) 필터링
   - 반환: 사용 가능한 시간 블록 목록

2. **그룹 일정 생성**
   - 프론트엔드에서 시간 블록 선택
   - 기존 일정 생성 API 사용 (`POST /api/v1/schedules`, `group_id` 포함)
   - 그룹 멤버들에게 알림 (향후 구현)

3. **일정 충돌 확인**
   - 그룹 일정 생성 전 멤버들의 개인 일정과 충돌 여부 확인
   - 충돌 시 경고 또는 강제 생성 옵션

### 1.4 사용 시나리오

**시나리오 1: 팀 프로젝트 미팅 일정 잡기**
1. 팀장이 그룹 페이지에서 "일정 조율" 클릭
2. 기간 선택: 2025-11-25 ~ 2025-11-30
3. 최소 지속 시간: 2시간
4. 백엔드가 모든 팀원의 공강 시간 계산
5. 프론트엔드에 시간 블록 표시 (예: 월요일 14:00-16:00, 화요일 10:00-12:00)
6. 팀장이 "월요일 14:00-16:00" 선택 → 그룹 일정 생성
7. 팀원들에게 알림 전송

**시나리오 2: 스터디 그룹 정기 모임**
1. 스터디장이 "매주 공강 찾기" 선택
2. 요일별 필터: 월/수/금만
3. 시간대: 18:00-22:00 (저녁 시간만)
4. 최소 지속 시간: 3시간
5. 매주 수요일 19:00-22:00 공통 가능 시간 발견
6. 반복 일정 생성 (recurrence_rule 사용)

---

## 2. 일정 조율 알고리즘

### 2.1 입력 파라미터

```json
{
  "groupId": 1,
  "userIds": [123, 456, 789],           // 선택된 멤버 (optional, null이면 전체 그룹 멤버)
  "startDate": "2025-11-25",
  "endDate": "2025-11-30",
  "minDurationMinutes": 120,            // 최소 지속 시간 (분)
  "workingHoursStart": "09:00",         // 근무/활동 시간 시작 (optional)
  "workingHoursEnd": "18:00",           // 근무/활동 시간 종료 (optional)
  "daysOfWeek": [1, 3, 5]              // 요일 필터 (optional, 1=월, 7=일)
}
```

### 2.2 알고리즘 설계

#### Step 1: 대상 멤버 결정
- `userIds`가 주어지면: 해당 멤버들의 일정만 조회
- `userIds`가 null: 그룹 전체 멤버의 일정 조회
- **권한 확인**: 요청자가 그룹 멤버인지 검증

#### Step 2: 일정 수집
```sql
SELECT start_time, end_time
FROM schedules
WHERE (user_id IN (123, 456, 789) OR group_id = 1)
  AND start_time >= '2025-11-25 00:00:00'
  AND end_time <= '2025-11-30 23:59:59'
ORDER BY start_time;
```

**수집 대상**:
- 개인 일정 (`user_id IN (...)`)
- 그룹 일정 (`group_id = 1`) - 이미 확정된 그룹 일정도 포함

#### Step 3: 시간 블록 병합 (Interval Merging)

**목적**: 겹치는 일정을 하나의 busy 구간으로 병합

**알고리즘** (Greedy):
```python
def merge_intervals(intervals):
    """
    intervals: [(start, end), (start, end), ...]
    return: 병합된 busy 구간 목록
    """
    if not intervals:
        return []

    # 시작 시간 기준 정렬
    intervals.sort(key=lambda x: x[0])

    merged = [intervals[0]]
    for current in intervals[1:]:
        last = merged[-1]
        if current[0] <= last[1]:  # 겹침
            merged[-1] = (last[0], max(last[1], current[1]))
        else:
            merged.append(current)

    return merged
```

**예시**:
```
입력:
  - User A: 09:00-11:00, 14:00-16:00
  - User B: 10:00-12:00, 15:00-17:00
  - User C: 13:00-14:30

병합 후 busy 구간:
  - 09:00-12:00 (A와 B 겹침)
  - 13:00-17:00 (C, A, B 겹침)
```

#### Step 4: 공강 시간 추출

**알고리즘**:
```python
def find_free_slots(busy_intervals, start_date, end_date, min_duration_minutes):
    """
    busy_intervals: 병합된 busy 구간
    return: 공강 시간 목록
    """
    free_slots = []
    current_time = start_date

    for busy_start, busy_end in busy_intervals:
        if busy_start - current_time >= min_duration_minutes:
            free_slots.append((current_time, busy_start))
        current_time = max(current_time, busy_end)

    # 마지막 busy 구간 이후
    if end_date - current_time >= min_duration_minutes:
        free_slots.append((current_time, end_date))

    return free_slots
```

**예시** (전체 범위: 2025-11-25 09:00 ~ 18:00):
```
Busy 구간:
  - 09:00-12:00
  - 13:00-17:00

Free 구간 (min_duration=60분):
  - 12:00-13:00 (1시간, 조건 만족)
  - 17:00-18:00 (1시간, 조건 만족)
```

#### Step 5: 필터링 (근무 시간, 요일)

**근무 시간 필터**:
```python
def apply_working_hours(free_slots, working_hours_start, working_hours_end):
    """
    free_slots를 근무 시간 범위로 제한
    """
    filtered = []
    for slot_start, slot_end in free_slots:
        # 근무 시간과 겹치는 부분만 추출
        adjusted_start = max(slot_start, working_hours_start)
        adjusted_end = min(slot_end, working_hours_end)

        if adjusted_start < adjusted_end:
            filtered.append((adjusted_start, adjusted_end))

    return filtered
```

**요일 필터**:
```python
def filter_by_days_of_week(free_slots, days_of_week):
    """
    days_of_week: [1, 3, 5] (월, 수, 금)
    """
    filtered = []
    for slot_start, slot_end in free_slots:
        if slot_start.weekday() + 1 in days_of_week:  # Python weekday: 0=월
            filtered.append((slot_start, slot_end))

    return filtered
```

### 2.3 복잡도 분석

- **시간 복잡도**: O(N log N) (정렬) + O(N) (병합) = O(N log N)
  - N: 일정 개수
- **공간 복잡도**: O(N)
- **확장성**: 그룹 멤버 100명, 각 멤버 일정 50개 → N=5000, 충분히 빠름 (~10ms)

### 2.4 최적화 전략

**캐싱**:
- 동일 그룹, 동일 기간 조회 시 캐싱 (Redis, 1시간 TTL)
- 새 일정 생성 시 캐시 무효화

**인덱스 활용**:
- `INDEX idx_user_id_time (user_id, start_time, end_time)`
- `INDEX idx_group_id_time (group_id, start_time, end_time)`

**Pagination** (향후):
- 공강 시간이 너무 많을 경우 페이징 (예: 첫 10개 블록만 반환)

---

## 3. API 설계

### 3.1 공강 시간 조회

```
POST /api/v1/schedules/find-free-slots
```

**Request Body**:
```json
{
  "groupId": 1,
  "userIds": [123, 456, 789],
  "startDate": "2025-11-25",
  "endDate": "2025-11-30",
  "minDurationMinutes": 120,
  "workingHoursStart": "09:00",
  "workingHoursEnd": "18:00",
  "daysOfWeek": [1, 3, 5]
}
```

**필드 설명**:
- `groupId` (required): 그룹 ID (권한 검증용)
- `userIds` (optional): 선택된 멤버 목록 (null이면 전체 그룹 멤버)
- `startDate` (required): 검색 시작일 (YYYY-MM-DD)
- `endDate` (required): 검색 종료일 (YYYY-MM-DD)
- `minDurationMinutes` (required): 최소 지속 시간 (분)
- `workingHoursStart` (optional): 근무 시간 시작 (HH:MM, 기본: 00:00)
- `workingHoursEnd` (optional): 근무 시간 종료 (HH:MM, 기본: 23:59)
- `daysOfWeek` (optional): 요일 필터 (1=월, 2=화, ..., 7=일, null이면 모든 요일)

**Response** (200 OK):
```json
{
  "groupId": 1,
  "groupName": "팀 프로젝트",
  "memberCount": 3,
  "searchPeriod": {
    "startDate": "2025-11-25",
    "endDate": "2025-11-30"
  },
  "freeSlots": [
    {
      "startTime": "2025-11-25T14:00:00Z",
      "endTime": "2025-11-25T16:00:00Z",
      "durationMinutes": 120,
      "dayOfWeek": "Monday"
    },
    {
      "startTime": "2025-11-26T10:00:00Z",
      "endTime": "2025-11-26T12:30:00Z",
      "durationMinutes": 150,
      "dayOfWeek": "Tuesday"
    },
    {
      "startTime": "2025-11-27T15:00:00Z",
      "endTime": "2025-11-27T18:00:00Z",
      "durationMinutes": 180,
      "dayOfWeek": "Wednesday"
    }
  ],
  "totalFreeSlotsFound": 3
}
```

**Errors**:
- 400 Bad Request: 잘못된 날짜 범위, minDurationMinutes < 0
- 403 Forbidden: 그룹 멤버가 아님
- 404 Not Found: 그룹 존재하지 않음

**비즈니스 로직**:
1. 권한 검증: 요청자가 그룹 멤버인지 확인
2. 멤버 결정: `userIds` 또는 전체 그룹 멤버
3. 일정 조회: 개인 일정 + 그룹 일정
4. 알고리즘 실행: 병합 → 공강 추출 → 필터링
5. 응답 반환

### 3.2 그룹 일정 생성

**기존 API 재사용**: `POST /api/v1/schedules`

```json
{
  "groupId": 1,
  "categoryId": 5,
  "title": "팀 프로젝트 미팅",
  "description": "요구사항 분석 회의",
  "location": "공학관 101호",
  "startTime": "2025-11-25T14:00:00Z",
  "endTime": "2025-11-25T16:00:00Z",
  "isAllDay": false,
  "status": "TODO"
}
```

**로직**:
1. 권한 검증: 요청자가 OWNER 또는 ADMIN인지 확인 (GroupMemberService 호출)
2. **충돌 확인** (optional, 프론트엔드에서 경고):
   - 선택된 시간이 멤버들의 개인 일정과 겹치는지 확인
   - 겹치면 경고 메시지 반환 (강제 생성 가능)
3. Schedule 생성 (`group_id` 포함)
4. 알림 발송 (향후 구현)

**중요**: `user_id`는 NULL로 설정하고 `group_id`만 지정하여 그룹 일정임을 명시

### 3.3 일정 충돌 확인 (Helper API)

```
POST /api/v1/schedules/check-conflicts
```

**Request Body**:
```json
{
  "groupId": 1,
  "startTime": "2025-11-25T14:00:00Z",
  "endTime": "2025-11-25T16:00:00Z"
}
```

**Response** (200 OK):
```json
{
  "hasConflict": true,
  "conflicts": [
    {
      "userId": 123,
      "userName": "Alice",
      "schedule": {
        "scheduleId": 456,
        "title": "개인 약속",
        "startTime": "2025-11-25T15:00:00Z",
        "endTime": "2025-11-25T16:00:00Z"
      }
    }
  ]
}
```

**사용 시나리오**:
- 프론트엔드에서 시간 블록 선택 시 충돌 확인
- 충돌이 있으면 경고 표시 ("Alice는 개인 약속이 있습니다. 계속하시겠습니까?")
- 사용자가 확인 후 강제 생성 가능

---

## 4. 프론트엔드 연동

### 4.1 UI/UX 플로우

**1단계: 일정 조율 페이지**
- 그룹 페이지 → "일정 조율" 버튼 클릭
- 날짜 범위, 최소 지속 시간, 멤버 선택 (선택적)
- "공강 찾기" 버튼 클릭

**2단계: 공강 시간 표시**
- 캘린더 뷰 또는 리스트 뷰로 공강 시간 블록 표시
- 각 블록에 날짜, 시간, 지속 시간 표시
- 블록 클릭 → 그룹 일정 생성 모달

**3단계: 그룹 일정 생성**
- 일정 제목, 설명, 장소 입력
- "일정 생성" 버튼 클릭
- 충돌 확인 API 호출 → 경고 표시 (있을 경우)
- 확인 후 `POST /api/v1/schedules` 호출

**4단계: 완료**
- 그룹 일정 생성 성공
- 그룹 멤버들에게 알림 (향후 구현)

### 4.2 프론트엔드 예시 (React)

```typescript
// types.ts
interface FreeSlotsRequest {
  groupId: number;
  userIds?: number[];
  startDate: string;
  endDate: string;
  minDurationMinutes: number;
  workingHoursStart?: string;
  workingHoursEnd?: string;
  daysOfWeek?: number[];
}

interface FreeSlot {
  startTime: string;
  endTime: string;
  durationMinutes: number;
  dayOfWeek: string;
}

interface FreeSlotsResponse {
  groupId: number;
  groupName: string;
  freeSlots: FreeSlot[];
}

// hooks/useFreeSlots.ts
export const useFindFreeSlots = () => {
  return useMutation({
    mutationFn: async (request: FreeSlotsRequest) => {
      const response = await api.post('/schedules/find-free-slots', request);
      return response.data as FreeSlotsResponse;
    },
  });
};

// components/FreeSlotSelector.tsx
export const FreeSlotSelector = ({ groupId }: { groupId: number }) => {
  const { mutate: findFreeSlots, data: freeSlots } = useFindFreeSlots();

  const handleSearch = () => {
    findFreeSlots({
      groupId,
      startDate: '2025-11-25',
      endDate: '2025-11-30',
      minDurationMinutes: 120,
    });
  };

  return (
    <div>
      <button onClick={handleSearch}>공강 찾기</button>
      {freeSlots?.freeSlots.map((slot, idx) => (
        <div key={idx} onClick={() => handleSelectSlot(slot)}>
          {slot.dayOfWeek} {slot.startTime} - {slot.endTime}
        </div>
      ))}
    </div>
  );
};
```

---

## 5. 그룹 일정 생성

### 5.1 기존 API 재사용

**엔드포인트**: `POST /api/v1/schedules` (기존)

**Request Body**:
```json
{
  "groupId": 1,                          // 그룹 일정 표시
  "categoryId": 5,
  "title": "팀 프로젝트 미팅",
  "description": "요구사항 분석 회의",
  "location": "공학관 101호",
  "startTime": "2025-11-25T14:00:00Z",
  "endTime": "2025-11-25T16:00:00Z",
  "isAllDay": false,
  "status": "TODO",
  "source": "USER"
}
```

**DB 저장**:
```sql
INSERT INTO schedules (
  user_id, group_id, category_id, title, description, location,
  start_time, end_time, is_all_day, status, source
) VALUES (
  NULL, 1, 5, '팀 프로젝트 미팅', '요구사항 분석 회의', '공학관 101호',
  '2025-11-25T14:00:00Z', '2025-11-25T16:00:00Z', false, 'TODO', 'USER'
);
```

**중요**: `user_id = NULL`, `group_id = 1`로 설정하여 그룹 일정임을 명시

### 5.2 권한 검증

**로직** (ScheduleService):
```java
public ScheduleResponse createSchedule(ScheduleCreateRequest request, String cognitoSub) {
    // 그룹 일정인 경우 권한 확인
    if (request.getGroupId() != null) {
        GroupMember member = groupMemberService.getMember(request.getGroupId(), cognitoSub);

        // OWNER 또는 ADMIN만 그룹 일정 생성 가능
        if (member.getRole() == GroupRole.MEMBER) {
            throw new ForbiddenException("Only OWNER or ADMIN can create group schedules");
        }
    }

    // Schedule 생성
    Schedule schedule = Schedule.builder()
        .userId(request.getGroupId() != null ? null : cognitoSub)  // 그룹 일정이면 user_id = NULL
        .groupId(request.getGroupId())
        .categoryId(request.getCategoryId())
        .title(request.getTitle())
        // ... 기타 필드
        .build();

    scheduleRepository.save(schedule);
    return ScheduleResponse.from(schedule);
}
```

### 5.3 그룹 일정 조회

**엔드포인트**: `GET /api/v1/schedules?groupId={groupId}`

**로직**:
- 그룹 멤버인지 확인 (MEMBER도 조회 가능)
- `group_id = {groupId}` 조건으로 조회
- 응답에 `isGroupSchedule: true` 포함

**Response**:
```json
{
  "schedules": [
    {
      "scheduleId": 123,
      "groupId": 1,
      "isGroupSchedule": true,
      "title": "팀 프로젝트 미팅",
      "startTime": "2025-11-25T14:00:00Z",
      "endTime": "2025-11-25T16:00:00Z",
      "createdBy": {
        "userId": 456,
        "name": "Alice",
        "role": "OWNER"
      }
    }
  ]
}
```

---

## 6. 구현 파일

### 6.1 Schedule-Service

#### Domain 구조 (DDD)

```
com.unisync.schedule/
├── coordination/                         # 신규 도메인
│   ├── controller/
│   │   └── ScheduleCoordinationController.java
│   ├── service/
│   │   └── ScheduleCoordinationService.java
│   ├── dto/
│   │   ├── FindFreeSlotsRequest.java
│   │   ├── FindFreeSlotsResponse.java
│   │   ├── FreeSlotDto.java
│   │   └── ConflictCheckRequest.java
│   └── algorithm/
│       └── FreeSlotFinder.java           # 알고리즘 구현
│
├── schedule/
│   ├── service/
│   │   └── ScheduleService.java          # 기존 (권한 검증 추가)
│   └── ...
│
└── common/
    └── client/
        └── GroupMemberClient.java        # User-Service 호출 (RestTemplate)
```

#### 주요 로직

**ScheduleCoordinationService.java**:
```java
@Service
@RequiredArgsConstructor
public class ScheduleCoordinationService {

    private final ScheduleRepository scheduleRepository;
    private final GroupMemberClient groupMemberClient;
    private final FreeSlotFinder freeSlotFinder;

    public FindFreeSlotsResponse findFreeSlots(FindFreeSlotsRequest request, String cognitoSub) {
        // 1. 권한 확인 (그룹 멤버인지)
        groupMemberClient.checkMembership(request.getGroupId(), cognitoSub);

        // 2. 멤버 결정
        List<Long> userIds = request.getUserIds() != null
            ? request.getUserIds()
            : groupMemberClient.getGroupMemberUserIds(request.getGroupId());

        // 3. 일정 조회 (개인 + 그룹)
        List<Schedule> schedules = scheduleRepository.findByUserIdsOrGroupIdAndDateRange(
            userIds, request.getGroupId(), request.getStartDate(), request.getEndDate()
        );

        // 4. 알고리즘 실행
        List<FreeSlot> freeSlots = freeSlotFinder.findFreeSlots(
            schedules, request.getStartDate(), request.getEndDate(),
            request.getMinDurationMinutes(), request.getWorkingHoursStart(),
            request.getWorkingHoursEnd(), request.getDaysOfWeek()
        );

        // 5. 응답 반환
        return FindFreeSlotsResponse.builder()
            .groupId(request.getGroupId())
            .freeSlots(freeSlots)
            .build();
    }
}
```

**FreeSlotFinder.java** (알고리즘):
```java
@Component
public class FreeSlotFinder {

    public List<FreeSlot> findFreeSlots(
        List<Schedule> schedules,
        LocalDate startDate,
        LocalDate endDate,
        int minDurationMinutes,
        LocalTime workingHoursStart,
        LocalTime workingHoursEnd,
        List<Integer> daysOfWeek
    ) {
        // 1. Interval 추출
        List<Interval> busyIntervals = schedules.stream()
            .map(s -> new Interval(s.getStartTime(), s.getEndTime()))
            .sorted(Comparator.comparing(Interval::getStart))
            .collect(Collectors.toList());

        // 2. Interval 병합
        List<Interval> mergedBusy = mergeIntervals(busyIntervals);

        // 3. 공강 추출
        List<Interval> freeIntervals = extractFreeIntervals(
            mergedBusy, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), minDurationMinutes
        );

        // 4. 필터링 (근무 시간, 요일)
        if (workingHoursStart != null && workingHoursEnd != null) {
            freeIntervals = applyWorkingHours(freeIntervals, workingHoursStart, workingHoursEnd);
        }

        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            freeIntervals = filterByDaysOfWeek(freeIntervals, daysOfWeek);
        }

        // 5. DTO 변환
        return freeIntervals.stream()
            .map(FreeSlot::from)
            .collect(Collectors.toList());
    }

    private List<Interval> mergeIntervals(List<Interval> intervals) {
        // 알고리즘 구현 (2.2 참고)
    }

    // ... 기타 메서드
}
```

**GroupMemberClient.java** (User-Service 호출):
```java
@Component
@RequiredArgsConstructor
public class GroupMemberClient {

    private final RestTemplate restTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    public void checkMembership(Long groupId, String cognitoSub) {
        String url = userServiceUrl + "/internal/v1/groups/" + groupId + "/members/check";
        // X-Cognito-Sub 헤더로 전달
        // 403 Forbidden 시 예외 발생
    }

    public List<Long> getGroupMemberUserIds(Long groupId) {
        String url = userServiceUrl + "/internal/v1/groups/" + groupId + "/members/user-ids";
        // User IDs 목록 반환
    }
}
```

### 6.2 User-Service (Internal API 추가)

**GroupMemberController.java** (Internal API):
```java
@RestController
@RequestMapping("/internal/v1/groups")
@RequiredArgsConstructor
public class GroupMemberInternalController {

    private final GroupMemberService groupMemberService;

    @GetMapping("/{groupId}/members/check")
    public ResponseEntity<Void> checkMembership(
        @PathVariable Long groupId,
        @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        groupMemberService.checkMembership(groupId, cognitoSub);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{groupId}/members/user-ids")
    public ResponseEntity<List<Long>> getGroupMemberUserIds(
        @PathVariable Long groupId,
        @RequestHeader("X-Api-Key") String apiKey
    ) {
        // API Key 검증
        List<Long> userIds = groupMemberService.getGroupMemberUserIds(groupId);
        return ResponseEntity.ok(userIds);
    }
}
```

### 6.3 환경변수

**Schedule-Service** (`.env.schedule-service`):
```bash
USER_SERVICE_URL=http://user-service:8081
INTERNAL_API_KEY=your-secret-key
```

---

## 7. 테스트 전략

### 7.1 단위 테스트 (JUnit5 + Mockito)

**FreeSlotFinderTest**:
- `test_mergeIntervals_overlapping`: 겹치는 구간 병합 검증
- `test_mergeIntervals_noOverlap`: 겹치지 않는 구간은 그대로 유지
- `test_extractFreeIntervals_basic`: 기본 공강 추출
- `test_extractFreeIntervals_minDuration`: 최소 지속 시간 필터링
- `test_applyWorkingHours_filters`: 근무 시간 필터링
- `test_filterByDaysOfWeek`: 요일 필터링

**ScheduleCoordinationServiceTest**:
- `test_findFreeSlots_success`: 공강 찾기 성공
- `test_findFreeSlots_noFreeSlots`: 공강 없음
- `test_findFreeSlots_notGroupMember`: 그룹 멤버 아니면 403 Forbidden

### 7.2 통합 테스트 (System Tests)

**`system-tests/integration/test_schedule_coordination.py`**:

```python
def test_find_free_slots_basic(api_client, group_id, users):
    """
    기본 공강 찾기 테스트
    """
    # 1. User A, B의 일정 생성
    create_schedule(user_a, start="2025-11-25T09:00:00Z", end="2025-11-25T12:00:00Z")
    create_schedule(user_b, start="2025-11-25T14:00:00Z", end="2025-11-25T16:00:00Z")

    # 2. 공강 찾기
    response = api_client.post("/schedules/find-free-slots", json={
        "groupId": group_id,
        "startDate": "2025-11-25",
        "endDate": "2025-11-25",
        "minDurationMinutes": 60
    })

    assert response.status_code == 200
    free_slots = response.json()["freeSlots"]

    # 3. 예상 공강: 12:00-14:00, 16:00-23:59
    assert len(free_slots) == 2
    assert free_slots[0]["startTime"] == "2025-11-25T12:00:00Z"
    assert free_slots[0]["endTime"] == "2025-11-25T14:00:00Z"


def test_create_group_schedule_with_conflict(api_client, group_id):
    """
    충돌이 있는 그룹 일정 생성
    """
    # 1. User A의 개인 일정 생성
    create_schedule(user_a, start="2025-11-25T14:00:00Z", end="2025-11-25T16:00:00Z")

    # 2. 충돌 확인
    response = api_client.post("/schedules/check-conflicts", json={
        "groupId": group_id,
        "startTime": "2025-11-25T14:00:00Z",
        "endTime": "2025-11-25T16:00:00Z"
    })

    assert response.status_code == 200
    assert response.json()["hasConflict"] is True
    assert len(response.json()["conflicts"]) == 1

    # 3. 강제 생성 (프론트엔드에서 확인 후)
    response = api_client.post("/schedules", json={
        "groupId": group_id,
        "title": "팀 미팅",
        "startTime": "2025-11-25T14:00:00Z",
        "endTime": "2025-11-25T16:00:00Z",
        # ... 기타 필드
    })

    assert response.status_code == 201
```

### 7.3 성능 테스트

**시나리오**: 그룹 100명, 각 멤버 50개 일정 (총 5000개)

```python
def test_find_free_slots_performance(api_client, large_group_id):
    """
    대규모 그룹 공강 찾기 성능 테스트
    """
    import time

    start_time = time.time()
    response = api_client.post("/schedules/find-free-slots", json={
        "groupId": large_group_id,
        "startDate": "2025-11-25",
        "endDate": "2025-11-30",
        "minDurationMinutes": 120
    })
    elapsed = time.time() - start_time

    assert response.status_code == 200
    assert elapsed < 1.0  # 1초 이내 응답
```

---

## 8. 구현 체크리스트

### Phase 1: 공강 찾기
- [ ] FreeSlotFinder 알고리즘 구현 (mergeIntervals, extractFreeIntervals)
- [ ] ScheduleCoordinationService 구현
- [ ] ScheduleCoordinationController 구현
- [ ] DTO: FindFreeSlotsRequest, FindFreeSlotsResponse, FreeSlotDto
- [ ] User-Service: Internal API (checkMembership, getGroupMemberUserIds)
- [ ] GroupMemberClient 구현 (RestTemplate)
- [ ] 단위 테스트: FreeSlotFinderTest, ScheduleCoordinationServiceTest
- [ ] 통합 테스트: test_find_free_slots_basic

### Phase 2: 그룹 일정 생성
- [ ] ScheduleService: 권한 검증 로직 추가 (OWNER/ADMIN 확인)
- [ ] ScheduleService: user_id=NULL, group_id 설정 로직
- [ ] ConflictCheckController 구현 (충돌 확인 API)
- [ ] 통합 테스트: test_create_group_schedule_with_conflict

### Phase 3: 최적화 및 확장
- [ ] Redis 캐싱 (동일 그룹, 동일 기간 조회)
- [ ] 성능 테스트: 대규모 그룹 (100명, 5000개 일정)
- [ ] 알림 서비스 연동 (그룹 일정 생성 시 SQS 이벤트 발행)

---

## 9. 참고 문서

- [소셜 및 그룹 관리](social-and-groups.md) - 친구/그룹 관리 (User-Service)
- [일정 관리](schedule-management.md) - Schedule-Service 기본 설계
- [시스템 아키텍처](../design/system-architecture.md) - Schedules, Groups 테이블 정의
