---
description: 기능 변경 후 영향받는 테스트/문서 업데이트
---

기능 변경이 완료되었습니다. 영향받는 테스트를 재검증하고 문서를 최신으로 유지해야 합니다.

## 체크리스트 (TodoWrite로 생성)

### 1단계: 영향받는 테스트 파악 및 실행

**변경 범위 분석**:
- 어떤 코드가 변경되었는지 확인 (Controller, Service, Entity, SQS Consumer 등)
- 영향받는 도메인 파악 (user, course, schedule, auth, credentials 등)

**Unit Tests (각 서비스 내부)**:
```bash
# 변경된 서비스의 Unit Tests 실행
cd app/backend/{service}
./gradlew test

# 예: User-Service 변경 시
cd app/backend/user-service && ./gradlew test

# 예: Course-Service 변경 시
cd app/backend/course-service && ./gradlew test

# 예: Schedule-Service 변경 시
cd app/backend/schedule-service && ./gradlew test
```

**System Tests (통합 환경)**:
```bash
# 전제 조건: docker-compose.acceptance.yml 실행 중
docker-compose -f docker-compose.acceptance.yml up -d

# 전체 System Tests
poetry run pytest system-tests/ -v

# 변경 영향 범위별 실행
# 1. API 변경 → Component Tests
poetry run pytest system-tests/component/{service_name}/ -v

# 2. SQS 메시지 변경 → Integration Tests
poetry run pytest system-tests/integration/{producer}_to_{consumer}/ -v

# 3. E2E 플로우 영향 → Scenario Tests
poetry run pytest system-tests/scenarios/ -v
```

**실패한 테스트 처리**:
- ✅ 테스트 코드 수정 (API 변경 반영)
- ✅ 테스트 제거 (더 이상 유효하지 않은 경우)
- ✅ 새 테스트 추가 (새 기능에 대한 테스트)

### 2단계: 문서 업데이트

**변경 내용에 따라 해당 문서 업데이트**:

#### API 변경 시
- **`docs/design/system-architecture.md`**
  - REST API 엔드포인트 추가/변경/삭제
  - 요청/응답 스키마 변경
  - 인증/인가 규칙 변경

#### 환경변수 추가 시
- **`app/backend/CLAUDE.md`**
  - 새 환경변수 목록에 추가
  - 역할, 형식 문서화
- **`.env.local.example`**
  - 템플릿에 새 환경변수 추가
  - 설명 주석 추가

#### SQS 메시지 변경 시
- **`docs/design/sqs-architecture.md`**
  - 메시지 스키마 변경 (필드 추가/삭제/타입 변경)
  - 새 큐 추가
  - 재시도 전략 변경

#### 새 기능 추가 시
- **`docs/features/{feature-name}.md`**
  - 새 기능 문서 생성 (없는 경우)
  - 기능 설명, 사용법, API 엔드포인트

#### 테스트 개수 변경 시
- **`docs/design/testing-strategy.md`**
  - "테스트 커버리지 현황" 섹션 업데이트
  - Unit Tests 개수 (총 156개 → ?)
  - System Tests 개수 (총 86개 → ?)
  - Component/Integration/Scenario Tests 상세 현황

#### DB 스키마 변경 시
- **`docs/design/system-architecture.md`**
  - 데이터 모델 섹션 업데이트
  - Entity 관계도 업데이트 (필요 시)

### 3단계: 변경사항 확인

```bash
# 문서 변경사항 확인
git diff docs/

# 테스트 실행 결과 확인
poetry run pytest system-tests/ -v

# 모든 서비스 Unit Tests 확인
./gradlew test
```

## 실행 순서 (TodoWrite로 작성)

1. **변경 범위 분석**
   - 어떤 코드가 변경되었는지 파악
   - 영향받는 도메인/서비스 확인

2. **Unit Tests 실행**
   - 변경된 서비스의 `./gradlew test` 실행
   - 실패한 테스트 수정/제거

3. **System Tests 실행**
   - Component Tests 실행
   - Integration Tests 실행 (SQS 변경 시)
   - Scenario Tests 실행 (E2E 플로우 영향 시)

4. **문서 업데이트**
   - system-architecture.md (API/DB 변경)
   - sqs-architecture.md (SQS 메시지 변경)
   - testing-strategy.md (테스트 개수 변경)
   - app/backend/CLAUDE.md (환경변수 추가)
   - docs/features/ (새 기능 추가)

5. **검증**
   - 모든 테스트 PASS 확인
   - 문서 변경사항 리뷰
   - 커밋 전 최종 확인

## 예시: API 변경 후 업데이트

**변경**: Schedule-Service에 새 필드 `priority` 추가

**1. Unit Tests**:
```bash
cd app/backend/schedule-service
./gradlew test
# 실패한 테스트 수정 (ScheduleServiceTest, ScheduleDtoTest)
```

**2. System Tests**:
```bash
poetry run pytest system-tests/component/schedule_service/ -v
# 실패한 테스트 수정 (test_schedule_crud.py)
```

**3. 문서 업데이트**:
- `docs/design/system-architecture.md`: Schedule API 스키마에 `priority` 필드 추가
- `docs/design/testing-strategy.md`: 테스트 개수 업데이트 (실패 테스트 수정 개수 반영)

**4. 검증**:
```bash
# 모든 테스트 재실행
poetry run pytest system-tests/ -v
./gradlew test

# 문서 확인
git diff docs/
```

## 완료 후 보고

사용자에게 다음 정보 제공:
- ✅ 실행한 테스트 및 결과 (PASS/FAIL 개수)
- ✅ 업데이트한 문서 목록
- ✅ 남아있는 작업 (실패한 테스트 수정 등)