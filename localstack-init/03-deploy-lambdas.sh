#!/bin/bash

# LocalStack Lambda 자동 배포
# LocalStack 시작 시 자동으로 Lambda 함수 배포

set -e

echo "🚀 Deploying Lambda functions to LocalStack..."

# 1. IAM Role 생성 (Lambda 실행용)
echo "📝 Creating IAM role..."
awslocal iam create-role \
  --role-name lambda-execution-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "lambda.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }' 2>/dev/null || echo "  Role already exists"

# 2. canvas-sync-lambda 배포
echo "📦 Deploying canvas-sync-lambda..."

# Lambda 소스 위치
LAMBDA_DIR="/etc/localstack/init/ready.d/../../app/serverless/canvas-sync-lambda"

if [ -d "$LAMBDA_DIR" ]; then
  cd $LAMBDA_DIR

  # 의존성 설치 및 패키징
  pip install -r requirements.txt -t /tmp/lambda-package/ --quiet
  cp src/handler.py /tmp/lambda-package/

  # ZIP 생성
  cd /tmp/lambda-package
  zip -r /tmp/canvas-sync-lambda.zip . -q

  # Lambda 생성 또는 업데이트
  awslocal lambda create-function \
    --function-name canvas-sync-lambda \
    --runtime python3.11 \
    --handler handler.lambda_handler \
    --zip-file fileb:///tmp/canvas-sync-lambda.zip \
    --role arn:aws:iam::000000000000:role/lambda-execution-role \
    --timeout 30 \
    --memory-size 256 \
    --environment Variables="{
      CANVAS_API_BASE_URL=${CANVAS_API_BASE_URL:-https://canvas.instructure.com/api/v1},
      AWS_REGION=us-east-1,
      SQS_ENDPOINT=http://localhost:4566,
      USER_SERVICE_URL=http://user-service:8081
    }" 2>/dev/null \
    && echo "  ✅ canvas-sync-lambda created" \
    || (awslocal lambda update-function-code \
        --function-name canvas-sync-lambda \
        --zip-file fileb:///tmp/canvas-sync-lambda.zip \
        && echo "  ✅ canvas-sync-lambda updated")

  # 정리
  rm -rf /tmp/lambda-package /tmp/canvas-sync-lambda.zip
else
  echo "  ⚠️ Lambda source not found at $LAMBDA_DIR"
fi

# 3. llm-lambda 배포 (선택사항)
echo "📦 Deploying llm-lambda..."

LLM_LAMBDA_DIR="/etc/localstack/init/ready.d/../../app/serverless/llm-lambda"

if [ -d "$LLM_LAMBDA_DIR" ]; then
  cd $LLM_LAMBDA_DIR

  pip install -r requirements.txt -t /tmp/llm-package/ --quiet
  cp src/handler.py /tmp/llm-package/

  cd /tmp/llm-package
  zip -r /tmp/llm-lambda.zip . -q

  awslocal lambda create-function \
    --function-name llm-lambda \
    --runtime python3.11 \
    --handler handler.lambda_handler \
    --zip-file fileb:///tmp/llm-lambda.zip \
    --role arn:aws:iam::000000000000:role/lambda-execution-role \
    --timeout 60 \
    --memory-size 512 \
    --environment Variables="{
      AWS_REGION=us-east-1,
      SQS_ENDPOINT=http://localhost:4566
    }" 2>/dev/null \
    && echo "  ✅ llm-lambda created" \
    || (awslocal lambda update-function-code \
        --function-name llm-lambda \
        --zip-file fileb:///tmp/llm-lambda.zip \
        && echo "  ✅ llm-lambda updated")

  rm -rf /tmp/llm-package /tmp/llm-lambda.zip
else
  echo "  ⚠️ LLM Lambda source not found"
fi

echo "✅ Lambda deployment completed!"