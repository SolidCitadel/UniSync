variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "canvas_sync_api_key" {
  description = "Canvas Sync API key value"
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

