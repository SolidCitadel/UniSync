# UniSync Terraform Infrastructure

이 디렉토리는 UniSync 프로젝트의 AWS 인프라를 Terraform으로 관리합니다.

## 구조

```
terraform/
├── modules/
│   ├── network/          # VPC, Subnets, IGW, NAT Gateway
│   ├── security-groups/  # Security Groups (ALB, ECS, RDS, Lambda)
│   └── rds/              # RDS MySQL with Secrets Manager
├── main.tf               # 메인 Terraform 설정
├── variables.tf          # 변수 정의
├── outputs.tf            # 출력 값
├── terraform.tfvars      # 변수 값 (gitignore됨)
└── README.md             # 이 파일
```

## 사용 방법

### 1. 초기 설정

```bash
cd terraform

# terraform.tfvars 파일 생성 (예시 파일 복사)
cp terraform.tfvars.example terraform.tfvars

# terraform.tfvars 파일 편집하여 필요한 값 설정
# 특히 ec2_cidr_blocks는 EC2 인스턴스 IP를 추가해야 RDS 접근 가능
```

### 2. Terraform 초기화

```bash
terraform init
```

### 3. 계획 확인

```bash
terraform plan
```

### 4. 인프라 생성

```bash
terraform apply
```

### 5. 출력 값 확인

```bash
terraform output
```

특히 RDS 엔드포인트는 다음 명령어로 확인:
```bash
terraform output rds_address
terraform output rds_port
```

### 6. Secrets Manager에서 비밀번호 확인

```bash
aws secretsmanager get-secret-value \
  --secret-id $(terraform output -raw rds_secret_name) \
  --region us-east-1 \
  --query SecretString --output text
```

## 모듈 설명

### Network Module
- VPC 생성 (10.0.0.0/16)
- Public Subnets (2개) - ALB, NAT Gateway용
- Private Subnets (2개) - ECS, RDS, Lambda용
- Internet Gateway
- NAT Gateway (각 AZ별)

### Security Groups Module
- **SG-ALB**: HTTP(80), HTTPS(443) 허용
- **SG-ECS**: ALB에서 8080 포트 허용, RDS 3306 포트 허용
- **SG-RDS**: ECS와 Lambda에서 3306 포트 허용, EC2 임시 접근 허용
- **SG-Lambda**: RDS 3306 포트 허용

### RDS Module
- MySQL 8.0 인스턴스
- Multi-AZ 구성
- Secrets Manager에 비밀번호 저장
- 자동 백업 (7일 보관)
- CloudWatch Logs 활성화

## 다음 단계

1. RDS 생성 후 `.env.local` 파일 업데이트
2. Docker Compose에서 MySQL 컨테이너 제거
3. 애플리케이션 재시작 및 테스트

## 주의사항

- `terraform.tfvars` 파일은 gitignore되므로 비밀 정보를 안전하게 관리
- RDS 삭제 보호가 활성화되어 있음 (`rds_deletion_protection = true`)
- EC2에서 RDS 접근을 위해 보안 그룹에 EC2 IP를 추가해야 함

