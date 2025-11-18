# UniSync Documentation

UniSync 프로젝트의 모든 문서를 담고 있습니다.

## 📂 문서 구조

### 📋 [requirements/](requirements/)
**요구사항 분석 및 비즈니스 기획**

- [product-spec.md](requirements/product-spec.md) - 프로젝트 기획서 (문제 정의, 핵심 기능, 사용자 시나리오)

### 🏗️ [design/](design/)
**시스템 설계 및 아키텍처**

- [system-architecture.md](design/system-architecture.md) - 전체 시스템 아키텍처, 데이터 모델, API 설계

### ⚙️ [features/](features/)
**기능별 상세 명세 (도메인 주도 문서화)**

- [schedule-management.md](features/schedule-management.md) - 일정 및 할일 관리 기능 설계
- [google-calendar-integration.md](features/google-calendar-integration.md) - Google Calendar 연동 구현 계획
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

## 🔗 관련 문서

- [CLAUDE.md](../CLAUDE.md) - AI 어시스턴트를 위한 프로젝트 컨텍스트
- [tests/README.md](../tests/README.md) - 테스트 구조 및 실행 방법
- [app/backend/CLAUDE.md](../app/backend/CLAUDE.md) - 백엔드 서비스 구조
- [app/serverless/CLAUDE.md](../app/serverless/CLAUDE.md) - 서버리스 구조
