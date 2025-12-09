# ECR Module Outputs

output "repository_urls" {
  description = "ECR repository URLs for each service"
  value = {
    for key, repo in aws_ecr_repository.services : key => repo.repository_url
  }
}

output "repository_arns" {
  description = "ECR repository ARNs for each service"
  value = {
    for key, repo in aws_ecr_repository.services : key => repo.arn
  }
}

output "registry_id" {
  description = "ECR registry ID"
  value       = values(aws_ecr_repository.services)[0].registry_id
}
