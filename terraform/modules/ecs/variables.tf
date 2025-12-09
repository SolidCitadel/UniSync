# ECS Module Variables

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for ECS tasks"
  type        = list(string)
}

variable "ecs_security_group_id" {
  description = "Security group ID for ECS tasks"
  type        = string
}

variable "ecr_repository_urls" {
  description = "ECR repository URLs for each service"
  type        = map(string)
}

variable "target_group_arns" {
  description = "Target group ARNs for each service"
  type        = map(string)
}

variable "cognito_user_pool_id" {
  description = "Cognito User Pool ID"
  type        = string
}

variable "cognito_client_id" {
  description = "Cognito App Client ID"
  type        = string
}

variable "service_names" {
  description = "List of service names"
  type        = list(string)
  default     = ["api-gateway", "user-service", "course-service", "schedule-service"]
}

variable "services" {
  description = "Service configurations"
  type = list(object({
    name        = string
    cpu         = number
    memory      = number
    port        = number
    environment = list(object({
      name  = string
      value = string
    }))
    secrets = list(object({
      name      = string
      valueFrom = string
    }))
  }))
}

variable "desired_count" {
  description = "Desired number of tasks"
  type        = number
  default     = 1
}

variable "min_capacity" {
  description = "Minimum number of tasks for auto scaling"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum number of tasks for auto scaling"
  type        = number
  default     = 3
}

variable "cpu_target_value" {
  description = "Target CPU utilization for auto scaling"
  type        = number
  default     = 70
}

variable "tags" {
  description = "Common tags"
  type        = map(string)
  default     = {}
}

variable "service_discovery_arns" {
  description = "Service Discovery ARNs for each service"
  type        = map(string)
  default     = {}
}
