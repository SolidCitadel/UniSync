# UniSync Documentation

UniSync 프로젝트의 모든 문서를 담고 있습니다.

## 📂 문서 구조

### 📋 [requirements/](requirements/)
**요구사항 분석 및 비즈니스 기획**

- [product-spec.md](requirements/product-spec.md) - 프로젝트 기획서 (문제 정의, 핵심 기능, 사용자 시나리오)

### 🏗️ [design/](design/)
**시스템 설계 및 아키텍처**

- [system-architecture.md](design/system-architecture.md) - 전체 시스템 아키텍처, 데이터 모델, API 설계
- [sqs-architecture.md](design/sqs-architecture.md) - **SQS 메시지 아키텍처 (큐 목록, 메시지 스키마, 재시도 전략)**

### ⚙️ [features/](features/)
**기능별 상세 명세 (도메인 주도 문서화)**

- [testing-strategy.md](features/testing-strategy.md) - **테스트 전략 및 계층 구조 (Unit/Integration/E2E)**
- [canvas-sync.md](features/canvas-sync.md) - **Canvas LMS 동기화 상세 설계 (✅ Phase 1 구현 완료)**
- [assignment-to-schedule.md](features/assignment-to-schedule.md) - **과제 → 일정/할일 자동 변환 (🚧 Phase 1 구현 예정)**
- [schedule-management.md](features/schedule-management.md) - 일정 및 할일 관리 기능 설계
- [google-calendar-integration.md](features/google-calendar-integration.md) - Google Calendar 연동 구현 계획
- [api-endpoint-migration.md](features/api-endpoint-migration.md) - API 엔드포인트 마이그레이션
- [acceptance-test.md](features/acceptance-test.md) - E2E 통합 테스트 및 개발 현황

### 📝 [adr/](adr/)
**Architecture Decision Records (아키텍처 결정 기록)**

- [README.md](adr/README.md) - ADR 작성 가이드 및 주요 결정사항 목록

### 📚 [guides/](guides/)
**개발자 가이드**

- [README.md](guides/README.md) - 개발자 온보딩 및 가이드 목록

## 🗺️ 문서 탐색 가이드

### 처음 시작하는 분
1. [product-spec.md](requirements/product-spec.md) - 프로젝트가 무엇인지 이해
2. [system-architecture.md](design/system-architecture.md) - 전체 시스템 구조 파악
3. [루트 README.md](../README.md) - 개발 환경 설정

### 특정 기능 개발 시
1. [features/](features/) - 해당 기능의 상세 명세 확인
2. [adr/](adr/) - 관련 아키텍처 결정 배경 이해
3. [system-architecture.md](design/system-architecture.md) - 전체 시스템과의 통합 지점 확인

### 새로운 기능 설계 시
1. 기존 [features/](features/) 문서 참고
2. [adr/](adr/)에 주요 결정사항 기록
3. 필요시 새 feature 문서 작성

## 📌 문서 작성 원칙

1. **설계 중심**: 문서는 작업 관리가 아닌 설계와 지식 공유를 위한 것
2. **도메인 주도**: 기능별로 독립된 문서 유지 (단일 진실 공급원)
3. **참조 연결**: 중복 대신 링크로 연결
4. **영어 파일명**: 국제 협업 대비 및 URL 친화적

## 📐 문서화 철학: Master vs Reference

### Master 문서 (docs/)
**위치**: `docs/design/`, `docs/features/`
**역할**: Single Source of Truth (설계의 유일한 진실)
**내용**:
- **무엇을**, **왜** 만드는가 (What, Why)
- 아키텍처 결정 및 배경
- 전체 시스템 관점
- 데이터 모델, API 명세
- 설계 철학, 제약사항

**예시**:
- `docs/design/sqs-architecture.md` - 전체 SQS 큐 목록, 메시지 스키마
- `docs/features/canvas-sync.md` - Canvas 동기화 설계

### Reference 문서 (app/)
**위치**: `app/backend/`, `app/serverless/`, `app/shared/`
**역할**: 개발자 빠른 참조 (구현 중심)
**내용**:
- **어떻게** 사용/실행하는가 (How)
- 개발 환경 설정
- 실행 방법, 사용법
- 간단한 요약 + Master 문서 참조

**예시**:
- `app/shared/README.md` - DTO 사용법 + `docs/design/sqs-architecture.md` 참조
- `app/backend/CLAUDE.md` - 환경변수, 포트, 실행 방법

### 원칙

1. **중복 금지**: 설계 내용은 Master에만, Reference는 참조만
2. **역할 분리**: 설계(docs) vs 구현(app)
3. **업데이트 우선순위**: Master 먼저 업데이트 → Reference는 참조 유지

## 🔗 관련 문서

- [CLAUDE.md](../CLAUDE.md) - AI 어시스턴트를 위한 프로젝트 컨텍스트
- [tests/README.md](../tests/README.md) - 테스트 구조 및 실행 방법
- [app/backend/CLAUDE.md](../app/backend/CLAUDE.md) - 백엔드 서비스 구조
- [app/serverless/CLAUDE.md](../app/serverless/CLAUDE.md) - 서버리스 구조
