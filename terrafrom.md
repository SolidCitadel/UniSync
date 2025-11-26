Services
Route 53 -> CloudFront(S3) -> ALB
ECS Fargate
RDS
Lambda (NAT Gateway)
EventBridge
CloudWatch Logs

IAM
ECS Service Role : ECS Service
    Loadbalancing, Cluster managing
Task Execution Role : Container Agent
    Image puling, Cloudwatch log, Secret Manager access
Task Role : Application in Container
    Acces to RDS, S3, SQS
Lambda Execution Role
    cloudwatch log, access to vpc, secrets manager

SG
SG-ALB
inbound
    HTTP 80 0.0.0.0/0
    HTTPS 443 0.0.0.0/0
outbound
    all traffic

SG-ECS-Fargate
inbound
    Custom TCP 8080 (container port) SG-ALB
outbound
    Custom TCP 3306 (MySQL port) SG-RDS
    All traffic (Using NAT Gateway)

SG-RDS
inbound
    Custom TCP 3306 (MySQL port) SG-ECS-Fargate
    Custom TCP 3306 (MySQL port) SG-Lambda
    Custom TCP 3306 (MySQL port) EC2 (임시 - RDS 마이그레이션용)
outbound
    All traffic SG-ECS-Fargate, SG-Lambda

SG-Lambda
inbound
    없음 (EventBridge는 VPC 외부에서 Lambda를 호출)
Outbound
    Custom TCP 3306 (MySQL port) SG-RDS
    All traffic (Using NAT Gateway)


VPC 10.0.0.0/16
public subnet a 10.0.1.0/24
    ALB, NAT Gateway
public subnet b 10.0.2.0/24
    동일
private subnet a 10.0.11.0/24
    ECS fargate, RDS, Lambda
private subnet b 10.0.12.0/24
    동일

## Terraform 사용 방법

### 디렉토리 구조
```
terraform/
├── modules/
│   ├── network/          # VPC, Subnets, IGW, NAT Gateway
│   ├── security-groups/  # Security Groups (ALB, ECS, RDS, Lambda)
│   └── rds/              # RDS MySQL with Secrets Manager
├── main.tf               # 메인 Terraform 설정
├── variables.tf          # 변수 정의
├── outputs.tf            # 출력 값
└── terraform.tfvars      # 변수 값 (gitignore됨)
```

### 초기 설정 및 실행

1. **변수 파일 생성**
   ```bash
   cd terraform
   cp terraform.tfvars.example terraform.tfvars
   # terraform.tfvars 파일 편집 (EC2 IP 추가 등)
   ```

2. **Terraform 초기화**
   ```bash
   terraform init
   ```

3. **계획 확인**
   ```bash
   terraform plan
   ```

4. **인프라 생성**
   ```bash
   terraform apply
   ```

5. **출력 값 확인**
   ```bash
   terraform output rds_address
   terraform output rds_port
   ```

### RDS 설정

- **인스턴스 클래스**: db.t3.micro
- **스토리지**: 30GB (최대 100GB 자동 확장)
- **Multi-AZ**: 활성화 (고가용성)
- **백업**: 7일 보관
- **엔진**: MySQL 8.0.39
- **포트**: 3306
- **비밀번호**: Secrets Manager에 저장 (`unisync/rds-password`)

### 주의사항

- **DB 포트**: MySQL은 3306 포트 사용 (PostgreSQL의 5432가 아님)
- **논리적 DB 생성**: Terraform은 RDS 인스턴스만 생성. `user_db`, `course_db`, `schedule_db`는 애플리케이션 초기화 시점이나 별도 스크립트로 생성
- **EC2 접근**: RDS 마이그레이션을 위해 임시로 EC2 IP를 보안 그룹에 추가 (ECS 전환 후 제거)
- **Secrets Manager**: DB 비밀번호는 Secrets Manager에 저장되며, Terraform에서 자동 생성 및 참조