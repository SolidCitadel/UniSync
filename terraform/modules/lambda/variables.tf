variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for Lambda VPC configuration"
  type        = list(string)
}

variable "lambda_security_group_id" {
  description = "Security group ID for Lambda"
  type        = string
}

variable "user_service_url" {
  description = "User Service URL"
  type        = string
}

variable "canvas_api_base_url" {
  description = "Canvas API base URL"
  type        = string
}

variable "canvas_sync_api_key_secret_arn" {
  description = "ARN of the Canvas Sync API key secret"
  type        = string
}

variable "sqs_queue_urls" {
  description = "SQS queue URLs"
  type = object({
    lambda_to_courseservice_sync = string
  })
}

variable "sqs_queue_arns" {
  description = "SQS queue ARNs"
  type = object({
    lambda_to_courseservice_sync = string
  })
}

variable "tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

