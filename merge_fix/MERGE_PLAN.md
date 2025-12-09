# Main ↔ Terraform 브랜치 Merge 계획

> 작성일: 2025-12-09
> 전략: **Terraform 인프라 존중 + Main API 동작 유지**

---

## 1. 브랜치 현황

| 브랜치 | 기준 커밋 | 특징 |
|--------|----------|------|
| `main` | `0af4bab` | 기능 구현 완성 (Enrollment API, Batch 처리, Group/Todo 확장) |
| `terraform_merge_fix` | `6cda63d` | AWS 인프라 배포 (IAM Role, Secrets Manager, 환경변수 표준화) |
| 공통 조상 | `fe2035d` | canvas sync lambda 성능문제 개선 |

---

## 2. 프론트엔드 영향 분석

### 결론: **프론트엔드 수정 불필요**

외부 API(프론트엔드가 호출하는 엔드포인트)는 **main 브랜치에서 추가된 것들**입니다.
terraform 브랜치에서 main을 merge하면 API가 **추가**되는 것이지, 기존 API가 **변경**되는 것이 아닙니다.

### Main에서 추가된 API (terraform에 없음)

| 서비스 | 엔드포인트 | 용도 |
|--------|-----------|------|
| course-service | `GET /api/v1/enrollments` | 수강 목록 조회 |
| course-service | `PATCH /api/v1/enrollments/{id}/toggle` | 동기화 활성화/비활성화 |
| course-service | `GET /internal/v1/enrollments/enabled` | Lambda용 활성 과목 조회 |
| user-service | `GET /internal/v1/users/by-cognito-sub/{sub}` | 내부 사용자 조회 |

### 기존 API 변경사항

| API | 변경 내용 | 프론트 영향 |
|-----|----------|-----------|
| `GET /api/v1/schedules/{id}` | 응답에 `todos`, `subtasks` 포함 | ❌ 없음 (필드 추가) |
| `GET /api/v1/todos` | 응답에 `deadline` 필드 추가 | ❌ 없음 (필드 추가) |
| `GET /api/v1/categories` | 그룹 카테고리 조회 지원 | ❌ 없음 (기능 확장) |

**결론**: 응답 필드 추가는 하위 호환성 유지. 프론트엔드 수정 불필요.

---

## 3. 내부 로직 차이 (SQS 메시지 처리)

### 3.1 Lambda → CourseService

| 항목 | Main | Terraform |
|------|------|-----------|
| 메시지 단위 | 전체 Courses 배열 | Course별 개별 |
| eventType | `CANVAS_SYNC_COMPLETED` | `CANVAS_COURSE_SYNCED` |
| syncMode | `"courses"` / `"assignments"` | 제거됨 |
| enabled 필터 | Course-Service API 호출 | 없음 (전체 동기화) |

### 3.2 CourseService → ScheduleService

| 항목 | Main | Terraform |
|------|------|-----------|
| 메시지 단위 | 사용자별 Batch | Assignment별 개별 |
| DTO | `UserAssignmentsBatchEvent` | `AssignmentToScheduleMessage` |
| eventType | `USER_ASSIGNMENTS_CREATED` | `ASSIGNMENT_CREATED` |
| 비활성 과목 처리 | 배치에 없으면 삭제 | 불가 |

### 3.3 권장: Hybrid 방식

```
Lambda (terraform 방식)
  │ Course별 개별 메시지 전송
  │ → SQS 256KB 제한 해결
  ▼
CourseService
  │ Course별 수신
  │ → 사용자별 Batch 재구성 (main 로직)
  ▼
ScheduleService (main 방식)
  │ Batch 처리
  │ → 성능 + 비활성 과목 처리
  ▼
```

---

## 4. 충돌 파일 및 해결 방향

### 4.1 충돌 파일 목록 (11개)

```
app/backend/api-gateway/.../GatewayRoutesConfig.java
app/backend/course-service/.../SqsPublisherConfig.java
app/backend/course-service/.../CanvasSyncMessage.java
app/backend/course-service/.../CanvasSyncListener.java
app/backend/course-service/.../AssignmentEventPublisher.java
app/backend/course-service/.../application.yml
app/backend/course-service/.../application-prod.yml
app/backend/schedule-service/.../SqsConsumerConfig.java
app/backend/schedule-service/.../AssignmentEventListener.java
app/backend/schedule-service/.../application.yml
app/serverless/canvas-sync-lambda/src/handler.py
```

### 4.2 해결 방향

| 파일 | Terraform 채택 | Main 채택 | 통합 |
|------|---------------|----------|------|
| SqsPublisherConfig.java | ✅ IAM Role | | |
| SqsConsumerConfig.java | ✅ IAM Role | | |
| application.yml (all) | ✅ 환경변수 | | |
| application-prod.yml | | ✅ ddl-auto:validate | |
| GatewayRoutesConfig.java | | ✅ enrollments 라우트 | |
| CanvasSyncMessage.java | | | ✅ course + courses + syncMode |
| CanvasSyncListener.java | | | ✅ terraform 수신 + main Batch |
| AssignmentEventPublisher.java | | | ✅ terraform URL + main Batch |
| AssignmentEventListener.java | | | ✅ terraform URL + main Batch |
| handler.py | | | ✅ terraform 인프라 + main enabled 필터 |

---

## 5. Terraform 브랜치에서의 최소 수정 사항

### 5.1 추가해야 할 파일 (Main에서 가져오기)

```
# Enrollment 기능 (API + 내부 로직)
course-service/enrollment/controller/EnrollmentController.java
course-service/enrollment/controller/EnrollmentInternalController.java
course-service/enrollment/dto/*.java (4개)
course-service/enrollment/service/EnrollmentService.java
course-service/enrollment/service/EnrollmentQueryService.java
course-service/enrollment/publisher/CourseEventPublisher.java
course-service/enrollment/exception/EnrollmentNotFoundException.java
course-service/course/listener/CourseEnrollmentListener.java
course-service/common/repository/AssignmentProjection.java

# Batch 처리 DTO
course-service/assignment/dto/UserAssignmentsBatchEvent.java
schedule-service/assignment/dto/UserAssignmentsBatchMessage.java

# Course 비활성화 처리
schedule-service/course/dto/CourseDisabledMessage.java
schedule-service/course/listener/CourseEventListener.java
schedule-service/course/service/CourseService.java

# 기타 기능
schedule-service/categories/model/CategorySourceType.java
schedule-service/todos/dto/TodoWithSubtasksResponse.java
user-service/user/controller/InternalUserController.java

# 테스트 파일들 (전부)
```

### 5.2 수정해야 할 파일

#### (1) CanvasSyncMessage.java - 메시지 스키마 통합

```java
// Terraform 버전에 추가
@JsonProperty("syncMode")
private String syncMode;  // Main의 필드 추가

@JsonProperty("courses")
private List<CourseData> courses;  // Main의 배열 형식도 지원
```

#### (2) CanvasSyncListener.java - Batch 로직 추가

```java
// Terraform의 Course별 수신 구조 유지
// + Main의 publishUserAssignmentBatches() 로직 추가
// + Main의 비활성 과목 처리 로직 추가
```

#### (3) AssignmentEventPublisher.java - Batch 발행 추가

```java
// Terraform의 URL 처리 로직 유지 (전체 URL 지원)
// + Main의 publishAssignmentBatchEvents() 메서드 추가
```

#### (4) AssignmentEventListener.java - Batch 처리 추가

```java
// Terraform의 URL 처리 로직 유지
// + Main의 UserAssignmentsBatchMessage 지원 추가
// + Main의 processAssignmentsBatch() 로직 추가
```

#### (5) AssignmentService.java (schedule) - Batch 처리 추가

```java
// Terraform의 개별 처리 유지 (processAssignmentEvent)
// + Main의 Batch 처리 추가 (processAssignmentsBatch)
```

#### (6) handler.py - enabled 필터 복원

```python
# Terraform의 Secrets Manager, Course별 전송 유지
# + Main의 fetch_enabled_enrollments() 복원
# + Main의 enabled_canvas_ids 필터링 복원
```

#### (7) GatewayRoutesConfig.java - enrollments 라우트 추가

```java
// Terraform에서 제거된 라우트 복원
.path(
    "/api/v1/courses/**",
    "/api/v1/assignments/**",
    "/api/v1/enrollments/**",  // ← 추가
    "/api/v1/tasks/**",
    "/api/v1/notices/**"
)
```

### 5.3 환경변수 (Terraform 유지, 수정 불필요)

Terraform의 환경변수 체계를 그대로 사용:

| 환경변수 | 값 | 비고 |
|---------|---|------|
| `SPRING_DATASOURCE_URL` | RDS URL | Spring Boot 표준 |
| `SPRING_DATASOURCE_USERNAME` | admin | Spring Boot 표준 |
| `SPRING_DATASOURCE_PASSWORD` | Secrets Manager | Spring Boot 표준 |
| `AWS_SQS_ENDPOINT` | https://sqs... | 전체 URL |
| `SQS_ASSIGNMENT_TO_SCHEDULE_QUEUE` | 전체 URL | Terraform 주입 |

### 5.4 SQS 큐 (Terraform 유지)

| 큐 | 이름 | 용도 |
|----|------|------|
| Lambda → Course | `lambda-to-courseservice-sync` | Canvas 동기화 |
| Course → Schedule | `unisync-courseservice-to-scheduleservice-assignments` | 일정 변환 |
| DLQ | `unisync-dlq-queue` | 실패 메시지 |

**추가 필요**: Course 비활성화 이벤트용 큐 (`course-to-schedule`)
- Main의 `CourseEventPublisher`가 사용
- Terraform SQS 모듈에 추가 필요

---

## 6. 추가 인프라 수정 (terraform/modules/sqs/main.tf)

```hcl
# Course 비활성화 이벤트 큐 추가
resource "aws_sqs_queue" "course_to_schedule" {
  name                      = "${var.project_name}-course-to-schedule"
  message_retention_seconds = 345600
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })
}
```

그리고 ECS 환경변수에 추가:
```hcl
{ name = "SQS_COURSE_TO_SCHEDULE_QUEUE", value = module.sqs.course_to_schedule_queue_url }
```

---

## 7. Merge 실행 순서

```bash
# 1. Terraform 브랜치에서 작업
git checkout terraform_merge_fix

# 2. Main merge (충돌 발생)
git merge main

# 3. 충돌 해결 (위 가이드 참고)
# - 환경변수/인증: Terraform 유지
# - API/기능: Main 채택
# - 메시지 처리: Hybrid 통합

# 4. SQS 모듈 수정 (course-to-schedule 큐 추가)
vi terraform/modules/sqs/main.tf

# 5. 테스트
./gradlew clean test
docker-compose -f docker-compose.acceptance.yml up -d
poetry run pytest system-tests/

# 6. Terraform apply
cd terraform
terraform plan
terraform apply

# 7. Main에 반영
git checkout main
git merge terraform_merge_fix
```

---

## 8. 체크리스트

### Merge 전
- [ ] Main의 모든 테스트 통과 확인
- [ ] Terraform의 모든 테스트 통과 확인

### Merge 중 (충돌 해결)
- [ ] CanvasSyncMessage.java - course/courses/syncMode 모두 지원
- [ ] CanvasSyncListener.java - Batch 로직 통합
- [ ] AssignmentEventPublisher.java - Batch 발행 추가
- [ ] AssignmentEventListener.java - Batch 처리 추가
- [ ] AssignmentService.java - Batch 처리 추가
- [ ] handler.py - enabled 필터 복원
- [ ] GatewayRoutesConfig.java - enrollments 라우트 추가

### Merge 후 (로컬 테스트)
- [ ] `./gradlew clean test` 통과
- [ ] docker-compose 서비스 정상 시작
- [ ] Canvas 동기화 → Schedule 생성
- [ ] Enrollment 비활성화 → Schedule 삭제

### Terraform Apply 후
- [ ] ECS 서비스 헬스체크 통과
- [ ] Lambda 테스트 호출 성공
- [ ] End-to-end 동기화 테스트

---

## 9. 요약

| 항목 | 결정 |
|------|------|
| 환경변수 | Terraform (SPRING_DATASOURCE_*) |
| AWS 인증 | Terraform (IAM Role) |
| SQS URL | Terraform (전체 URL) |
| API 엔드포인트 | Main (추가) |
| Batch 처리 | Main (유지) |
| 메시지 전송 | Terraform (Course별) |
| 프론트 수정 | 불필요 |

**최소 작업량**: 충돌 파일 11개 해결 + SQS 큐 1개 추가 + 테스트
