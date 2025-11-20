# CLAUDE.md

## 프로젝트 개요
Canvas LMS 연동 학업 일정관리 서비스. Canvas 과제를 자동 동기화하여 일정/할일로 변환.

## 아키텍처
- **마이크로서비스** (Spring Boot, 서비스별 DB 분리)
  - User-Service: 사용자/인증/소셜/그룹
  - Course-Service: Canvas 학업 데이터 (과목/과제)
  - Schedule-Service: 일정(시간 단위) + 할일(기간 단위) 통합 관리
- **서버리스**: Step Functions (Canvas-Sync-Workflow, Google-Calendar-Sync-Workflow) + Lambda
- **이벤트 기반**: SQS 비동기 통신

## 기술 스택
- **Backend**: Java 21, Spring Boot 3.5.7, Gradle 8.5 (Kotlin DSL), MySQL 8.0, Spring Data JPA
- **Auth**: AWS Cognito + JWT
- **Infra**: Docker Compose + LocalStack (로컬), SQS, Step Functions, Lambda, S3

## 중요한 설계 결정

### 1. Canvas API 토큰 방식 (OAuth2 사용 안함)
- 사용자가 Canvas에서 직접 API 토큰 발급 후 입력
- AES-256 암호화 저장
- Credentials 테이블 `provider='CANVAS'`

### 2. 단계별 구현 전략
**Phase 1 (✅ 구현 완료): 수동 API 기반 동기화**
- User-Service: POST /v1/sync/canvas 엔드포인트로 Canvas Sync Lambda 직접 호출 (AWS SDK)
- Lambda: Canvas API 조회 후 SQS로 enrollments/assignments 메시지 발행
- Course-Service: SQS 메시지 consume하여 DB 저장
- 사용자가 명시적으로 동기화 버튼 클릭

**Phase 2 (계획): EventBridge 자동 동기화**
- EventBridge 스케줄러로 주기적 자동 호출
- 사용자 설정에 따른 동기화 주기 조정

**Phase 3 (선택): LLM 기반 자동화 (시간 여유 시)**
- 새 과제 감지 → LLM 자동 분석 → task/subtask 생성
- 제출물 감지 → LLM 자동 검증 → 유효시 task 상태 DONE

### 3. Leader 선출 (과목당 1명만 Canvas API 호출)
- 과목 첫 연동자가 Leader (`is_sync_leader=true`)
- Leader 토큰으로만 Canvas API 폴링 (비용 절감)
- 조회 데이터는 모든 수강생 공유

## 데이터 모델 핵심
- **Assignments**: `canvas_assignment_id` UNIQUE (Course-Service)
- **Schedules**: `start_time`, `end_time`, `source` (CANVAS/USER/GOOGLE), `category_id` 필수 (Schedule-Service)
- **Todos**: `start_date`, `due_date` 필수, `schedule_id` FK, `parent_todo_id` (서브태스크) (Schedule-Service)
- **Categories**: 일정/할일 분류, 개인/그룹별 (Schedule-Service)
- **Groups**: 협업 그룹, 권한 관리 (OWNER, ADMIN, MEMBER) (User-Service)
- **Group_Members**: 그룹 멤버십 및 역할 (User-Service)
- **Enrollments**: `is_sync_leader` Leader 플래그 (Course-Service)
- **Credentials**: `provider` ENUM, `access_token` 암호화 (User-Service)

## 주의사항

### 절대 금지
- Canvas 토큰 평문 저장
- 서비스 간 DB 직접 접근 (반드시 API/이벤트)
- 사용자 입력 검증 생략

### 핵심 원칙
- JWT에서 user_id 추출하여 본인 데이터만 접근
- 외부 API 호출은 SQS 비동기 처리
- Entity 직접 반환 금지 (DTO 변환)

## 코드 구조 원칙 (DDD)

### 도메인 단위 패키지 구조
각 마이크로서비스는 도메인 단위(Domain-based) 구성. Layer-based 구조 사용 안함.

```
com.unisync.{service}/
├── {domain}/           # 도메인별
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── exception/
└── common/             # 공통
    ├── entity/         # 엔티티 (DB 모델)
    ├── repository/     # JPA Repository
    ├── config/         # 설정
    └── exception/      # 공통 예외
```

### 핵심 원칙
- 관련 기능은 같은 도메인 패키지 배치 (High Cohesion)
- 도메인 간 직접 의존 금지, `common`으로 공유 (Low Coupling)
- Entity는 `common/entity`, Repository는 `common/repository`
- 도메인 특화 예외는 각 도메인, 공통 예외는 `common/exception`
- Entity 직접 반환 금지, 각 도메인의 DTO만 사용

## 모듈별 상세 문서
- [Backend Services](app/backend/CLAUDE.md) - 백엔드 환경변수, 서비스 포트, API Gateway
- [Serverless](app/serverless/CLAUDE.md) - 워크플로우, Lambda, SQS 메시지 스키마
- [Shared Modules](app/shared/README.md) - java-common, python-common DTO 표준화
- [Tests](tests/README.md) - 테스트 구조 및 실행 방법

## 참고 문서
- [프로젝트 기획서](docs/requirements/product-spec.md) - 문제 정의, 핵심 기능, 사용자 시나리오
- [시스템 아키텍처](docs/design/system-architecture.md) - 상세 아키텍처, API 설계, DB 스키마, 배포 전략
- **[SQS 아키텍처](docs/design/sqs-architecture.md) - SQS 큐 목록, 메시지 스키마, 재시도 전략**
- [Canvas 동기화](docs/features/canvas-sync.md) - Canvas LMS 연동 상세 설계 (✅ Phase 1 완료)
- [과제 → 일정 변환](docs/features/assignment-to-schedule.md) - 과제를 일정/할일로 자동 변환 (🚧 Phase 1 구현 예정)
- [문서 구조](docs/README.md) - 모든 설계 문서 탐색 가이드
