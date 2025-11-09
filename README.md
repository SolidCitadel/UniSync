# UniSync - Canvas LMS 연동 학업 일정관리 서비스

Canvas LMS와 연동하여 자동으로 학업 일정을 동기화하고 AI로 분석하는 서비스입니다.

## 프로젝트 현황

- **Phase 1 완료**: ✅ 기본 인프라 및 서비스 구조
- **Phase 2 진행 중**: 🚧 Canvas 동기화 및 SQS 이벤트 처리
- **최근 구현**:
  - API Gateway (Spring Cloud Gateway + JWT 인증 + Cognito 연동)
  - Canvas Sync Lambda 및 SQS 통합
  - Course-Service의 SQS 구독 기능
  - 공유 모듈(java-common, python-common)을 통한 DTO 표준화
  - E2E 통합 테스트 환경 구축

## 프로젝트 구조

```
UniSync/
├── app/
│   ├── backend/
│   │   ├── api-gateway/        # API Gateway + JWT 인증 (8080)
│   │   ├── user-service/       # 사용자/인증/소셜/그룹 (8081)
│   │   ├── course-service/     # Canvas 학업 데이터 (8082)
│   │   └── schedule-service/   # 일정(Schedule) + 할일(Todo) (8083)
│   ├── serverless/
│   │   ├── canvas-sync-lambda/ # Canvas API 호출
│   │   ├── llm-lambda/         # LLM Task 생성/검증
│   │   └── step-functions/     # Step Functions 정의
│   └── shared/
│       ├── java-common/        # Java 공용 DTO (SQS 메시지 등)
│       ├── python-common/      # Python 공용 DTO
│       └── message-schemas/    # JSON Schema 정의
├── tests/
│   ├── integration/            # E2E 통합 테스트
│   └── fixtures/               # 테스트 데이터
├── scripts/
│   ├── infra/                  # 인프라 관리 (Lambda 배포, SQS 재생성)
│   └── run-integration-tests.sh  # E2E 통합 테스트 실행
├── localstack-init/            # LocalStack 자동 초기화 (컨테이너 시작 시)
├── mysql-init/                 # MySQL 자동 초기화 (컨테이너 시작 시)
├── docker-compose.yml          # 개발 환경 (인프라만)
├── docker-compose-app.yml      # 전체 애플리케이션 실행
├── docker-compose.test.yml     # 통합 테스트 환경
└── .env.example                # 환경 변수 템플릿
```

## 기술 스택

### Backend
- **Java 21** (LTS) + **Spring Boot 3.5.7**
- **Gradle 8.5** + Kotlin DSL
- **MySQL 8.0** + Spring Data JPA
- **AWS Cognito** + JWT
- **SpringDoc OpenAPI 3** (Swagger)

### 인프라
- **Docker** + LocalStack (로컬 AWS 환경)
- **SQS** (메시징), **Step Functions** (워크플로우), **Lambda** (서버리스)

## 개발 환경 설정

### 1. 사전 요구사항

- **Docker & Docker Compose**
- **Java 21** (LTS)
- **Gradle 8.5 이상** (또는 Gradle Wrapper 사용)

### 2. Docker 컨테이너 시작 (최초 1회)

```bash
# 모든 인프라 서비스 시작 (LocalStack, MySQL)
docker-compose up -d

# 로그 확인 (LocalStack 초기화 완료 대기)
docker-compose logs -f localstack
# "Cognito 설정 완료!" 메시지가 보일 때까지 대기
```

### 3. LocalStack 출력값 확인

LocalStack이 초기화되면 자동으로 `.localstack-outputs.yml` 파일이 생성됩니다:

```bash
# 생성된 값 확인
cat .localstack-outputs.yml
```

출력 예시:
```yaml
# LocalStack에서 자동 생성된 값들
# 이 값들을 각 서비스의 application-local.yml에 복사하세요

# AWS Cognito 설정
aws:
  region: ap-northeast-2
  cognito:
    user-pool-id: ap-northeast-2_xxxxx
    client-id: xxxxxxxxx
    region: ap-northeast-2
    endpoint: http://localhost:4566
```

### 4. 서비스별 로컬 설정 파일 생성

각 Spring Boot 서비스마다 `application-local.yml` 파일을 생성합니다:

```bash
# User Service
cd app/backend/user-service/src/main/resources
cp application-local.yml.example application-local.yml

# Course Service
cd app/backend/course-service/src/main/resources
cp application-local.yml.example application-local.yml

# Schedule Service
cd app/backend/schedule-service/src/main/resources
cp application-local.yml.example application-local.yml
```

**각 서비스의 `application-local.yml`에 `.localstack-outputs.yml`의 값 복사**:

```yaml
# application-local.yml 편집
# .localstack-outputs.yml의 cognito 설정을 복사
aws:
  cognito:
    user-pool-id: ap-northeast-2_xxxxx  # .localstack-outputs.yml에서 복사
    client-id: xxxxxxxxx                # .localstack-outputs.yml에서 복사
```

**참고**:
- `application-local.yml`은 `.gitignore`에 포함되어 커밋되지 않습니다
- 다른 LocalStack 설정(SQS endpoint 등)은 이미 올바르게 설정되어 있습니다

### 5. IDE에서 Active Profile 설정

각 서비스를 IDE에서 실행하려면 Active Profile을 `local`로 설정해야 합니다:

**IntelliJ IDEA**:
- Run/Debug Configurations → Active profiles: `local`

**VS Code**:
- `launch.json` → `"spring.profiles.active": "local"`

### 6. 서비스 상태 확인

```bash
# 컨테이너 상태 확인
docker-compose ps

# MySQL 접속 확인
docker exec -it unisync-mysql mysql -uroot -proot_password -e "SHOW DATABASES;"

# LocalStack 확인
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

### 7-A. Spring Boot 서비스 실행 (개별)

각 서비스를 별도 터미널에서 실행:

```bash
# User Service
cd app/backend/user-service
./gradlew bootRun --args='--spring.profiles.active=local'

# Course Service
cd app/backend/course-service
./gradlew bootRun --args='--spring.profiles.active=local'

# Schedule Service
cd app/backend/schedule-service
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 7-B. 전체 애플리케이션 실행 (Docker Compose)

모든 서비스를 컨테이너로 한 번에 실행:

```bash
# 전체 빌드 및 실행 (인프라 + 백엔드 서비스)
docker-compose -f docker-compose.app.yml up -d --build

# 로그 확인
docker-compose -f docker-compose.app.yml logs -f

# 특정 서비스 로그만 확인
docker-compose -f docker-compose.app.yml logs -f course-service

# 중지
docker-compose -f docker-compose.app.yml down
```

**참고**: `docker-compose-app.yml`은 각 서비스의 Dockerfile을 사용하여 컨테이너 이미지를 빌드하고 실행합니다.

## 서비스 엔드포인트

| 서비스 | 포트 | 엔드포인트/문서 |
|--------|------|------------|
| **API Gateway** | 8080 | http://localhost:8080/api/v1/* |
| User Service | 8081 | http://localhost:8081/swagger-ui.html |
| Course Service | 8082 | http://localhost:8082/swagger-ui.html |
| Schedule Service | 8083 | http://localhost:8083/swagger-ui.html |
| MySQL | 3306 | - |
| LocalStack | 4566 | - |

**참고**: 직접 서비스 포트로 테스트 가능하지만 JWT 인증이 필요합니다

## 인프라 서비스

### LocalStack (AWS 에뮬레이션)

LocalStack은 다음 AWS 서비스를 로컬에서 제공합니다:

- **SQS**: 서비스 간 비동기 메시징
- **Step Functions**: 동기화 워크플로우
- **Lambda**: LLM 분석 함수
- **S3**: 파일 저장소
- **EventBridge**: 스케줄링

```bash
# SQS 큐 목록 확인
aws --endpoint-url=http://localhost:4566 sqs list-queues

# SQS 메시지 전송 테스트
aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/assignment-events-queue \
  --message-body '{"eventType":"ASSIGNMENT_CREATED","assignmentId":"test123"}'

# S3 버킷 목록 확인
aws --endpoint-url=http://localhost:4566 s3 ls
```

### MySQL

각 마이크로서비스는 독립적인 데이터베이스를 사용합니다:

- `user_db`: 사용자/인증/소셜/그룹 (Users, Credentials, Friendships, Groups, Group_Members)
- `course_db`: Canvas 학업 데이터 (Courses, Enrollments, Assignments, Notices, Sync_Status)
- `schedule_db`: 일정 및 할일 (Schedules, Todos, Categories)

```bash
# MySQL 접속
docker exec -it unisync-mysql mysql -uunisync -punisync_password

# 특정 데이터베이스 접속
docker exec -it unisync-mysql mysql -uunisync -punisync_password -D user_db
```

## 테스트

### 단위 테스트

```bash
# 모든 서비스 단위 테스트
./gradlew test

# 특정 서비스 테스트
cd app/backend/user-service
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests UserServiceTest

# Serverless 함수 테스트 (Python)
cd app/serverless
python -m pytest canvas-sync-lambda/tests/
python -m pytest llm-lambda/tests/
```

### E2E 통합 테스트

전체 워크플로우를 테스트하는 통합 테스트:

```bash
# 자동화된 통합 테스트 실행 (권장)
./scripts/run-integration-tests.sh

# 수동 실행
docker-compose -f docker-compose.test.yml up -d
python -m pytest tests/integration/ -v
docker-compose -f docker-compose.test.yml down -v
```

**통합 테스트 시나리오**:
- Canvas API → Lambda → SQS → Course-Service → DB
- Assignment 생성/수정/중복 처리
- SQS 메시지 처리 검증

자세한 내용은 [tests/README.md](tests/README.md)를 참고하세요.

## 종료 및 정리

```bash
# 모든 컨테이너 중지
docker-compose down

# 컨테이너 및 볼륨 삭제 (데이터 초기화)
docker-compose down -v

# 특정 서비스만 재시작
docker-compose restart mysql
```

## 문제 해결

### LocalStack이 시작되지 않는 경우

```bash
# LocalStack 로그 확인
docker-compose logs localstack

# LocalStack 재시작
docker-compose restart localstack
```

### MySQL 연결 실패

```bash
# MySQL 헬스체크 확인
docker-compose ps mysql

# MySQL 로그 확인
docker-compose logs mysql

# 포트 충돌 확인 (Windows)
netstat -ano | findstr :3306
```

### SQS 큐가 생성되지 않은 경우

```bash
# 초기화 스크립트 수동 실행
docker exec -it unisync-localstack bash
cd /etc/localstack/init/ready.d
./01-create-queues.sh
```

## 참고 문서

- [기획서](./기획.md) - 문제 정의, 핵심 기능, 사용자 시나리오
- [설계서](./설계서.md) - 상세 아키텍처, API 설계, DB 스키마
- [CLAUDE.md](./CLAUDE.md) - Claude Code 작업 가이드

## 라이선스

MIT License
