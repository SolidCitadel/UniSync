output "canvas_sync_lambda_function_name" {
  description = "Name of the Canvas Sync Lambda function"
  value       = aws_lambda_function.canvas_sync.function_name
}

output "canvas_sync_lambda_function_arn" {
  description = "ARN of the Canvas Sync Lambda function"
  value       = aws_lambda_function.canvas_sync.arn
}

output "canvas_sync_lambda_invoke_arn" {
  description = "Invoke ARN of the Canvas Sync Lambda function"
  value       = aws_lambda_function.canvas_sync.invoke_arn
}

output "lambda_execution_role_arn" {
  description = "ARN of the Lambda execution role (LabRole)"
  value       = var.lambda_execution_role_arn
}

