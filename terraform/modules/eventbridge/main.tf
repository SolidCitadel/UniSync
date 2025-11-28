# EventBridge Module - Scheduled Rule for Canvas Sync Lambda

# EventBridge Rule - Run every hour
resource "aws_cloudwatch_event_rule" "canvas_sync_schedule" {
  name                = "${var.project_name}-canvas-sync-schedule"
  description         = "Trigger Canvas Sync Lambda every hour"
  schedule_expression = "rate(1 hour)"

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-canvas-sync-schedule"
    }
  )
}

# EventBridge Target - Lambda Function
resource "aws_cloudwatch_event_target" "canvas_sync_lambda" {
  rule      = aws_cloudwatch_event_rule.canvas_sync_schedule.name
  target_id = "${var.project_name}-canvas-sync-lambda-target"
  arn       = var.canvas_sync_lambda_arn
}

# Lambda Permission for EventBridge
resource "aws_lambda_permission" "allow_eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = var.canvas_sync_lambda_function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.canvas_sync_schedule.arn
}

