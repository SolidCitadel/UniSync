# Developer Guides

개발자 온보딩 및 개발 가이드 문서 모음입니다.

## 시작하기

개발 환경 설정 및 프로젝트 실행 방법은 [루트 README.md](../../README.md)를 참고하세요.

## 가이드 목록

### 📚 작성 완료

- **[Development Setup](./development-setup.md)**: Poetry 기반 개발 환경 설정 (의존성 관리, 테스트 실행)
- **[Deployment](./deployment.md)**: AWS ECS 배포 가이드 (VPC, RDS, Lambda, ECR)
- **[Environment Variables](./environment-variables.md)**: 환경변수 레퍼런스 및 설정 가이드
- **[Troubleshooting](./troubleshooting.md)**: 자주 발생하는 문제 해결

### 📝 예정된 가이드

- **Contributing**: 기여 가이드라인
- **Code Style**: 코딩 컨벤션 및 베스트 프랙티스
- **Testing**: 테스트 작성 가이드 (현재 [system-tests/README.md](../../system-tests/README.md) 참고)
- **API Testing**: Postman/Swagger 활용 (docker-compose.demo로 충분)
- **Database Migration**: 스키마 변경 가이드

## 관련 문서

### 프로젝트 개요
- [프로젝트 구조](../../README.md#프로젝트-구조)
- [개발 환경 설정](../../README.md#개발-환경-설정)
- [CLAUDE.md](../../CLAUDE.md) - 프로젝트 전체 개요

### 백엔드 개발
- [Backend 환경 설정](../../app/backend/CLAUDE.md) - 프로파일, 환경변수, DDD 구조
- [서비스별 README](../../app/backend/) - User/Course/Schedule Service 상세
- [시스템 테스트 가이드](../../system-tests/README.md)

### 설계 문서
- [시스템 아키텍처](../design/system-architecture.md)
- [기능별 상세 설계](../features/)
- [요구사항 명세](../requirements/product-spec.md)
