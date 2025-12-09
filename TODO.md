# UniSync 미해결 이슈 목록

> 마지막 업데이트: 2025-12-09

## ✅ 완료된 작업

### 주요 기능
- [x] API Gateway 500 에러 해결 (CORS, Cognito 인증)
- [x] Canvas Sync Lambda 연동 완료
- [x] SQS 메시지 크기 제한 해결 (Course 단위 분할 전송)
- [x] **Course → Schedule 이벤트 발행 문제 해결** ✨
  - 처리 순서 변경 (Assignment → Enrollment)
  - LocalStack 하드코딩 수정 (accountId 000000000000 → 전체 URL 사용)
  - DefaultCredentialsProvider 추가

### 테스트 결과 (2025-12-09)
| 기능 | 상태 | 비고 |
|------|------|------|
| 회원가입 | ✅ | `POST /api/v1/auth/signup` |
| 로그인 | ✅ | `POST /api/v1/auth/signin` |
| 사용자 정보 조회 | ✅ | `GET /api/v1/users/me` |
| Canvas 토큰 등록 | ✅ | `POST /api/v1/integrations/canvas/credentials` |
| Canvas 토큰 조회 | ✅ | `GET /api/v1/integrations/canvas/credentials` |
| Canvas 동기화 | ✅ | `POST /api/v1/integrations/canvas/sync` |
| Course 조회 | ✅ | `GET /api/v1/courses` (23개) |
| Assignment 조회 | ✅ | `GET /api/v1/courses/{id}/assignments` |
| **Schedule 조회** | ✅ | `GET /api/v1/schedules` (44개) |
| 토큰 갱신 | ❌ | 미구현 |

---

## 🟡 개선 가능 (선택사항)

### 1. 토큰 갱신 API 
- `POST /api/v1/auth/refresh` → 404 (미구현)
- Cognito에서 직접 처리 가능, 프론트엔드 요구사항에 따라 구현

### 2. API Gateway 라우팅 정리
- `/credentials/**`, `/sync/**` 경로가 `/integrations/**`로 우회 사용 중
- 문서 동기화 또는 명시적 라우팅 추가 권장

### 3. DLQ 모니터링
- 자동 알림 설정 권장 (CloudWatch Alarm)

---

## 📊 시스템 현황

| 리소스 | 상태 | 비고 |
|-------|------|------|
| ECS Cluster | ✅ Running | 4 services |
| RDS MySQL | ✅ Running | unisync-mysql |
| SQS Queues | ✅ Active | 3 queues |
| Lambda | ✅ Active | canvas-sync-lambda |
| EventBridge | ✅ Active | 매시간 자동 동기화 |
