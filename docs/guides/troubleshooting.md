# Troubleshooting Guide

자주 발생하는 문제와 해결 방법을 정리한 가이드입니다.

---

## 🔴 LocalStack 관련 문제

### 문제 1: Cognito User Pool ID가 계속 변경됨

**증상**:
```
Error: User pool ap-northeast-2_abc123 does not exist
```

**원인**: LocalStack 재시작 시 Cognito User Pool이 새로 생성되어 ID가 변경됨

**식별**:
```bash
# LocalStack 로그 확인
docker-compose logs localstack | grep "Cognito User Pool"

# 출력 예시:
# Cognito User Pool created: ap-northeast-2_xyz789
```

**해결**:
```bash
# 1. 새로 생성된 Cognito ID 확인
cat .env | grep COGNITO

# 2. .env.local 파일 업데이트
COGNITO_USER_POOL_ID=ap-northeast-2_xyz789  # 새 ID로 변경
COGNITO_CLIENT_ID=xxxxx  # 새 Client ID로 변경

# 3. 서비스 재시작
./gradlew bootRun
```

**예방**: LocalStack Persistence 활성화 (이미 설정됨)

---

### 문제 2: LocalStack 포트 충돌

**증상**:
```
Error starting userland proxy: listen tcp 0.0.0.0:4566: bind: address already in use
```

**식별**:
```bash
# 4566 포트 사용 중인 프로세스 확인
# Linux/Mac
lsof -i :4566

# Windows
netstat -ano | findstr :4566
```

**해결**:
```bash
# 기존 LocalStack 컨테이너 중지
docker stop $(docker ps -q --filter "name=localstack")

# 또는 프로세스 강제 종료 후 재시작
docker-compose restart localstack
```

---

### 문제 3: SQS 큐가 생성되지 않음

**증상**:
```
QueueDoesNotExist: The specified queue does not exist
```

**식별**:
```bash
# SQS 큐 목록 확인
aws --endpoint-url=http://localhost:4566 sqs list-queues

# 출력: (빈 목록 또는 일부 큐만 존재)
```

**해결**:
```bash
# LocalStack 초기화 스크립트 수동 실행
docker exec -it unisync-localstack bash
cd /etc/localstack/init/ready.d
./01-create-queues.sh

# 큐 생성 확인
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

---

## 🗄️ MySQL 관련 문제

### 문제 4: MySQL 연결 실패

**증상**:
```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago
```

**식별**:
```bash
# MySQL 컨테이너 상태 확인
docker-compose ps mysql

# MySQL 로그 확인
docker-compose logs mysql | tail -n 50
```

**해결**:
```bash
# 1. MySQL 헬스체크 대기
docker-compose logs mysql | grep "ready for connections"

# 2. 포트 확인
docker-compose ps
# mysql의 PORTS가 0.0.0.0:3306->3306/tcp인지 확인

# 3. 연결 테스트
docker exec -it unisync-mysql mysql -uunisync -punisync_password -e "SELECT 1"
```

**흔한 원인**:
- MySQL 컨테이너가 완전히 시작되지 않음 (30초 대기 권장)
- 비밀번호 불일치 (`.env.local`의 `MYSQL_PASSWORD` 확인)

---

### 문제 5: 데이터베이스가 존재하지 않음

**증상**:
```
Unknown database 'user_db'
```

**식별**:
```bash
# MySQL 접속하여 DB 목록 확인
docker exec -it unisync-mysql mysql -uroot -proot_password -e "SHOW DATABASES"
```

**해결**:
```bash
# 데이터베이스 수동 생성
docker exec -it unisync-mysql mysql -uroot -proot_password <<EOF
CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS course_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS schedule_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON *.* TO 'unisync'@'%';
FLUSH PRIVILEGES;
EOF
```

**참고**: `mysql-init/01-create-databases.sql`이 실행되지 않았을 가능성

---

## 🔐 인증 관련 문제

### 문제 6: JWT 인증 실패

**증상**:
```
401 Unauthorized
Invalid JWT token
```

**식별**:
```bash
# API Gateway 로그 확인
docker-compose -f docker-compose.acceptance.yml logs api-gateway | grep "JWT"

# JWT 디코딩 (https://jwt.io 사용)
# Authorization 헤더의 토큰을 복사하여 확인
```

**해결**:
```bash
# 1. 새 JWT 토큰 발급
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPassword123!"}'

# 2. 응답에서 accessToken 복사 후 사용
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <NEW_TOKEN>"
```

**흔한 원인**:
- 토큰 만료 (Cognito 기본 1시간)
- Cognito User Pool ID 불일치
- `Bearer` 접두사 누락

---

### 문제 7: ENCRYPTION_KEY 누락

**증상**:
```
IllegalArgumentException: Encryption key must be 32 bytes
```

**식별**:
```bash
# 환경변수 로드 테스트
cd app/backend/user-service
./gradlew test --tests EnvironmentVariablesTest
```

**해결**:
```bash
# 1. ENCRYPTION_KEY 생성
openssl rand -base64 32

# 2. .env.local에 추가
echo "ENCRYPTION_KEY=생성된_키" >> .env.local

# 3. 서비스 재시작
./gradlew bootRun
```

---

## 🎯 Canvas API 관련 문제

### 문제 8: Canvas API 토큰 무효

**증상**:
```
401 Unauthorized
Invalid access token
```

**식별**:
```bash
# Canvas API 토큰 직접 테스트
curl https://khcanvas.khu.ac.kr/api/v1/users/self \
  -H "Authorization: Bearer YOUR_TOKEN"

# 401 응답 시 토큰 무효
```

**해결**:
1. Canvas LMS에서 새 토큰 발급:
   - **Account** → **Settings** → **Approved Integrations**
   - **+ New Access Token**
2. `.env.local` 업데이트:
   ```bash
   CANVAS_API_TOKEN=새_토큰
   ```
3. 서비스 재시작

---

### 문제 9: Canvas API Rate Limit

**증상**:
```
403 Forbidden
Rate limit exceeded
```

**해결**:
- Canvas API는 **초당 10 요청** 제한
- Lambda 함수에 Rate Limiting 로직 추가 (현재 구현됨)
- 과도한 테스트 자제

---

## 📨 SQS 메시지 처리 문제

### 문제 10: SQS 메시지가 처리되지 않음

**증상**: Assignment 생성해도 DB에 반영 안 됨

**식별**:
```bash
# SQS 큐 메시지 수 확인
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/assignment-events-queue \
  --attribute-names ApproximateNumberOfMessages

# 출력: ApproximateNumberOfMessages > 0 이면 처리 안 됨
```

**해결**:
```bash
# 1. Course-Service SQS 리스너 로그 확인
docker-compose logs course-service | grep "SQS"

# 2. DLQ 확인 (처리 실패 메시지)
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/assignment-events-queue-dlq

# 3. 메시지 포맷 확인
# DLQ의 메시지 body를 확인하여 DTO와 일치하는지 검증
```

**흔한 원인**:
- SQS 리스너가 시작되지 않음 (Spring 설정 확인)
- 메시지 DTO 필드 불일치 (`canvasCourseId` 누락 등)
- 역직렬화 실패 (JSON 형식 오류)

---

## 🐳 Docker Compose 관련 문제

### 문제 11: 서비스 간 통신 실패

**증상**:
```
Connection refused
Could not connect to http://user-service:8081
```

**식별**:
```bash
# 네트워크 확인
docker network ls
docker network inspect unisync_default

# 서비스 이름 확인
docker-compose ps
```

**해결**:
```bash
# 1. 같은 네트워크에 있는지 확인
docker inspect course-service | grep NetworkMode

# 2. 서비스 이름으로 ping
docker exec course-service ping user-service

# 3. 환경변수 확인
docker exec course-service env | grep USER_SERVICE_URL
# 출력: USER_SERVICE_URL=http://user-service:8081
```

---

### 문제 12: 컨테이너가 계속 재시작됨

**증상**:
```bash
docker-compose ps
# STATE: Restarting
```

**식별**:
```bash
# 컨테이너 로그 확인
docker-compose logs <service-name> | tail -n 100

# Exit Code 확인
docker inspect <container-id> --format='{{.State.ExitCode}}'
```

**흔한 원인**:
- 환경변수 누락 (필수 값 미설정)
- DB 연결 실패 (MySQL이 준비되지 않음)
- OOMKilled (메모리 부족)

**해결**:
```bash
# 1. 로그에서 오류 확인
docker-compose logs <service-name>

# 2. 환경변수 점검
docker-compose config

# 3. 의존성 순서 확인 (depends_on 설정)
```

---

## 🧪 테스트 관련 문제

### 문제 13: E2E 테스트 실패

**증상**:
```
AssertionError: Expected 10 courses, got 0
```

**식별**:
```bash
# 테스트 로그 확인
python tests/e2e/test_canvas_sync_with_jwt_e2e.py -v

# Lambda 로그 확인
docker-compose logs localstack | grep "canvas-sync-lambda"
```

**해결**:
```bash
# 1. Canvas 토큰 유효성 확인
curl https://khcanvas.khu.ac.kr/api/v1/users/self \
  -H "Authorization: Bearer $CANVAS_API_TOKEN"

# 2. SQS 메시지 전송 확인
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/assignment-events-queue

# 3. DB 데이터 확인
docker exec -it unisync-mysql mysql -uunisync -punisync_password -D course_db \
  -e "SELECT COUNT(*) FROM courses;"
```

---

## 🆘 문제 해결 체크리스트

### 서비스 시작 실패 시
1. ✅ `.env.local` 파일이 존재하는가?
2. ✅ 필수 환경변수가 모두 설정되었는가? (`ENCRYPTION_KEY`, `COGNITO_*`)
3. ✅ LocalStack과 MySQL이 실행 중인가? (`docker-compose ps`)
4. ✅ LocalStack 초기화가 완료되었는가? (`docker-compose logs localstack`)
5. ✅ 포트 충돌이 없는가? (3306, 4566, 8080-8083)

### 인증 실패 시
1. ✅ JWT 토큰이 만료되지 않았는가?
2. ✅ `COGNITO_USER_POOL_ID`가 올바른가?
3. ✅ `Authorization: Bearer` 형식이 맞는가?

### 데이터 동기화 실패 시
1. ✅ Canvas API 토큰이 유효한가?
2. ✅ SQS 큐가 생성되었는가?
3. ✅ SQS 메시지가 DLQ로 이동했는가?
4. ✅ 서비스 로그에 에러가 있는가?

---

## 📚 추가 도움

- [환경변수 가이드](./environment-variables.md) - 환경변수 상세 설정
- [Backend 개발 가이드](../../app/backend/CLAUDE.md) - 프로파일 및 환경 설정
- [Serverless 테스트](../../app/serverless/TESTING.md) - Lambda 테스트 및 디버깅
