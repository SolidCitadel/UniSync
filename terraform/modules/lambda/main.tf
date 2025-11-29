# Lambda Module - Canvas Sync Lambda with VPC

# Data source for Lambda code archive
data "archive_file" "canvas_sync_lambda" {
  type        = "zip"
  source_dir  = "${path.module}/../../../app/serverless/canvas-sync-lambda"
  output_path = "${path.module}/canvas-sync-lambda.zip"
  excludes    = ["__pycache__", "*.pyc", "tests", "conftest.py"]
}

# IAM Role for Lambda Execution
# Note: IAM Role 생성 권한이 없어서 기존 LabRole 사용
# LabRole에 다음 권한이 필요:
# - AWSLambdaBasicExecutionRole (CloudWatch Logs)
# - AWSLambdaVPCAccessExecutionRole (VPC 접근)
# - SQS SendMessage 권한
# - Secrets Manager GetSecretValue 권한
#
# resource "aws_iam_role" "lambda_execution" {
#   name = "${var.project_name}-lambda-execution-role"
#   ...
# }
#
# resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
#   ...
# }
#
# resource "aws_iam_role_policy_attachment" "lambda_vpc_execution" {
#   ...
# }
#
# resource "aws_iam_role_policy" "lambda_sqs" {
#   ...
# }
#
# resource "aws_iam_role_policy" "lambda_secrets" {
#   ...
# }

# Lambda Function - Canvas Sync
resource "aws_lambda_function" "canvas_sync" {
  filename         = data.archive_file.canvas_sync_lambda.output_path
  function_name    = "${var.project_name}-canvas-sync-lambda"
  role            = var.lambda_execution_role_arn
  handler         = "handler.lambda_handler"
  runtime         = "python3.11"
  timeout         = 120
  memory_size     = 256

  source_code_hash = data.archive_file.canvas_sync_lambda.output_base64sha256

  # VPC Configuration for NAT Gateway access
  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }

  environment {
    variables = {
      # AWS_REGION은 Lambda 예약 변수이므로 제거 (Lambda가 자동 제공)
      USER_SERVICE_URL              = var.user_service_url
      CANVAS_API_BASE_URL           = var.canvas_api_base_url
      CANVAS_SYNC_API_KEY_SECRET_ARN = var.canvas_sync_api_key_secret_arn
      SQS_QUEUE_URL                 = var.sqs_queue_urls.lambda_to_courseservice_sync
    }
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-canvas-sync-lambda"
    }
  )

  # LabRole 사용 시 depends_on 제거 (IAM Role이 Terraform 밖에서 관리됨)
  # depends_on = [
  #   aws_iam_role_policy_attachment.lambda_vpc_execution
  # ]
}

