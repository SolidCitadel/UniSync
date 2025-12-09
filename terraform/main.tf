# Main Terraform Configuration for UniSync Infrastructure

terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.1"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }

  # Local backend (state file stored locally)
  backend "local" {
    path = "terraform.tfstate"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = var.common_tags
  }
}

# Network Module
module "network" {
  source = "./modules/network"

  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  availability_zones   = var.availability_zones
  project_name         = var.project_name
  single_nat_gateway   = var.single_nat_gateway
  tags                 = var.common_tags
}

# Security Groups Module
module "security_groups" {
  source = "./modules/security-groups"

  vpc_id         = module.network.vpc_id
  project_name   = var.project_name
  ec2_cidr_blocks = var.ec2_cidr_blocks
  tags           = var.common_tags
}

# Cognito Module
module "cognito" {
  source = "./modules/cognito"

  project_name = var.project_name
  tags         = var.common_tags
}

# RDS Module
module "rds" {
  source = "./modules/rds"

  project_name        = var.project_name
  private_subnet_ids  = module.network.private_subnet_ids
  security_group_ids  = [module.security_groups.rds_security_group_id]
  instance_class      = var.rds_instance_class
  allocated_storage   = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  master_username     = var.rds_master_username
  multi_az            = var.rds_multi_az
  backup_retention_period = var.rds_backup_retention_period
  performance_insights_enabled = var.rds_performance_insights_enabled
  deletion_protection = var.rds_deletion_protection
  tags                = var.common_tags
}

# Secrets Manager Module
module "secrets" {
  source = "./modules/secrets"

  project_name         = var.project_name
  canvas_sync_api_key  = var.canvas_sync_api_key
  tags                 = var.common_tags
}

# SQS Module
module "sqs" {
  source = "./modules/sqs"

  project_name = var.project_name
  tags         = var.common_tags
}

# Lambda Module
module "lambda" {
  source = "./modules/lambda"

  project_name                  = var.project_name
  aws_region                    = var.aws_region
  private_subnet_ids            = module.network.private_subnet_ids
  lambda_security_group_id      = module.security_groups.lambda_security_group_id
  user_service_url              = "http://${module.alb.alb_dns_name}"
  canvas_api_base_url           = var.canvas_api_base_url
  canvas_sync_api_key_secret_arn = module.secrets.canvas_sync_api_key_secret_arn
  sqs_queue_urls = {
    lambda_to_courseservice_sync = module.sqs.lambda_to_courseservice_sync_queue_url
  }
  sqs_queue_arns = {
    lambda_to_courseservice_sync = module.sqs.lambda_to_courseservice_sync_queue_arn
  }
  tags = var.common_tags
}

# EventBridge Module
module "eventbridge" {
  source = "./modules/eventbridge"

  project_name                      = var.project_name
  canvas_sync_lambda_arn            = module.lambda.canvas_sync_lambda_function_arn
  canvas_sync_lambda_function_name  = module.lambda.canvas_sync_lambda_function_name
  tags                              = var.common_tags
}

# ECR Module - Container Registry
module "ecr" {
  source = "./modules/ecr"

  project_name = var.project_name
  tags         = var.common_tags
}

# ALB Module - Application Load Balancer
module "alb" {
  source = "./modules/alb"

  project_name          = var.project_name
  vpc_id                = module.network.vpc_id
  public_subnet_ids     = module.network.public_subnet_ids
  alb_security_group_id = module.security_groups.alb_security_group_id
  tags                  = var.common_tags
}

# Service Discovery Module - Cloud Map
module "service_discovery" {
  source = "./modules/service-discovery"

  project_name  = var.project_name
  vpc_id        = module.network.vpc_id
  service_names = ["api-gateway", "user-service", "course-service", "schedule-service"]
  tags          = var.common_tags
}


# ECS Module - Fargate Cluster and Services
module "ecs" {
  source = "./modules/ecs"

  project_name          = var.project_name
  aws_region            = var.aws_region
  private_subnet_ids    = module.network.private_subnet_ids
  ecs_security_group_id = module.security_groups.ecs_security_group_id
  ecr_repository_urls   = module.ecr.repository_urls
  target_group_arns     = module.alb.target_group_arns
  cognito_user_pool_id  = module.cognito.user_pool_id
  cognito_client_id     = module.cognito.app_client_id

  # Service Discovery ARNs for inter-service communication
  service_discovery_arns = module.service_discovery.service_arns

  services = [
    {
      name   = "api-gateway"
      cpu    = 256
      memory = 512
      port   = 8080
      environment = [
        # Service Discovery DNS를 통한 서비스 간 통신
        { name = "USER_SERVICE_URL", value = "http://user-service.${var.project_name}.local:8081" },
        { name = "COURSE_SERVICE_URL", value = "http://course-service.${var.project_name}.local:8082" },
        { name = "SCHEDULE_SERVICE_URL", value = "http://schedule-service.${var.project_name}.local:8083" },
        { name = "COGNITO_ENDPOINT", value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${module.cognito.user_pool_id}" },
        { name = "CORS_ALLOWED_ORIGIN", value = "*" }
      ]
      secrets = []
    },
    {
      name   = "user-service"
      cpu    = 256
      memory = 512
      port   = 8081
      environment = [
        { name = "SPRING_DATASOURCE_URL", value = "jdbc:mysql://${module.rds.db_instance_address}:3306/user_db?createDatabaseIfNotExist=true" },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.rds_master_username },
        { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "update" },
        { name = "AWS_SQS_ENDPOINT", value = "https://sqs.${var.aws_region}.amazonaws.com" },
        { name = "SPRING_APPLICATION_JSON", value = "{\"jwt\":{\"secret\":\"UniSyncSuperSecretKeyForJwt2025!@#$\"}}" },
        { name = "CANVAS_BASE_URL", value = replace(var.canvas_api_base_url, "/\\/api\\/v1$/", "") },
        { name = "AWS_LAMBDA_CANVAS_SYNC_FUNCTION_NAME", value = module.lambda.canvas_sync_lambda_function_name },
        { name = "CANVAS_SYNC_API_KEY", value = var.canvas_sync_api_key }
      ]
      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = module.rds.db_secret_arn }
      ]
    },
    {
      name   = "course-service"
      cpu    = 256
      memory = 512
      port   = 8082
      environment = [
        { name = "SPRING_DATASOURCE_URL", value = "jdbc:mysql://${module.rds.db_instance_address}:3306/course_db?createDatabaseIfNotExist=true" },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.rds_master_username },
        { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "update" },
        { name = "AWS_SQS_ENDPOINT", value = "https://sqs.${var.aws_region}.amazonaws.com" },
        { name = "SQS_ASSIGNMENT_TO_SCHEDULE_QUEUE", value = module.sqs.courseservice_to_scheduleservice_assignments_queue_url }
      ]
      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = module.rds.db_secret_arn }
      ]
    },
    {
      name   = "schedule-service"
      cpu    = 256
      memory = 512
      port   = 8083
      environment = [
        { name = "SPRING_DATASOURCE_URL", value = "jdbc:mysql://${module.rds.db_instance_address}:3306/schedule_db?createDatabaseIfNotExist=true" },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.rds_master_username },
        { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "update" },
        { name = "AWS_SQS_ENDPOINT", value = "https://sqs.${var.aws_region}.amazonaws.com" },
        { name = "SQS_ASSIGNMENT_TO_SCHEDULE_QUEUE", value = module.sqs.courseservice_to_scheduleservice_assignments_queue_url }
      ]
      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = module.rds.db_secret_arn }
      ]
    }
  ]

  desired_count    = var.ecs_desired_count
  min_capacity     = var.ecs_min_capacity
  max_capacity     = var.ecs_max_capacity
  cpu_target_value = var.ecs_cpu_target_value

  tags = var.common_tags
}


