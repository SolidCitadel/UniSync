# Lambda Module - Canvas Sync Lambda with VPC

# Prepare Lambda package: install dependencies into src/ and zip src directory
resource "null_resource" "canvas_sync_lambda_dependencies" {
  # 재실행 기준: requirements.txt 내용이 바뀔 때
  triggers = {
    requirements_hash = filesha1("${path.module}/../../../app/serverless/canvas-sync-lambda/requirements.txt")
  }

  provisioner "local-exec" {
    working_dir = "${path.module}/../../../app/serverless/canvas-sync-lambda/src"
    # requirements.txt는 상위 디렉터리에 있으므로 상대 경로로 지정
    command     = "pip install -r ../requirements.txt -t ."
  }
}

# Data source for Lambda code archive (after dependencies installation)
data "archive_file" "canvas_sync_lambda" {
  depends_on = [null_resource.canvas_sync_lambda_dependencies]

  type        = "zip"
  source_dir  = "${path.module}/../../../app/serverless/canvas-sync-lambda/src"
  output_path = "${path.module}/canvas-sync-lambda.zip"
  excludes    = ["__pycache__", "*.pyc", "tests", "conftest.py"]
}

# IAM Role for Lambda Execution
resource "aws_iam_role" "lambda_execution" {
  name = "${var.project_name}-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-lambda-execution-role"
    }
  )
}

# IAM Policy for Lambda Basic Execution
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# IAM Policy for VPC Access
resource "aws_iam_role_policy_attachment" "lambda_vpc_execution" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# IAM Policy for SQS Access
resource "aws_iam_role_policy" "lambda_sqs" {
  name = "${var.project_name}-lambda-sqs-policy"
  role = aws_iam_role.lambda_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl"
        ]
        Resource = [
          var.sqs_queue_arns.lambda_to_courseservice_sync
        ]
      }
    ]
  })
}

# IAM Policy for Secrets Manager Access
resource "aws_iam_role_policy" "lambda_secrets" {
  name = "${var.project_name}-lambda-secrets-policy"
  role = aws_iam_role.lambda_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          var.canvas_sync_api_key_secret_arn
        ]
      }
    ]
  })
}

# Lambda Function - Canvas Sync
resource "aws_lambda_function" "canvas_sync" {
  filename         = data.archive_file.canvas_sync_lambda.output_path
  function_name    = "${var.project_name}-canvas-sync-lambda"
  role            = aws_iam_role.lambda_execution.arn
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

  depends_on = [
    aws_iam_role_policy_attachment.lambda_vpc_execution
  ]
}

