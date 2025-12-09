# Security Groups Module

# Security Group for ALB
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-sg-alb"
  description = "Security group for Application Load Balancer"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-sg-alb"
    }
  )
}

# Security Group for ECS Fargate
resource "aws_security_group" "ecs" {
  name        = "${var.project_name}-sg-ecs-fargate"
  description = "Security group for ECS Fargate tasks"
  vpc_id      = var.vpc_id

  # API Gateway port from ALB
  ingress {
    description     = "API Gateway port from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # User Service port from ALB
  ingress {
    description     = "User Service port from ALB"
    from_port       = 8081
    to_port         = 8081
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Course Service port from ALB
  ingress {
    description     = "Course Service port from ALB"
    from_port       = 8082
    to_port         = 8082
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Schedule Service port from ALB
  ingress {
    description     = "Schedule Service port from ALB"
    from_port       = 8083
    to_port         = 8083
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # ECS 서비스 간 통신 (Service Discovery)
  ingress {
    description = "Inter-service communication"
    from_port   = 8080
    to_port     = 8083
    protocol    = "tcp"
    self        = true
  }

  egress {
    description = "All outbound traffic via NAT Gateway"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-sg-ecs-fargate"
    }
  )
}


# ECS to RDS egress rule (별도 리소스로 분리하여 순환 참조 방지)
resource "aws_security_group_rule" "ecs_to_rds" {
  type                     = "egress"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds.id
  security_group_id        = aws_security_group.ecs.id
  description              = "MySQL access to RDS"
}

# Security Group for RDS
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-sg-rds"
  description = "Security group for RDS MySQL"
  vpc_id      = var.vpc_id

  # RDS는 클라이언트 연결을 받기만 하면 되므로 egress 규칙 불필요

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-sg-rds"
    }
  )
}

# RDS ingress from EC2 (Temporary for migration - moved from inline block)
resource "aws_security_group_rule" "rds_from_ec2" {
  type              = "ingress"
  from_port         = 3306
  to_port           = 3306
  protocol          = "tcp"
  cidr_blocks       = var.ec2_cidr_blocks
  security_group_id = aws_security_group.rds.id
  description       = "MySQL from EC2 (temporary for migration)"
}

# RDS ingress from ECS (별도 리소스로 분리하여 순환 참조 방지)
resource "aws_security_group_rule" "rds_from_ecs" {
  type                     = "ingress"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ecs.id
  security_group_id        = aws_security_group.rds.id
  description              = "MySQL from ECS Fargate"
}

# RDS ingress from Lambda (별도 리소스로 분리하여 순환 참조 방지)
resource "aws_security_group_rule" "rds_from_lambda" {
  type                     = "ingress"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.lambda.id
  security_group_id        = aws_security_group.rds.id
  description              = "MySQL from Lambda"
}

# Security Group for Lambda
resource "aws_security_group" "lambda" {
  name        = "${var.project_name}-sg-lambda"
  description = "Security group for Lambda functions in VPC"
  vpc_id      = var.vpc_id

  # Lambda는 EventBridge가 VPC 외부에서 호출하므로 inbound 규칙 없음

  egress {
    description = "All outbound traffic via NAT Gateway"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-sg-lambda"
    }
  )
}

# Lambda to RDS egress rule (별도 리소스로 분리하여 순환 참조 방지)
resource "aws_security_group_rule" "lambda_to_rds" {
  type                     = "egress"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds.id
  security_group_id        = aws_security_group.lambda.id
  description              = "MySQL access to RDS"
}


# Lambda to ECS ingress rule
resource "aws_security_group_rule" "ecs_from_lambda" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8083
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.lambda.id
  security_group_id        = aws_security_group.ecs.id
  description              = "Access from Lambda to ECS Services"
}
