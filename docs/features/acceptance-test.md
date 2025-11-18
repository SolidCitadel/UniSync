# E2E 자동 동기화 플로우 구현 TODO

## 🎯 E2E 테스트 목표 플로우

### 입력 (사용자 액션)
1. `POST /api/v1/auth/signup` - 회원가입 → `cognitoSub` 발급
2. `POST /api/v1/auth/login` - 로그인 → JWT 토큰 획득
3. `POST /api/v1/credentials/canvas` (JWT + Canvas Token) - Canvas 토큰 등록

### 기대 결과 (비동기 처리 후)
1. `GET /api/v1/integrations/status` (JWT)
   - `canvas.isConnected = true`
   - `canvas.externalUsername = "2021105636"` (실제 Canvas 사용자명)

2. `GET /api/v1/courses` (JWT)
   - 사용자가 수강 중인 Course 목록 반환 (예: 10개)
   - 각 Course에 `canvasCourseId`, `name`, `courseCode` 포함

3. `GET /api/v1/courses/{courseId}/assignments` (JWT)
   - 해당 Course의 Assignment 목록 반환
   - 각 Assignment에 `canvasAssignmentId`, `title`, `dueAt` 포함

### 핵심
**3번(토큰 등록) API 호출 후 비동기 동기화가 완료되면, 2번과 3번 조회 API에서 Canvas 데이터가 자동으로 조회되어야 함**

---

## 📋 구현 체크리스트

### Phase 1: 기본 인프라 및 DB
- [x] Docker Compose 환경 구성
- [x] LocalStack (SQS, Lambda, Cognito)
- [x] MySQL 8.0 (User DB, Course DB)
- [x] Spring Boot 서비스 (User, Course, Schedule)
- [x] API Gateway (Spring Cloud Gateway)

### Phase 2: Canvas 동기화 플로우
- [x] SQS 큐 생성 (7개)
- [x] User-Service: Canvas 토큰 등록 API
- [x] User-Service: SQS 이벤트 발행 (user-token-registered)
- [x] User-Service: 연동 상태 조회 API
- [x] Lambda: Canvas Course 동기화 (initial_sync_handler)
- [x] Lambda: Canvas Assignment 동기화 (assignment_sync_handler)
- [x] Course-Service: Enrollment 엔티티
- [x] Course-Service: Course SQS 리스너
- [x] Course-Service: Assignment SQS 리스너
- [x] Course-Service: Course 조회 API

### Phase 3: 인증 및 보안
- [x] LocalStack Cognito User Pool 설정
- [x] User-Service: 회원가입/로그인 API (Cognito 연동)
- [x] API Gateway: JWT 인증 필터
- [x] cognitoSub 마이그레이션 (User/Course/Lambda 전체)
- [x] AuthResponse DTO: userId → cognitoSub 변경
- [x] API 경로 매핑 수정 (Credentials, Integration)

### Phase 4: 테스트 및 자동화
- [x] User-Service 유닛 테스트 (28/28 passed)
- [x] Course-Service 통합 테스트 (19/19 passed)
- [x] Lambda 테스트 (canvas-sync: 8/8, llm: 11/11)
- [x] E2E 테스트: JWT 인증 + Canvas 동기화
- [x] Docker Compose 테스트 환경
- [x] LocalStack 데이터 영속성 (Named Volume)
- [x] 테스트 자동화 스크립트 (test-e2e.sh, test-e2e.bat)

### Phase 5: Canvas 동기화 완료
- [x] **Course 동기화 E2E 플로우 완성** ✅
  - User-Service: cognitoSub 기반 AuthResponse 반환
  - Lambda: 내부 API 경로 수정 (/credentials/...)
  - Course-Service: API Gateway RewritePath 매칭 (/courses)
  - E2E 테스트 통과: JWT 인증 → Canvas 토큰 등록 → Course 자동 동기화 (10개) → API 조회 성공
- [x] **Assignment 동기화 워크플로우 완성** ✅
  - Lambda: AssignmentEventMessage DTO 필드 정합성 수정
  - canvasCourseId 추가, submissionTypes/dueAt 포맷 변환
  - Course-Service: Assignment SQS 리스너 정상 동작
  - E2E 테스트 통과: Assignment 자동 동기화 (5개) → API 조회 성공

### Phase 6: Schedule 및 확장 기능 (진행 예정)
- [ ] **Schedule-Service 일정 통합 기능**
  - Schedules, Todos, Categories 엔티티 구현
  - Canvas Assignment → Schedule 자동 생성
  - SQS 리스너: assignment-events-queue 구독
- [ ] **Google Calendar 동기화 워크플로우**
  - Google OAuth2 인증 플로우
  - Google Calendar API 연동
  - 양방향 동기화
- [ ] **LLM Task 생성 자동화**
  - Assignment 설명 분석
  - Todo/Subtask 자동 생성
  - 제출물 자동 검증

---

## 📊 현재 상태 (2025-11-18)

### ✅ 구현 완료
- **인증 시스템**: JWT 인증, 회원가입/로그인 API, cognitoSub 마이그레이션
- **Canvas 토큰**: 등록/조회/삭제 API, 연동 상태 조회 (실제 Canvas API 검증 완료)
- **Course 동기화**: Lambda Course 동기화, Course-Service SQS 리스너, E2E 테스트 (10 courses)
- **Assignment 동기화**: Lambda Assignment 동기화, Assignment-Service SQS 리스너, E2E 테스트 (5 assignments)
- **테스트**: 유닛/통합 테스트 66/66 passed (100%), E2E 테스트 완료 (Course + Assignment)

### ✅ E2E 테스트 현재 보장 범위 (test_canvas_sync_with_jwt_e2e.py)
1. JWT 인증: 회원가입 → 로그인 → JWT 토큰 획득 ✅
2. Canvas 토큰 등록: cognitoSub 기반 저장 ✅
3. 연동 상태 조회: Canvas username 2021105636 확인 ✅
4. **Course 동기화: 10개 Course 자동 동기화 성공 ✅**
5. **Course 조회 API: API Gateway 경유 조회 성공 ✅**
6. **Assignment 동기화: 5개 Assignment 자동 동기화 성공 ✅**
7. **Assignment 조회 API: API Gateway 경유 5개 Assignment 조회 성공 ✅**

### 🎉 해결된 이슈

**1. Course 동기화 E2E 성공** (2025-11-07 해결)
- **원인**: API Gateway RewritePath 필터와 Controller 경로 불일치
  - User-Service: `/api/v1/credentials` → `/credentials` (수정 완료)
  - Course-Service: `/api/v1/courses` → `/courses` (수정 완료)
- **해결 과정**:
  1. User-Service 재빌드 (AuthResponse cognitoSub 반환)
  2. Lambda 내부 API 경로 수정 (`/credentials/...`)
  3. Course-Service Controller 경로 수정 (`/courses`)
- **결과**: 10개 Course 동기화 성공

**2. Assignment 동기화 E2E 성공** (2025-11-07 해결)
- **원인**: Lambda가 AssignmentEventMessage DTO와 맞지 않는 필드 전송
  - 문제 1: `submissionTypes`를 list로 전송 (Java는 String 기대)
  - 문제 2: `dueAt`에 timezone 포함 (Java LocalDateTime은 timezone 없음)
  - 문제 3: `canvasCourseId` 누락 (DTO 필수 필드)
  - 문제 4: `courseId` 잘못 전송 (DTO에 없는 필드)
- **해결 과정**:
  1. Lambda: submissionTypes를 comma-separated string으로 변환
  2. Lambda: dueAt에서 timezone 제거 (ISO 8601 → LocalDateTime)
  3. Lambda: canvasCourseId 필드 추가 (handler.py lines 136, 211)
  4. Lambda: courseId 필드 제거 (DTO에 없음)
  5. LocalStack 재시작하여 수정된 Lambda 배포
- **결과**: 5개 Assignment 동기화 및 API 조회 성공 (1 passed in 19.23s)

### 📍 다음 작업 (Phase 6)
1. **Schedule-Service 구현** (최우선)
   - Schedules, Todos, Categories 엔티티 및 Repository
   - Canvas Assignment → Schedule 자동 생성 로직
   - SQS 리스너 구현
   - 기본 CRUD API
2. **Google Calendar 동기화 워크플로우**
   - Google OAuth2 인증 플로우
   - Google Calendar API 연동 Lambda
   - 양방향 동기화 구현
3. **LLM 기반 자동화**
   - Assignment 설명 분석 → Todo/Subtask 생성
   - 제출물 자동 검증 → Task 상태 업데이트

---

## 🔑 핵심 설계 원칙
1. **이벤트 드리븐**: SQS 기반 비동기 통신
2. **cognitoSub 사용**: JWT Claim이 곧 사용자 식별자 (DB 조회 불필요)
3. **Leader 선출**: Course당 첫 등록자만 Canvas API 호출
4. **멱등성**: 중복 이벤트 처리 방지
5. **느슨한 결합**: 서비스 간 직접 의존 없음

---

## 📝 최근 업데이트

### 2025-11-18: 문서 구조 개선
- docs/ 폴더 체계화 (adr, requirements, design, features, guides)
- 문서 파일 영문명 변경 및 참조 링크 업데이트

### 2025-11-07: Canvas 동기화 완료

### cognitoSub 마이그레이션 완료
- **AuthResponse/AuthService**: userId → cognitoSub 변경
- **테스트**: User-Service, E2E 모두 cognitoSub 기반으로 수정
- **API 경로**: Credentials, Integration 컨트롤러 RewritePath 이슈 해결
- **LocalStack 영속성**: Named volume + 자동화 스크립트

### 아키텍처 개선
**Before**: JWT → User DB 조회 → userId 변환 (느림, 복잡)
**After**: JWT → cognitoSub 직접 사용 (빠름, 간단)

- ✅ DB 조회 불필요 → 성능 향상
- ✅ User 테이블 장애 시에도 인증 가능
- ✅ 마이크로서비스 간 의존성 제거
