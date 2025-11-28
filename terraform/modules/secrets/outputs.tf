output "canvas_sync_api_key_secret_arn" {
  description = "ARN of the Canvas Sync API key secret"
  value       = aws_secretsmanager_secret.canvas_sync_api_key.arn
}

output "canvas_sync_api_key_secret_name" {
  description = "Name of the Canvas Sync API key secret"
  value       = aws_secretsmanager_secret.canvas_sync_api_key.name
}

