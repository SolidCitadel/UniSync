Terraform 변환 전략
Phase 1: 인프라 기반 (우선순위 높음)
목표: AWS 관리형 서비스로 전환

RDS MySQL (MySQL 컨테이너 대체)
이유: 데이터 영구성, 백업, Multi-AZ
난이도: 낮음
영향: 서비스 재시작 필요 (데이터 마이그레이션 필요)
Terraform 리소스:
aws_db_instance (3개 DB)
aws_db_subnet_group
aws_security_group

AWS SQS (LocalStack SQS 대체)
이유: 실제 AWS 서비스 사용
난이도: 낮음
영향: 코드 변경 최소 (엔드포인트만 변경)
Terraform 리소스:
aws_sqs_queue (5개 큐)
aws_sqs_queue_policy

AWS Cognito (LocalStack Cognito 대체)
이유: 프로덕션 인증
난이도: 중간
영향: JWT 검증 로직 확인 필요
Terraform 리소스:
aws_cognito_user_pool
aws_cognito_user_pool_client

Phase 2: 서버리스 (중간 우선순위)
목표: Lambda 함수 배포

Lambda 함수 (LocalStack Lambda 대체)
이유: 서버리스 실행
난이도: 중간
영향: 배포 방식 변경
Terraform 리소스:
aws_lambda_function (Canvas Sync, LLM)
aws_lambda_permission
aws_iam_role (Lambda 실행 역할)

Phase 3: 컨테이너 서비스 (높은 우선순위)
목표: Spring Boot 서비스를 ECS로 전환

ECS Fargate (컨테이너 서비스)
이유: 확장성, 자동 복구
난이도: 높음
영향: 큰 변경 (배포 파이프라인 필요)
Terraform 리소스:
aws_ecs_cluster
aws_ecs_service (4개 서비스)
aws_ecs_task_definition
aws_ecr_repository (Docker 이미지 저장)

Application Load Balancer (ALB)
이유: 트래픽 분산, SSL 종료
난이도: 중간
영향: API Gateway 라우팅 설정 변경
Terraform 리소스:
aws_lb
aws_lb_target_group
aws_lb_listener

Phase 4: 네트워크 및 보안 (기반)
목표: VPC 및 보안 구성

VPC 및 네트워크
이유: 격리 및 보안
난이도: 중간
영향: 모든 리소스 배치 변경
Terraform 리소스:
aws_vpc
aws_subnet (Public/Private)
aws_internet_gateway
aws_nat_gateway

보안 그룹 및 IAM
이유: 최소 권한 원칙
난이도: 중간
영향: 서비스 간 통신 설정
Terraform 리소스:
aws_security_group
aws_iam_role
aws_iam_policy