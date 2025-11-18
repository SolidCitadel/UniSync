# Architecture Decision Records (ADR)

이 디렉토리는 프로젝트의 중요한 아키텍처 결정사항을 기록합니다.

## ADR이란?

Architecture Decision Record는 소프트웨어 개발 과정에서 내린 중요한 아키텍처 결정과 그 배경을 문서화하는 방법입니다.

## ADR 작성 가이드

각 ADR은 다음 형식을 따릅니다:

```markdown
# ADR-{번호}: {제목}

**날짜**: YYYY-MM-DD
**상태**: [제안됨 | 승인됨 | 폐기됨 | 대체됨]
**결정자**: {이름 또는 팀}

## 컨텍스트

어떤 문제 또는 상황이 이 결정을 필요로 했는가?

## 결정

우리는 무엇을 결정했는가?

## 근거

왜 이 결정을 내렸는가? 고려한 대안은 무엇이었는가?

## 결과

이 결정의 긍정적/부정적 영향은 무엇인가?

## 참고자료

- 관련 문서, 이슈, PR 링크
```

## ADR 목록

### 핵심 아키텍처 결정

1. **Canvas API 토큰 방식 채택** (OAuth2 미사용)
   - 사용자가 Canvas에서 API 토큰을 직접 발급
   - AES-256 암호화 저장
   - 근거: Canvas LMS의 OAuth2 제약사항 (학생 권한 부족)

2. **마이크로서비스 아키텍처**
   - User-Service, Course-Service, Schedule-Service 분리
   - 서비스별 독립 DB
   - SQS 기반 비동기 통신

3. **Leader 기반 Canvas 동기화**
   - 과목당 1명의 Leader만 Canvas API 호출
   - API 비용 절감 및 중복 호출 방지
   - 데이터는 모든 수강생에게 공유

4. **단계별 구현 전략**
   - Phase 1: 수동 API 호출
   - Phase 2: EventBridge 자동화
   - Phase 3: LLM 기반 자동화

---

**Note**: 위 결정들은 현재 [system-architecture.md](../design/system-architecture.md)와 [CLAUDE.md](../../CLAUDE.md)에 분산되어 있습니다. 향후 각각을 독립 ADR 문서로 분리할 수 있습니다.
