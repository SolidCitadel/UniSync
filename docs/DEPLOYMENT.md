# UniSync AWS 배포 가이드

이 문서는 UniSync 프로젝트를 AWS ECS Fargate에 배포하는 전체 과정을 설명합니다.

## 📋 목차

1. [사전 요구사항](#사전-요구사항)
2. [인프라 배포 (Terraform)](#인프라-배포-terraform)
3. [Docker 이미지 빌드 & ECR 푸시](#docker-이미지-빌드--ecr-푸시)
4. [ECS 서비스 배포](#ecs-서비스-배포)
5. [배포 확인](#배포-확인)
6. [업데이트 배포](#업데이트-배포)
7. [트러블슈팅](#트러블슈팅)

---

## 사전 요구사항

### 필수 도구 설치

- **AWS CLI** (v2 이상)
  ```powershell
  aws --version
  ```

- **Terraform** (v1.0 이상)
  ```powershell
  terraform --version
  ```

- **Docker Desktop** (최신 버전)
  ```powershell
  docker --version
  ```

### AWS 자격 증명 설정

```powershell
# AWS CLI 설정
aws configure

# 입력 정보:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region: ap-northeast-2
# - Default output format: json
```

### Terraform 변수 파일 생성

```powershell
cd terraform
cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars` 파일을 편집하여 필요한 값을 설정:

```hcl
project_name = "unisync"
environment  = "prod"
aws_region   = "ap-northeast-2"

# RDS 설정
db_username = "admin"
db_name     = "unisync"

# 기타 설정...
```

---

## 인프라 배포 (Terraform)

### 1. Terraform 초기화

```powershell
cd terraform
terraform init
```

### 2. 배포 계획 확인

```powershell
terraform plan
```

### 3. 인프라 배포

```powershell
terraform apply
```

배포되는 리소스:
- ✅ VPC, Subnets, NAT Gateway
- ✅ Security Groups
- ✅ RDS (MySQL 8.0, ARM64 Graviton)
- ✅ Cognito User Pool
- ✅ ECR Repositories (4개)
- ✅ ECS Cluster (Fargate)
- ✅ Application Load Balancer (ALB)
- ✅ SQS Queues
- ✅ Lambda Functions
- ✅ EventBridge Rules
- ✅ Secrets Manager

### 4. 배포 정보 확인

```powershell
# ALB DNS 주소
terraform output alb_dns_name

# ECR Repository URLs
terraform output ecr_repository_urls

# RDS Endpoint
terraform output rds_endpoint

# Cognito User Pool ID
terraform output cognito_user_pool_id
```

---

## Docker 이미지 빌드 & ECR 푸시

### 중요: ECR 로그인 이슈 해결

PowerShell에서 ECR 로그인 시 파이프 문제가 발생할 수 있습니다. **cmd.exe를 사용하여 로그인**해야 합니다.

### 1. ECR 로그인

```powershell
# ✅ 올바른 방법 (cmd.exe 사용)
cmd /c "aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com"

# ❌ 잘못된 방법 (PowerShell 파이프 - 작동하지 않음)
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com
```

### 2. 자동 빌드 & 푸시 스크립트 사용

```powershell
# 프로젝트 루트에서 실행
.\scripts\build-and-push-images.ps1
```

이 스크립트는 다음을 자동으로 수행합니다:
1. AWS Account ID 조회
2. ECR 로그인 (cmd.exe 사용)
3. 4개 서비스 빌드 (ARM64 플랫폼)
   - api-gateway
   - user-service
   - course-service
   - schedule-service
4. ECR에 태그 & 푸시

### 3. 수동 빌드 & 푸시 (선택사항)

개별 서비스를 수동으로 빌드하려면:

```powershell
# 1. ECR 로그인
cmd /c "aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 377846699896.dkr.ecr.ap-northeast-2.amazonaws.com"

# 2. 이미지 빌드 (ARM64)
docker buildx build --platform linux/arm64 -t unisync-api-gateway -f app/backend/api-gateway/Dockerfile app/

# 3. ECR 태그
docker tag unisync-api-gateway:latest 377846699896.dkr.ecr.ap-northeast-2.amazonaws.com/unisync-api-gateway:latest

# 4. ECR 푸시
docker push 377846699896.dkr.ecr.ap-northeast-2.amazonaws.com/unisync-api-gateway:latest
```

---

## ECS 서비스 배포

### 자동 배포

Terraform이 ECS 서비스를 이미 생성했으므로, ECR에 이미지가 푸시되면 **자동으로 배포**됩니다.

### 수동 배포 (강제 재배포)

새 이미지를 배포하려면:

```powershell
# 단일 서비스 재배포
aws ecs update-service --cluster unisync-cluster --service unisync-api-gateway --force-new-deployment --region ap-northeast-2

# 모든 서비스 재배포
aws ecs update-service --cluster unisync-cluster --service unisync-api-gateway --force-new-deployment --region ap-northeast-2
aws ecs update-service --cluster unisync-cluster --service unisync-user-service --force-new-deployment --region ap-northeast-2
aws ecs update-service --cluster unisync-cluster --service unisync-course-service --force-new-deployment --region ap-northeast-2
aws ecs update-service --cluster unisync-cluster --service unisync-schedule-service --force-new-deployment --region ap-northeast-2
```

---

## 배포 확인

### 1. ECS 서비스 상태 확인

```powershell
# 서비스 목록
aws ecs list-services --cluster unisync-cluster --region ap-northeast-2

# 서비스 상세 정보
aws ecs describe-services --cluster unisync-cluster --services unisync-api-gateway --region ap-northeast-2
```

### 2. ALB를 통한 서비스 접근

```powershell
# ALB DNS 주소 확인
terraform output alb_dns_name
```

브라우저에서 접근:
- API Gateway: `http://<alb-dns>/api/`
- User Service: `http://<alb-dns>/api/users/`
- Course Service: `http://<alb-dns>/api/courses/`
- Schedule Service: `http://<alb-dns>/api/schedules/`

### 3. CloudWatch Logs 확인

```powershell
# 로그 그룹 목록
aws logs describe-log-groups --log-group-name-prefix /ecs/unisync --region ap-northeast-2

# 로그 스트림 확인
aws logs tail /ecs/unisync/api-gateway --follow --region ap-northeast-2
```

### 4. RDS 연결 확인

```powershell
# RDS 엔드포인트 확인
terraform output rds_endpoint

# Secrets Manager에서 비밀번호 조회
aws secretsmanager get-secret-value --secret-id unisync/rds-password --region ap-northeast-2 --query SecretString --output text
```

---

## 업데이트 배포

### 코드 변경 후 재배포

1. **코드 수정**
2. **Docker 이미지 재빌드 & 푸시**
   ```powershell
   .\scripts\build-and-push-images.ps1
   ```
3. **ECS 서비스 강제 재배포**
   ```powershell
   aws ecs update-service --cluster unisync-cluster --service unisync-<service-name> --force-new-deployment --region ap-northeast-2
   ```

### Terraform 인프라 변경

```powershell
cd terraform

# 변경 사항 확인
terraform plan

# 변경 적용
terraform apply
```

---

## 트러블슈팅

### 1. ECR 로그인 실패

**증상:**
```
Error: login attempt to https://377846699896.dkr.ecr.ap-northeast-2.amazonaws.com/v2/ failed with status: 400 Bad Request
```

**해결:**
PowerShell 대신 cmd.exe를 사용하여 로그인:
```powershell
cmd /c "aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com"
```

### 2. Dockerfile ARM64 호환성 문제

**증상:**
```
ERROR: no match for platform in manifest: not found
```

**해결:**
Dockerfile에서 `-alpine` 이미지 대신 일반 이미지 사용:
```dockerfile
# ❌ 잘못된 예
FROM gradle:8.5-jdk21-alpine AS builder

# ✅ 올바른 예
FROM gradle:8.5-jdk21 AS builder
```

### 3. Secrets Manager 시크릿 삭제 오류

**증상:**
```
Error: You can't create this secret because a secret with this name is already scheduled for deletion.
```

**해결:**
삭제 예정인 시크릿 강제 삭제:
```powershell
aws secretsmanager delete-secret --secret-id unisync/rds-password --force-delete-without-recovery --region ap-northeast-2
```

### 4. ECS 태스크 시작 실패

**원인:**
- ECR에 이미지가 없음
- IAM 권한 부족
- 환경 변수 누락

**확인:**
```powershell
# ECS 태스크 이벤트 확인
aws ecs describe-services --cluster unisync-cluster --services unisync-api-gateway --region ap-northeast-2 --query 'services[0].events'

# CloudWatch Logs 확인
aws logs tail /ecs/unisync/api-gateway --follow --region ap-northeast-2
```

### 5. ALB Health Check 실패

**원인:**
- Spring Boot Actuator 엔드포인트 미설정
- Security Group 규칙 문제

**확인:**
```powershell
# Target Group 상태 확인
aws elbv2 describe-target-health --target-group-arn <target-group-arn> --region ap-northeast-2
```

**해결:**
`application.yml`에 Actuator 설정 추가:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

---

## 비용 최적화 팁

1. **Graviton (ARM64) 사용**: ECS 태스크와 RDS에서 ARM64 아키텍처 사용으로 약 20% 비용 절감
2. **Single NAT Gateway**: 개발 환경에서는 단일 NAT Gateway 사용
3. **Fargate Spot**: 프로덕션이 아닌 환경에서 Fargate Spot 사용 고려
4. **Auto Scaling**: CPU 사용률 기반 자동 스케일링으로 리소스 최적화

---

## 참고 자료

- [AWS ECS Fargate 문서](https://docs.aws.amazon.com/ecs/latest/developerguide/AWS_Fargate.html)
- [Terraform AWS Provider 문서](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Docker Buildx 문서](https://docs.docker.com/buildx/working-with-buildx/)
- [AWS ECR 문서](https://docs.aws.amazon.com/ecr/)

---

## 문의

배포 중 문제가 발생하면 CloudWatch Logs와 ECS 서비스 이벤트를 먼저 확인하세요.
