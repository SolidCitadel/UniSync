#!/bin/bash

# LocalStack Lambda 자동 배포
# LocalStack 시작 시 자동으로 Lambda 함수 배포

set -e

# 필수 환경변수 검증
AWS_REGION=${AWS_REGION:?ERROR: AWS_REGION environment variable must be set}
CANVAS_API_BASE_URL=${CANVAS_API_BASE_URL:?ERROR: CANVAS_API_BASE_URL environment variable must be set}
USER_SERVICE_URL=${USER_SERVICE_URL:?ERROR: USER_SERVICE_URL environment variable must be set}
COURSE_SERVICE_URL=${COURSE_SERVICE_URL:?ERROR: COURSE_SERVICE_URL environment variable must be set}
CANVAS_SYNC_API_KEY=${CANVAS_SYNC_API_KEY:?ERROR: CANVAS_SYNC_API_KEY environment variable must be set}
AWS_SQS_ENDPOINT=${AWS_SQS_ENDPOINT:-}

echo "🚀 Deploying Lambda functions to LocalStack..."
echo "  - Region: ${AWS_REGION}"
echo "  - Canvas API: ${CANVAS_API_BASE_URL}"
echo "  - User Service URL: ${USER_SERVICE_URL}"
echo "  - Course Service URL: ${COURSE_SERVICE_URL}"

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
LAMBDA_DIR="/var/lib/localstack/serverless/canvas-sync-lambda"

if [ -d "$LAMBDA_DIR" ]; then
  cd $LAMBDA_DIR

  # 의존성 설치 및 패키징
  pip install -r requirements.txt -t /tmp/lambda-package/ --quiet
  cp src/handler.py /tmp/lambda-package/

  # ZIP 생성
  cd /tmp/lambda-package
  zip -r /tmp/canvas-lambda.zip . -q

  # canvas-sync-lambda 생성
  awslocal lambda create-function \
    --region ${AWS_REGION} \
    --function-name canvas-sync-lambda \
    --runtime python3.11 \
    --handler handler.lambda_handler \
    --zip-file fileb:///tmp/canvas-lambda.zip \
    --role arn:aws:iam::000000000000:role/lambda-execution-role \
    --timeout 120 \
    --memory-size 256 \
    && echo "  ✅ canvas-sync-lambda created" \
    || (awslocal lambda update-function-code \
        --region ${AWS_REGION} \
        --function-name canvas-sync-lambda \
        --zip-file fileb:///tmp/canvas-lambda.zip \
        && echo "  ✅ canvas-sync-lambda updated")

  # 환경 변수 구성 (JSON)
  ENV_VARS="Variables={CANVAS_API_BASE_URL=${CANVAS_API_BASE_URL},AWS_REGION=${AWS_REGION},USER_SERVICE_URL=${USER_SERVICE_URL},COURSE_SERVICE_URL=${COURSE_SERVICE_URL},CANVAS_SYNC_API_KEY=${CANVAS_SYNC_API_KEY}"
  if [ -n "${AWS_SQS_ENDPOINT}" ]; then
    ENV_VARS="${ENV_VARS},SQS_ENDPOINT=${AWS_SQS_ENDPOINT}"
  fi
  ENV_VARS="${ENV_VARS}}"

  awslocal lambda update-function-configuration \
    --region ${AWS_REGION} \
    --function-name canvas-sync-lambda \
    --environment "${ENV_VARS}" \
    >/dev/null \
    && echo "  ✅ canvas-sync-lambda environment updated"

  # 정리
  rm -rf /tmp/lambda-package /tmp/canvas-lambda.zip
else
  echo "  ⚠️ Lambda source not found at $LAMBDA_DIR"
fi

# 3. llm-lambda 배포 (선택사항)
echo "📦 Deploying llm-lambda..."

LLM_LAMBDA_DIR="/var/lib/localstack/serverless/llm-lambda"

if [ -d "$LLM_LAMBDA_DIR" ]; then
  cd $LLM_LAMBDA_DIR

  pip install -r requirements.txt -t /tmp/llm-package/ --quiet
  cp src/handler.py /tmp/llm-package/

  cd /tmp/llm-package
  zip -r /tmp/llm-lambda.zip . -q

  awslocal lambda create-function \
    --region ${AWS_REGION} \
    --function-name llm-lambda \
    --runtime python3.11 \
    --handler handler.lambda_handler \
    --zip-file fileb:///tmp/llm-lambda.zip \
    --role arn:aws:iam::000000000000:role/lambda-execution-role \
    --timeout 60 \
    --memory-size 512 \
    --environment Variables="{
      AWS_REGION=${AWS_REGION}
    }" 2>/dev/null \
    && echo "  ✅ llm-lambda created" \
    || (awslocal lambda update-function-code \
        --region ${AWS_REGION} \
        --function-name llm-lambda \
        --zip-file fileb:///tmp/llm-lambda.zip \
        && echo "  ✅ llm-lambda updated")

  rm -rf /tmp/llm-package /tmp/llm-lambda.zip
else
  echo "  ⚠️ LLM Lambda source not found"
fi

echo ""
echo "✅ Lambda deployment completed!"
echo ""
echo "🔍 Verifying deployment..."

# Lambda 함수 목록 확인
LAMBDA_COUNT=$(awslocal lambda list-functions --region ${AWS_REGION} --query 'Functions[].FunctionName' --output text | wc -w)
echo "  - Lambda functions deployed: ${LAMBDA_COUNT}"

# SQS 큐 목록 확인
QUEUE_COUNT=$(awslocal sqs list-queues --region ${AWS_REGION} --query 'QueueUrls' --output text | wc -w)
echo "  - SQS queues available: ${QUEUE_COUNT}"

if [ ${LAMBDA_COUNT} -ge 2 ] && [ ${QUEUE_COUNT} -ge 4 ]; then
  echo ""
  echo "✅ All validations passed!"
  echo "   Deployed Lambdas: canvas-sync-lambda, llm-lambda"
else
  echo ""
  echo "⚠️ Some validations failed. Please check the logs above."
  echo "    Expected: Lambda >= 2, Queues >= 4"
  echo "    Actual: Lambda = ${LAMBDA_COUNT}, Queues = ${QUEUE_COUNT}"
fi
