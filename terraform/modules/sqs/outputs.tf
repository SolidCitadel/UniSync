output "dlq_queue_url" {
  description = "URL of the Dead Letter Queue"
  value       = aws_sqs_queue.dlq.url
}

output "dlq_queue_arn" {
  description = "ARN of the Dead Letter Queue"
  value       = aws_sqs_queue.dlq.arn
}

output "lambda_to_courseservice_sync_queue_url" {
  description = "URL of the lambda-to-courseservice-sync queue"
  value       = aws_sqs_queue.lambda_to_courseservice_sync.url
}

output "lambda_to_courseservice_sync_queue_arn" {
  description = "ARN of the lambda-to-courseservice-sync queue"
  value       = aws_sqs_queue.lambda_to_courseservice_sync.arn
}

output "courseservice_to_scheduleservice_assignments_queue_url" {
  description = "URL of the courseservice-to-scheduleservice-assignments queue"
  value       = aws_sqs_queue.courseservice_to_scheduleservice_assignments.url
}

output "courseservice_to_scheduleservice_assignments_queue_arn" {
  description = "ARN of the courseservice-to-scheduleservice-assignments queue"
  value       = aws_sqs_queue.courseservice_to_scheduleservice_assignments.arn
}

output "courseservice_to_scheduleservice_courses_queue_url" {
  description = "URL of the courseservice-to-scheduleservice-courses queue"
  value       = aws_sqs_queue.courseservice_to_scheduleservice_courses.url
}

output "courseservice_to_scheduleservice_courses_queue_arn" {
  description = "ARN of the courseservice-to-scheduleservice-courses queue"
  value       = aws_sqs_queue.courseservice_to_scheduleservice_courses.arn
}
