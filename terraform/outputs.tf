# Network Outputs
output "vpc_id" {
  description = "ID of the VPC"
  value       = module.network.vpc_id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC"
  value       = module.network.vpc_cidr
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = module.network.public_subnet_ids
}

output "private_subnet_ids" {
  description = "IDs of the private subnets"
  value       = module.network.private_subnet_ids
}

# Security Group Outputs
output "alb_security_group_id" {
  description = "ID of the ALB security group"
  value       = module.security_groups.alb_security_group_id
}

output "ecs_security_group_id" {
  description = "ID of the ECS Fargate security group"
  value       = module.security_groups.ecs_security_group_id
}

output "rds_security_group_id" {
  description = "ID of the RDS security group"
  value       = module.security_groups.rds_security_group_id
}

output "lambda_security_group_id" {
  description = "ID of the Lambda security group"
  value       = module.security_groups.lambda_security_group_id
}

# RDS Outputs
output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = module.rds.db_instance_endpoint
}

output "rds_address" {
  description = "RDS instance address (hostname)"
  value       = module.rds.db_instance_address
  sensitive   = false
}

output "rds_port" {
  description = "RDS instance port"
  value       = module.rds.db_instance_port
}

output "rds_master_username" {
  description = "RDS master username"
  value       = module.rds.master_username
}

output "rds_secret_arn" {
  description = "ARN of the Secrets Manager secret for DB password"
  value       = module.rds.db_secret_arn
}

output "rds_secret_name" {
  description = "Name of the Secrets Manager secret for DB password"
  value       = module.rds.db_secret_name
}

# Secrets Manager Outputs
output "canvas_sync_api_key_secret_arn" {
  description = "ARN of the Canvas Sync API key secret"
  value       = module.secrets.canvas_sync_api_key_secret_arn
}

output "canvas_sync_api_key_secret_name" {
  description = "Name of the Canvas Sync API key secret"
  value       = module.secrets.canvas_sync_api_key_secret_name
}

# SQS Outputs
output "dlq_queue_url" {
  description = "URL of the Dead Letter Queue"
  value       = module.sqs.dlq_queue_url
}

output "lambda_to_courseservice_sync_queue_url" {
  description = "URL of the lambda-to-courseservice-sync queue"
  value       = module.sqs.lambda_to_courseservice_sync_queue_url
}

output "courseservice_to_scheduleservice_assignments_queue_url" {
  description = "URL of the courseservice-to-scheduleservice-assignments queue"
  value       = module.sqs.courseservice_to_scheduleservice_assignments_queue_url
}

# Lambda Outputs
output "canvas_sync_lambda_function_name" {
  description = "Name of the Canvas Sync Lambda function"
  value       = module.lambda.canvas_sync_lambda_function_name
}

output "canvas_sync_lambda_function_arn" {
  description = "ARN of the Canvas Sync Lambda function"
  value       = module.lambda.canvas_sync_lambda_function_arn
}

output "canvas_sync_lambda_invoke_arn" {
  description = "Invoke ARN of the Canvas Sync Lambda function"
  value       = module.lambda.canvas_sync_lambda_invoke_arn
}

# EventBridge Outputs
output "eventbridge_rule_arn" {
  description = "ARN of the EventBridge rule"
  value       = module.eventbridge.eventbridge_rule_arn
}

output "eventbridge_rule_name" {
  description = "Name of the EventBridge rule"
  value       = module.eventbridge.eventbridge_rule_name
}

# Cognito Outputs
output "cognito_user_pool_id" {
  description = "Cognito User Pool ID"
  value       = module.cognito.user_pool_id
}

output "cognito_user_pool_arn" {
  description = "Cognito User Pool ARN"
  value       = module.cognito.user_pool_arn
}

output "cognito_user_pool_endpoint" {
  description = "Cognito User Pool endpoint"
  value       = module.cognito.user_pool_endpoint
}

output "cognito_app_client_id" {
  description = "Cognito App Client ID"
  value       = module.cognito.app_client_id
}

output "cognito_user_pool_name" {
  description = "Cognito User Pool name"
  value       = module.cognito.user_pool_name
}

# ECR Outputs
output "ecr_repository_urls" {
  description = "ECR repository URLs for each service"
  value       = module.ecr.repository_urls
}

output "ecr_registry_id" {
  description = "ECR registry ID"
  value       = module.ecr.registry_id
}

# ALB Outputs
output "alb_dns_name" {
  description = "ALB DNS name for accessing services"
  value       = module.alb.alb_dns_name
}

output "alb_arn" {
  description = "ALB ARN"
  value       = module.alb.alb_arn
}

# ECS Outputs
output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.ecs.cluster_name
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN"
  value       = module.ecs.cluster_arn
}

output "ecs_service_names" {
  description = "ECS service names"
  value       = module.ecs.service_names
}

