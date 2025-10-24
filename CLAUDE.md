# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요
Canvas LMS 연동 학업 일정관리 서비스. **자동 동기화 + AI 분석**으로 수동 입력 제거.

**현재 상태**: 기획/설계 완료, 구현 시작 전

## 아키텍처
- **마이크로서비스**: User/Course/Sync/Schedule/Social (Spring Boot, 서비스별 DB 분리)
- **서버리스**: Sync-Workflow (Step Functions), LLM (Lambda)
- **이벤트 기반**: SQS로 비동기 통신

## 중요한 설계 결정

### 1. Canvas API 토큰 방식 (OAuth2 ❌)
- 사용자가 Canvas에서 직접 API 토큰 발급 → UniSync에 입력
- AES-256 암호화 저장
- Credentials 테이블에 `provider='CANVAS'`로 저장

### 2. AI 자동화 (사용자 버튼 ❌)
- 새 과제 감지 → LLM 자동 분석 → task/subtask 생성
- 제출물 감지 → LLM 자동 검증 → 유효하면 task 상태 DONE
- **사용자 액션 없이 Sync-Workflow에서 자동 실행**

### 3. Leader 선출 (과목당 1명만 Canvas API 호출)
- 과목 첫 연동자가 Leader (`is_sync_leader=true`)
- Leader 토큰으로만 Canvas API 폴링 → 비용 절감
- 조회 데이터는 모든 수강생 공유

## 핵심 워크플로우

```
EventBridge (5분마다)
  → Step Functions
  → Canvas API 폴링 (Leader 토큰)
  → 새 과제 감지
     → SQS: assignment-events-queue
     → LLM Lambda: 분석
     → SQS: task-creation-queue
     → Sync-Service: task 저장
  → 제출 감지
     → SQS: submission-events-queue
     → LLM Lambda: 검증
     → Sync-Service: task 상태 업데이트
```

## 데이터 모델 핵심
- **Assignments**: `canvas_assignment_id` (UNIQUE)
- **Tasks**: `assignment_id` FK, `parent_task_id` (자기참조), `is_ai_generated`
- **Enrollments**: `is_sync_leader` (Leader 플래그)
- **Credentials**: `provider` ENUM, `access_token` (암호화)

## SQS 메시지

### assignment-events-queue
```json
{ "eventType": "ASSIGNMENT_CREATED", "assignmentId": "canvas_123", ... }
```

### submission-events-queue
```json
{ "eventType": "SUBMISSION_DETECTED", "userId": 1, "submissionMetadata": {...} }
```

### task-creation-queue (LLM → Sync-Service)
```json
{ "assignmentId": 10, "tasks": [{"title": "...", "subtasks": []}] }
```

## 주의사항

### 절대 금지
- Canvas 토큰 평문 저장
- 서비스 간 DB 직접 접근 (반드시 API/이벤트)
- 사용자 입력 검증 생략

### 핵심 원칙
- JWT에서 user_id 추출하여 본인 데이터만 접근
- 외부 API 호출은 SQS 비동기 처리
- Entity 직접 반환 금지 (DTO 변환)

## 개발 환경 설정 (구현 시 사용)

### 로컬 실행
```bash
# 1. 인프라 시작
docker-compose up -d localstack mysql

# 2. 각 서비스 실행 (Maven)
cd user-service && ./mvnw spring-boot:run
cd course-service && ./mvnw spring-boot:run
cd sync-service && ./mvnw spring-boot:run
cd schedule-service && ./mvnw spring-boot:run
cd social-service && ./mvnw spring-boot:run

# 3. 프론트엔드 실행
cd frontend && npm run dev
```

### 테스트
```bash
# 단위 테스트
./mvnw test

# 통합 테스트
./mvnw verify

# 특정 테스트 실행
./mvnw test -Dtest=AssignmentServiceTest
```

### API 문서
- Swagger UI: http://localhost:808{1-5}/swagger-ui.html (각 서비스별)

## 서비스 포트
- User-Service: 8081
- Course-Service: 8082
- Sync-Service: 8083
- Schedule-Service: 8084
- Social-Service: 8085
- Frontend: 3000

## 참고 문서
- [기획서](./기획.md) - 문제 정의, 핵심 기능, 사용자 시나리오
- [설계서](./설계서.md) - 상세 아키텍처, API 설계, DB 스키마, 배포 전략