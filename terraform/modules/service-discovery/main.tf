# Service Discovery Module - AWS Cloud Map
# Private DNS namespace for internal service communication

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "service_names" {
  description = "List of service names to register"
  type        = list(string)
  default     = ["api-gateway", "user-service", "course-service", "schedule-service"]
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

# Private DNS Namespace
resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "${var.project_name}.local"
  vpc         = var.vpc_id
  description = "Private DNS namespace for ${var.project_name} services"

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-namespace"
    }
  )
}

# Service Discovery Services (one per ECS service)
resource "aws_service_discovery_service" "services" {
  for_each = toset(var.service_names)

  name = each.key

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.main.id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${each.key}-discovery"
    }
  )
}

# Outputs
output "namespace_id" {
  description = "Service Discovery namespace ID"
  value       = aws_service_discovery_private_dns_namespace.main.id
}

output "namespace_name" {
  description = "Service Discovery namespace name"
  value       = aws_service_discovery_private_dns_namespace.main.name
}

output "service_arns" {
  description = "Service Discovery service ARNs"
  value = {
    for key, svc in aws_service_discovery_service.services : key => svc.arn
  }
}
