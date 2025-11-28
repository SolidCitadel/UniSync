variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "canvas_sync_lambda_arn" {
  description = "ARN of the Canvas Sync Lambda function"
  type        = string
}

variable "canvas_sync_lambda_function_name" {
  description = "Name of the Canvas Sync Lambda function"
  type        = string
}

variable "tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

