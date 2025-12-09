# ALB Module Variables

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs for ALB"
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "Security group ID for ALB"
  type        = string
}

variable "services" {
  description = "Service configurations"
  type = list(object({
    name = string
    port = number
  }))
  default = [
    { name = "api-gateway", port = 8080 },
    { name = "user-service", port = 8081 },
    { name = "course-service", port = 8082 },
    { name = "schedule-service", port = 8083 }
  ]
}

variable "tags" {
  description = "Common tags"
  type        = map(string)
  default     = {}
}
