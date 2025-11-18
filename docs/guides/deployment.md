# Deployment Guide - AWS ECS

UniSync 프로젝트를 AWS ECS 환경에 배포하는 가이드입니다.

> **Note**: 이 문서는 수동 배포 절차를 다룹니다. CI/CD 자동화는 향후 추가될 예정입니다.

---

## 📋 배포 아키텍처

```
Internet
  ↓
Application Load Balancer (ALB)
  ↓
┌─────────────────── ECS Cluster ────────────────────┐
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│
│  │ API Gateway │  │User Service │  │Course Svc   ││
│  │  (Fargate)  │  │  (Fargate)  │  │  (Fargate)  ││
│  └─────────────┘  └─────────────┘  └─────────────┘│
│  ┌─────────────┐                                   │
│  │Schedule Svc │                                   │
│  │  (Fargate)  │                                   │
│  └─────────────┘                                   │
└────────────────────────────────────────────────────┘
  ↓                    ↓                    ↓
RDS MySQL         Lambda Functions      SQS Queues
(Private)         (Serverless)          (Managed)
```

---

## 🛠️ 1. 사전 준비

### 필요한 도구
- AWS CLI 설치 및 구성
- Docker 설치
- 적절한 IAM 권한 (ECS, RDS, Lambda, SQS, Secrets Manager)

### AWS CLI 설정
```bash
aws configure
# AWS Access Key ID: YOUR_ACCESS_KEY
# AWS Secret Access Key: YOUR_SECRET_KEY
# Default region: ap-northeast-2
# Default output format: json
```

---

## 🏗️ 2. AWS 인프라 설정

### 2.1 VPC 및 네트워크 구성

**VPC 생성** (이미 있으면 기존 VPC 사용):
```bash
# VPC 생성
aws ec2 create-vpc \
  --cidr-block 10.0.0.0/16 \
  --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=unisync-vpc}]'

# VPC ID 저장
export VPC_ID=vpc-xxxxx
```

**서브넷 생성** (가용 영역별 Public/Private):
```bash
# Public 서브넷 (ALB용)
aws ec2 create-subnet \
  --vpc-id $VPC_ID \
  --cidr-block 10.0.1.0/24 \
  --availability-zone ap-northeast-2a \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=unisync-public-2a}]'

aws ec2 create-subnet \
  --vpc-id $VPC_ID \
  --cidr-block 10.0.2.0/24 \
  --availability-zone ap-northeast-2c \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=unisync-public-2c}]'

# Private 서브넷 (ECS, RDS용)
aws ec2 create-subnet \
  --vpc-id $VPC_ID \
  --cidr-block 10.0.11.0/24 \
  --availability-zone ap-northeast-2a \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=unisync-private-2a}]'

aws ec2 create-subnet \
  --vpc-id $VPC_ID \
  --cidr-block 10.0.12.0/24 \
  --availability-zone ap-northeast-2c \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=unisync-private-2c}]'
```

**인터넷 게이트웨이 및 NAT 게이트웨이**:
```bash
# 인터넷 게이트웨이 생성 및 연결
aws ec2 create-internet-gateway \
  --tag-specifications 'ResourceType=internet-gateway,Tags=[{Key=Name,Value=unisync-igw}]'

export IGW_ID=igw-xxxxx

aws ec2 attach-internet-gateway \
  --vpc-id $VPC_ID \
  --internet-gateway-id $IGW_ID

# NAT 게이트웨이 (Private 서브넷의 외부 통신용)
# 1. Elastic IP 할당
aws ec2 allocate-address --domain vpc

export EIP_ID=eipalloc-xxxxx

# 2. NAT 게이트웨이 생성 (Public 서브넷에 배치)
aws ec2 create-nat-gateway \
  --subnet-id subnet-xxxxx \  # Public 서브넷 ID
  --allocation-id $EIP_ID
```

### 2.2 보안 그룹 생성

**ALB 보안 그룹**:
```bash
aws ec2 create-security-group \
  --group-name unisync-alb-sg \
  --description "Security group for UniSync ALB" \
  --vpc-id $VPC_ID

export ALB_SG=sg-xxxxx

# HTTP/HTTPS 허용
aws ec2 authorize-security-group-ingress \
  --group-id $ALB_SG \
  --protocol tcp --port 80 --cidr 0.0.0.0/0

aws ec2 authorize-security-group-ingress \
  --group-id $ALB_SG \
  --protocol tcp --port 443 --cidr 0.0.0.0/0
```

**ECS 태스크 보안 그룹**:
```bash
aws ec2 create-security-group \
  --group-name unisync-ecs-sg \
  --description "Security group for UniSync ECS tasks" \
  --vpc-id $VPC_ID

export ECS_SG=sg-xxxxx

# ALB에서의 트래픽만 허용
aws ec2 authorize-security-group-ingress \
  --group-id $ECS_SG \
  --protocol tcp --port 8080-8083 \
  --source-group $ALB_SG
```

**RDS 보안 그룹**:
```bash
aws ec2 create-security-group \
  --group-name unisync-rds-sg \
  --description "Security group for UniSync RDS" \
  --vpc-id $VPC_ID

export RDS_SG=sg-xxxxx

# ECS 태스크에서의 MySQL 접근 허용
aws ec2 authorize-security-group-ingress \
  --group-id $RDS_SG \
  --protocol tcp --port 3306 \
  --source-group $ECS_SG
```

---

## 🗄️ 3. RDS MySQL 설정

### 3.1 서브넷 그룹 생성
```bash
aws rds create-db-subnet-group \
  --db-subnet-group-name unisync-db-subnet-group \
  --db-subnet-group-description "Subnet group for UniSync RDS" \
  --subnet-ids subnet-xxxxx subnet-yyyyy  # Private 서브넷 IDs
```

### 3.2 RDS 인스턴스 생성
```bash
aws rds create-db-instance \
  --db-instance-identifier unisync-mysql \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --engine-version 8.0 \
  --master-username admin \
  --master-user-password 'YOUR_SECURE_PASSWORD' \
  --allocated-storage 20 \
  --db-subnet-group-name unisync-db-subnet-group \
  --vpc-security-group-ids $RDS_SG \
  --no-publicly-accessible \
  --backup-retention-period 7 \
  --multi-az
```

### 3.3 데이터베이스 생성
RDS 인스턴스가 생성되면 엔드포인트를 확인하고 데이터베이스를 생성합니다:

```bash
# RDS 엔드포인트 확인
aws rds describe-db-instances \
  --db-instance-identifier unisync-mysql \
  --query 'DBInstances[0].Endpoint.Address'

export RDS_ENDPOINT=unisync-mysql.xxxxx.ap-northeast-2.rds.amazonaws.com

# MySQL 접속 (VPN 또는 베스천 호스트 통해)
mysql -h $RDS_ENDPOINT -u admin -p

# 데이터베이스 생성
CREATE DATABASE user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE course_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE schedule_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 🔐 4. Secrets Manager 설정

민감한 환경변수를 Secrets Manager에 저장합니다.

```bash
# ENCRYPTION_KEY 저장
aws secretsmanager create-secret \
  --name unisync/encryption-key \
  --secret-string "$(openssl rand -base64 32)"

# RDS 비밀번호 저장
aws secretsmanager create-secret \
  --name unisync/rds-password \
  --secret-string "YOUR_SECURE_PASSWORD"

# Canvas API 토큰 (테스트용)
aws secretsmanager create-secret \
  --name unisync/canvas-api-token \
  --secret-string "YOUR_CANVAS_TOKEN"

# Cognito 설정
aws secretsmanager create-secret \
  --name unisync/cognito-config \
  --secret-string '{
    "userPoolId": "ap-northeast-2_xxxxx",
    "clientId": "xxxxx"
  }'
```

---

## 📦 5. ECR 및 Docker 이미지

### 5.1 ECR 리포지토리 생성
```bash
# 각 서비스별 ECR 리포지토리 생성
aws ecr create-repository --repository-name unisync/api-gateway
aws ecr create-repository --repository-name unisync/user-service
aws ecr create-repository --repository-name unisync/course-service
aws ecr create-repository --repository-name unisync/schedule-service
```

### 5.2 Docker 이미지 빌드 및 푸시
```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin \
  ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com

# API Gateway 빌드 및 푸시
cd app/backend/api-gateway
docker build -t unisync/api-gateway .
docker tag unisync/api-gateway:latest \
  ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/unisync/api-gateway:latest
docker push ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/unisync/api-gateway:latest

# 나머지 서비스도 동일하게 반복
cd ../user-service
docker build -t unisync/user-service .
docker tag unisync/user-service:latest \
  ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/unisync/user-service:latest
docker push ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/unisync/user-service:latest

# course-service, schedule-service도 동일
```

---

## 🐋 6. ECS 클러스터 및 서비스 설정

### 6.1 ECS 클러스터 생성
```bash
aws ecs create-cluster --cluster-name unisync-cluster
```

### 6.2 태스크 실행 역할 생성

**신뢰 정책** (`ecs-task-trust-policy.json`):
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

**역할 생성**:
```bash
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-task-trust-policy.json

# 필수 정책 연결
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

# Secrets Manager 접근 정책 추가
aws iam put-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name SecretsManagerAccess \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": [
          "secretsmanager:GetSecretValue"
        ],
        "Resource": "arn:aws:secretsmanager:ap-northeast-2:ACCOUNT_ID:secret:unisync/*"
      }
    ]
  }'
```

### 6.3 태스크 정의 등록

**API Gateway 태스크 정의** (`api-gateway-task.json`):
```json
{
  "family": "unisync-api-gateway",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::ACCOUNT_ID:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "api-gateway",
      "image": "ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/unisync/api-gateway:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        },
        {
          "name": "USER_SERVICE_URL",
          "value": "http://unisync-user-service:8081"
        },
        {
          "name": "COURSE_SERVICE_URL",
          "value": "http://unisync-course-service:8082"
        },
        {
          "name": "SCHEDULE_SERVICE_URL",
          "value": "http://unisync-schedule-service:8083"
        }
      ],
      "secrets": [
        {
          "name": "COGNITO_USER_POOL_ID",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:ACCOUNT_ID:secret:unisync/cognito-config:userPoolId::"
        },
        {
          "name": "COGNITO_CLIENT_ID",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:ACCOUNT_ID:secret:unisync/cognito-config:clientId::"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/unisync-api-gateway",
          "awslogs-region": "ap-northeast-2",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

**태스크 정의 등록**:
```bash
aws ecs register-task-definition --cli-input-json file://api-gateway-task.json

# 나머지 서비스도 동일하게 태스크 정의 생성 및 등록
```

### 6.4 Application Load Balancer 생성

```bash
# ALB 생성
aws elbv2 create-load-balancer \
  --name unisync-alb \
  --subnets subnet-xxxxx subnet-yyyyy \  # Public 서브넷 IDs
  --security-groups $ALB_SG

export ALB_ARN=arn:aws:elasticloadbalancing:...

# 타겟 그룹 생성 (API Gateway용)
aws elbv2 create-target-group \
  --name unisync-api-gateway-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id $VPC_ID \
  --target-type ip \
  --health-check-path /actuator/health \
  --health-check-interval-seconds 30

export TG_ARN=arn:aws:elasticloadbalancing:...

# 리스너 생성
aws elbv2 create-listener \
  --load-balancer-arn $ALB_ARN \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn=$TG_ARN
```

### 6.5 ECS 서비스 생성

```bash
aws ecs create-service \
  --cluster unisync-cluster \
  --service-name api-gateway \
  --task-definition unisync-api-gateway \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={
    subnets=[subnet-xxxxx,subnet-yyyyy],
    securityGroups=[$ECS_SG],
    assignPublicIp=DISABLED
  }" \
  --load-balancers "targetGroupArn=$TG_ARN,containerName=api-gateway,containerPort=8080"

# 나머지 서비스도 동일하게 생성 (user-service, course-service, schedule-service)
```

---

## ⚡ 7. Lambda 함수 배포

### 7.1 Lambda 실행 역할 생성

```bash
# 신뢰 정책
cat > lambda-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

aws iam create-role \
  --role-name unisync-lambda-execution-role \
  --assume-role-policy-document file://lambda-trust-policy.json

# 필수 정책 연결
aws iam attach-role-policy \
  --role-name unisync-lambda-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam attach-role-policy \
  --role-name unisync-lambda-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole

# SQS, Secrets Manager 권한 추가
```

### 7.2 Lambda 함수 패키징 및 배포

```bash
cd app/serverless/canvas-sync-lambda

# 의존성 포함하여 패키징
pip install -r requirements.txt -t package/
cd package
zip -r ../canvas-sync-lambda.zip .
cd ..
zip -g canvas-sync-lambda.zip src/handler.py

# Lambda 함수 생성
aws lambda create-function \
  --function-name canvas-sync-lambda \
  --runtime python3.11 \
  --role arn:aws:iam::ACCOUNT_ID:role/unisync-lambda-execution-role \
  --handler src.handler.lambda_handler \
  --zip-file fileb://canvas-sync-lambda.zip \
  --timeout 300 \
  --memory-size 512 \
  --environment Variables="{
    USER_SERVICE_URL=http://unisync-user-service:8081,
    CANVAS_API_BASE_URL=https://canvas.instructure.com/api/v1
  }"
```

---

## 🔄 8. SQS 큐 생성

```bash
# assignment-events-queue
aws sqs create-queue --queue-name assignment-events-queue

# DLQ
aws sqs create-queue --queue-name assignment-events-queue-dlq

# 나머지 큐들도 생성
aws sqs create-queue --queue-name submission-events-queue
aws sqs create-queue --queue-name task-creation-queue
aws sqs create-queue --queue-name llm-analysis-queue
```

---

## ✅ 9. 배포 확인

### 9.1 ALB DNS로 접속
```bash
# ALB DNS 확인
aws elbv2 describe-load-balancers \
  --names unisync-alb \
  --query 'LoadBalancers[0].DNSName'

# Health Check
curl http://ALB_DNS/actuator/health
```

### 9.2 로그 확인
```bash
# CloudWatch Logs 확인
aws logs tail /ecs/unisync-api-gateway --follow
```

---

## 🔧 10. 업데이트 배포

### 새 버전 배포
```bash
# 1. 새 이미지 빌드 및 푸시
docker build -t unisync/api-gateway:v1.1 .
docker tag unisync/api-gateway:v1.1 \
  ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/unisync/api-gateway:v1.1
docker push ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/unisync/api-gateway:v1.1

# 2. 새 태스크 정의 등록 (이미지 태그만 변경)
aws ecs register-task-definition --cli-input-json file://api-gateway-task-v1.1.json

# 3. 서비스 업데이트
aws ecs update-service \
  --cluster unisync-cluster \
  --service api-gateway \
  --task-definition unisync-api-gateway:2  # 새 리비전 번호
```

---

## 💰 비용 절감 팁

1. **Fargate Spot 사용**: 개발/스테이징 환경에서 최대 70% 절감
2. **RDS 인스턴스 크기 조정**: 트래픽에 따라 db.t3.micro → db.t3.small
3. **CloudWatch Logs 보존 기간**: 7일 → 3일 (개발 환경)
4. **NAT 게이트웨이**: 고비용 → VPC Endpoint 사용 고려 (S3, SQS)

---

## 📚 참고 자료

- [AWS ECS 공식 문서](https://docs.aws.amazon.com/ecs/)
- [AWS RDS 공식 문서](https://docs.aws.amazon.com/rds/)
- [프로젝트 아키텍처](../design/system-architecture.md)
- [환경변수 가이드](./environment-variables.md)
