# UniSync - Canvas LMS 연동 학업 일정관리 서비스

Canvas LMS와 연동하여 자동으로 학업 일정을 동기화하고 AI로 분석하는 서비스입니다.

## 프로젝트 구조

```
UniSync/
├── app/
│   └── backend/
│       ├── user-service/       # 사용자 관리 (8081)
│       ├── course-service/     # 과목 관리 (8082)
│       ├── sync-service/       # Canvas 동기화 (8083)
│       ├── schedule-service/   # 일정 관리 (8084)
│       └── social-service/     # 소셜 기능 (8085)
├── docker-compose.yml
├── .env.example
├── localstack-init/           # LocalStack 초기화 스크립트
└── mysql-init/                # MySQL 초기화 스크립트
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

### Frontend (예정)
- React 18 + TypeScript + Vite

## 개발 환경 설정

### 1. 사전 요구사항

- **Docker & Docker Compose**
- **Java 21** (LTS)
- **Gradle 8.5 이상** (또는 Gradle Wrapper 사용)
- **Node.js 18 이상** (프론트엔드)

### 2. 환경 변수 설정

```bash
# .env.example을 .env로 복사
cp .env.example .env

# .env 파일에서 필요한 값 수정 (JWT_SECRET, OPENAI_API_KEY 등)
```

### 3. Docker 컨테이너 시작

```bash
# 모든 인프라 서비스 시작 (LocalStack, MySQL)
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 특정 서비스만 시작
docker-compose up -d localstack mysql
```

### 4. 서비스 상태 확인

```bash
# 컨테이너 상태 확인
docker-compose ps

# MySQL 접속 확인
docker exec -it unisync-mysql mysql -uroot -proot_password -e "SHOW DATABASES;"

# LocalStack 확인
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

### 5. Spring Boot 서비스 실행

각 서비스를 별도 터미널에서 실행:

```bash
# User Service
cd app/backend/user-service
./gradlew bootRun

# Course Service
cd app/backend/course-service
./gradlew bootRun

# Sync Service
cd app/backend/sync-service
./gradlew bootRun

# Schedule Service
cd app/backend/schedule-service
./gradlew bootRun

# Social Service
cd app/backend/social-service
./gradlew bootRun
```

## 서비스 엔드포인트

| 서비스 | 포트 | Swagger UI |
|--------|------|------------|
| User Service | 8081 | http://localhost:8081/swagger-ui.html |
| Course Service | 8082 | http://localhost:8082/swagger-ui.html |
| Sync Service | 8083 | http://localhost:8083/swagger-ui.html |
| Schedule Service | 8084 | http://localhost:8084/swagger-ui.html |
| Social Service | 8085 | http://localhost:8085/swagger-ui.html |
| MySQL | 3306 | - |
| LocalStack | 4566 | - |

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

- `user_db`: 사용자 정보
- `course_db`: 과목/수강 정보
- `sync_db`: 동기화 데이터
- `schedule_db`: 일정 데이터
- `social_db`: 소셜 데이터

```bash
# MySQL 접속
docker exec -it unisync-mysql mysql -uunisync -punisync_password

# 특정 데이터베이스 접속
docker exec -it unisync-mysql mysql -uunisync -punisync_password -D user_db
```

## 테스트

```bash
# 단위 테스트
./gradlew test

# 통합 테스트
./gradlew build

# 특정 서비스 테스트
cd app/backend/user-service
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests UserServiceTest
```

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
