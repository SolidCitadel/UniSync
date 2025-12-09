# ECS 배포 이슈 및 해결 방법

## 🔴 발견된 문제점

### 1. 서비스 간 통신 문제 (Critical)

**에러 로그:**
```
io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: localhost/127.0.0.1:8081
```

**원인:**
- API Gateway가 `http://localhost:8081`로 user-service에 연결하려고 시도
- ECS Fargate에서는 각 서비스가 **별도 태스크(컨테이너)**로 실행됨
- `localhost`로는 다른 서비스에 접근할 수 없음

**현재 잘못된 설정** (`terraform/main.tf`):
```hcl
# API Gateway 환경변수 (잘못됨)
{ name = "USER_SERVICE_URL", value = "http://localhost:8081" }
{ name = "COURSE_SERVICE_URL", value = "http://localhost:8082" }
{ name = "SCHEDULE_SERVICE_URL", value = "http://localhost:8083" }
```

**해결 방법:**
ECS Fargate에서 서비스 간 통신을 위해 다음 중 하나를 사용해야 합니다:

#### Option A: AWS Cloud Map (Service Discovery) - 권장
```hcl
# 예시: user-service.unisync.local
{ name = "USER_SERVICE_URL", value = "http://user-service.unisync.local:8081" }
```

#### Option B: ALB 내부 통신
```hcl
# ALB DNS를 통한 내부 통신
{ name = "USER_SERVICE_URL", value = "http://internal-alb.ap-northeast-2.elb.amazonaws.com/api/users" }
```

#### Option C: 모든 서비스를 하나의 태스크에 배포
- 같은 태스크 내에서는 localhost로 통신 가능
- 하지만 개별 스케일링이 불가능해짐

---

### 2. RDS 데이터베이스 미생성 (Critical)

**원인:**
- RDS 인스턴스는 생성되었지만, 개별 데이터베이스(`user_db`, `course_db`, `schedule_db`)는 수동으로 생성해야 함

**현재 설정** (`terraform/main.tf`):
```hcl
{ name = "SPRING_DATASOURCE_URL", value = "jdbc:mysql://.../user_db" }   # user_db 없음
{ name = "SPRING_DATASOURCE_URL", value = "jdbc:mysql://.../course_db" } # course_db 없음
{ name = "SPRING_DATASOURCE_URL", value = "jdbc:mysql://.../schedule_db" } # schedule_db 없음
```

**해결 방법:**
RDS에 접속하여 데이터베이스 생성:
```sql
CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS course_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS schedule_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 🛠️ 해결 단계

### Step 1: Service Discovery 설정 (Terraform 수정)

1. Cloud Map 네임스페이스 생성
2. 각 서비스에 서비스 디스커버리 등록
3. API Gateway 환경변수 수정

### Step 2: RDS 데이터베이스 생성

1. RDS 엔드포인트 확인
   ```
   unisync-mysql.c9cmcw6wa6kz.ap-northeast-2.rds.amazonaws.com
   ```

2. Bastion Host 또는 VPN을 통해 RDS 접속

3. 데이터베이스 생성 SQL 실행

### Step 3: ECS 서비스 재배포

```bash
# 모든 서비스 강제 재배포
aws ecs update-service --cluster unisync-cluster --service unisync-api-gateway --force-new-deployment
aws ecs update-service --cluster unisync-cluster --service unisync-user-service --force-new-deployment
aws ecs update-service --cluster unisync-cluster --service unisync-course-service --force-new-deployment
aws ecs update-service --cluster unisync-cluster --service unisync-schedule-service --force-new-deployment
```

---

## 📊 현재 상태 요약

| 구성 요소 | 상태 | 문제 |
|----------|------|-----|
| VPC/Network | ✅ | - |
| RDS 인스턴스 | ✅ | DB가 생성되지 않음 |
| ECR | ✅ | - |
| ECS Cluster | ✅ | - |
| API Gateway | ⚠️ | localhost로 다른 서비스에 연결 시도 |
| User Service | ❌ | DB 연결 실패 + API Gateway 연결 안됨 |
| Course Service | ❌ | DB 연결 실패 |
| Schedule Service | ❌ | DB 연결 실패 |
| ALB | ✅ | - |

---

## 📝 To-Do List

- [ ] Service Discovery (Cloud Map) 모듈 추가
- [ ] API Gateway 서비스 URL 환경변수 수정
- [ ] RDS에 데이터베이스 생성 (user_db, course_db, schedule_db)
- [ ] ECS 태스크 정의 업데이트
- [ ] 서비스 재배포 및 테스트
