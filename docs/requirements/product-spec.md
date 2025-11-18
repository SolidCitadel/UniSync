# UniSync
E-Campus 연동 일정관리 서비스 -> 학업 생산성 증대

**개발 현황**: Phase 2 진행 중 (Canvas 동기화 및 SQS 통합)

## 문제 정의
### 분리된 정보 관리
과제 정보는 LMS에, 일정 관리는 외부 캘린더 혹은 Todo앱에서 수행
1. LMS의 UI/UX는 불편함
2. 외부 앱은 수동 동기화 필요

### 수동 입력의 한계
1. 새로운 과제 등록을 확인하고 직접 입력
2. 번거로울 뿐더러 누락/오류 위험성

### 기존 연동 방식(iCal)의 한계
외부 캘린더로 캘린더 연동 가능
1. LMS의 일정을 단방향 동기화 -> 메모, subtask 등 불가
2. 제출/마감 이후에도 사라지지 않는 등 불편

## 핵심 기능
### Canvas LMS 자동 동기화
- Canvas API로 새로운 과제/변경사항을 자동 동기화
- 과제명, 설명, 마감일, 링크 등

### 자체적인 대시보드
- **일정(Schedule)과 할일(Todo) 분리 관리**
  - 일정: 시간 단위 이벤트 (캘린더 뷰)
  - 할일: 기간 단위 작업 (칸반보드, 간트차트)
- 분류, 필터링, 우선순위
- 다양한 시각화 도구: 캘린더, 칸반보드, 간트차트, 리스트

### AI 자동화
동기화 과정에서 자동으로 AI 분석 수행:
- 새 과제 감지 시 **일정(Schedule)으로 자동 등록**
- LLM이 과제 설명 분석하여 **할일(Todo) 및 서브태스크 자동 생성**
- 과제 제출물 자동 검사 및 요구사항 적합성 검증 (형식, 파일명 등)
- 제출 완료 시 일정/할일 상태 업데이트

**핵심**: Canvas 과제 = 일정(Schedule), 이를 달성하기 위한 계획 = 할일(Todo)

### 외부 서비스 연동
- Google Calendar, Todoist 등 외부 서비스와 양방향 연동

### 공강 시간 찾기
- When2Meet처럼 직접 시간대를 설정할 필요 없이, 친구들과 겹치지 않는 시간대를 자동으로 계산
- 같은 과목 수강생 간 친구 추천 기능

## 사용자 시나리오

### 1. 신규 사용자 온보딩
1. 회원가입 (이메일 또는 소셜 로그인)
2. Canvas에서 API 토큰 발급 (Settings → New Access Token)
3. UniSync 설정 페이지에서 Canvas API 토큰 입력
4. Canvas에서 수강 과목 및 과제 최초 동기화
5. 대시보드에서 동기화된 과제 및 AI로 생성된 일정 확인

### 2. Canvas 과제 자동 동기화 및 AI 분석
1. 교수가 Canvas에 새 과제 등록
2. Step Functions가 주기적으로 Canvas API 폴링하여 새 과제 감지
3. 새 과제 감지 시:
   - SQS로 이벤트 전송
   - Course-Service가 과제 정보 저장
   - **Schedule-Service가 일정(Schedule)으로 자동 등록** (과제 마감일)
   - LLM이 과제 설명 자동 분석하여 **할일(Todo) 및 서브태스크 자동 생성**
   - 할일은 일정과 연결됨 (`schedule_id`)
4. 사용자에게 알림
5. 프론트엔드에서 실시간으로 새 과제(일정) 및 할일 표시

### 3. 과제 제출물 자동 검사
1. 학생이 Canvas에 과제 제출
2. Step Functions가 Canvas API 폴링 중 제출 감지
3. LLM이 자동으로 제출물 메타데이터 검증:
   - 파일 형식이 요구사항과 일치하는지 확인
   - 파일명이 규칙에 맞는지 확인
   - 제출물 개수가 적절한지 확인
4. 검증 결과를 사용자에게 알림:
   - 문제가 있으면 경고 메시지 표시
   - 문제 없으면 관련 일정/할일 상태 업데이트 제안
5. 사용자가 직접 일정/할일 상태를 완료로 변경

### 4. 외부 캘린더 연동
1. 설정 페이지에서 Google Calendar 연동
2. OAuth2 인증 후 캘린더 권한 부여
3. UniSync의 과제/일정이 Google Calendar에 자동 동기화
4. Google Calendar에서 생성한 일정도 UniSync로 동기화
5. 양방향 실시간 동기화 유지

### 5. 친구와 공강 시간대 찾기
1. Social 탭에서 친구 추가 (같은 과목 수강생 추천 목록 제공)
2. "공강 시간 찾기" 메뉴 선택
3. 참여할 친구들 선택
4. 시스템이 모든 친구의 시간표를 분석하여 겹치지 않는 시간대 표시
5. 적합한 시간대 선택 시 모든 참여자에게 일정 생성 요청

## 구현 방안
- Canvas API (Access Token 방식)
- OAuth2 (Google, Todoist 등)

### 아키텍처

#### Api-Gateway
AWS ACM + Cognito + ALB
- HTTPS
- JWT

#### Microservices (Spring Boot)

**User-Service**
- Users, Credentials, Friendships
- 회원가입 및 인증
- Canvas/Google/Todoist 토큰 저장 (암호화)
- 사용자 프로필 설정
- 친구 관계 관리 및 추천

**Course-Service**
- Courses, Enrollments, Assignments, Notices
- Canvas 학업 데이터 관리
- 과목/수강생/과제/공지 조회
- Leader 선출 및 관리
- SQS로부터 Canvas 동기화 이벤트 수신

**Schedule-Service**
- Schedules (일정: 시간 단위), Todos (할일: 기간 단위), Categories, Groups
- **일정 관리**: Canvas 과제 일정, Google Calendar 동기화, 사용자 생성 일정
- **할일 관리**: LLM 자동 생성 할일, 서브태스크, 사용자 직접 생성 할일
- 카테고리별 분류, 그룹 기반 협업
- 공강 시간대 계산
- SQS로부터 Canvas 과제 및 외부 캘린더 동기화 이벤트 수신

#### Serverless Sync Components

**Canvas-Sync-Workflow (Step Functions + Lambda)**
- Canvas API 폴링 (과제, 공지, 제출물)
- 변경 사항 감지 및 SQS 이벤트 발행
- Leader 토큰으로 과목당 1회만 API 호출
- LLM Lambda 트리거 (할일 자동 생성, 제출물 검증)
- **플로우**: Canvas 과제 → 일정 자동 등록 → LLM 분석 → 할일 자동 생성

**Google-Calendar-Sync-Workflow (Step Functions + Lambda)**
- Google Calendar API 폴링
- 변경 사항 감지 및 SQS 이벤트 발행
- Webhook 수신 처리

**LLM-Lambda (Python)**
- 과제 설명 분석 → 할일(Todo) 및 서브태스크 자동 생성
- 제출물 요구사항 검증 (파일 형식, 파일명, 개수)
- 생성된 할일은 자동으로 일정과 연결됨

## 기술

### 프론트엔드
- React

### 백엔드
- Spring
- Swagger
- AWS SDK
- MySQL (RDS)

### 개발 환경
- docker-compose
- LocalStack
  - ALB & Cognito
  - SQS
  - EventBridge
  - Lambda
  - RDS
  - ECS & Fargate

### 배포
- GitHub Actions
- AWS ECR

## 구현 현황

### ✅ 완료된 기능
- **인프라 구축**
  - Docker Compose 기반 개발 환경
  - LocalStack을 통한 AWS 서비스 에뮬레이션 (SQS, Lambda, Step Functions)
  - MySQL 데이터베이스 설정 및 초기화

- **백엔드 서비스**
  - Spring Boot 마이크로서비스 기본 구조 (User, Course, Schedule)
  - **API Gateway (Spring Cloud Gateway + JWT 인증 + Cognito 연동)**
  - Course-Service의 SQS 구독 및 Assignment 처리
  - 공유 모듈(java-common, python-common) 기반 DTO 표준화

- **서버리스 컴포넌트**
  - Canvas Sync Lambda 구현 및 배포
  - LLM Lambda 구현 및 배포
  - SQS 기반 이벤트 드리븐 아키텍처

- **테스트 환경**
  - E2E 통합 테스트 인프라 구축
  - Lambda 단위 테스트
  - Canvas API → SQS → Course-Service → DB 플로우 검증

### 🚧 진행 중
- User-Service의 인증 및 Canvas 토큰 관리
- Schedule-Service의 일정 통합 기능
- Step Functions 워크플로우 구성
- LLM Task 생성 자동화

### 📋 예정
- 프론트엔드 구현 (React)
- Google Calendar 연동
- Todoist 연동
- 친구 관리 및 공강 시간 찾기 기능
- 실시간 알림 (WebSocket)

