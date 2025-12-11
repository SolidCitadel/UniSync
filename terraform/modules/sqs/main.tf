# SQS Module - Queues with DLQ

# Dead Letter Queue
resource "aws_sqs_queue" "dlq" {
  name                      = "${var.project_name}-dlq-queue"
  message_retention_seconds = 1209600 # 14 days

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-dlq-queue"
    }
  )
}

# Lambda to Course Service Sync Queue
resource "aws_sqs_queue" "lambda_to_courseservice_sync" {
  name                      = "lambda-to-courseservice-sync"
  message_retention_seconds = 345600 # 4 days
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-lambda-to-courseservice-sync"
    }
  )
}

# Course Service to Schedule Service Assignments Queue
resource "aws_sqs_queue" "courseservice_to_scheduleservice_assignments" {
  name                      = "${var.project_name}-courseservice-to-scheduleservice-assignments"
  message_retention_seconds = 345600 # 4 days
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-courseservice-to-scheduleservice-assignments"
    }
  )
}

# Course Service to Schedule Service Course Events Queue
resource "aws_sqs_queue" "courseservice_to_scheduleservice_courses" {
  name                      = "${var.project_name}-courseservice-to-scheduleservice-courses"
  message_retention_seconds = 345600 # 4 days
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-courseservice-to-scheduleservice-courses"
    }
  )
}
