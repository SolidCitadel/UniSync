#!/bin/bash
# Lambda function deployment script (LocalStack / AWS)

set -e

# Set environment variable (default: local)
ENVIRONMENT=${1:-local}

if [ "$ENVIRONMENT" = "local" ]; then
  echo "Deploying to LocalStack..."
  export AWS_ENDPOINT_URL=http://localhost:4566
  alias awslocal="aws --endpoint-url=http://localhost:4566"
  AWS_CMD="awslocal"
else
  echo "Deploying to AWS..."
  AWS_CMD="aws"
fi

# 1. Deploy Canvas Sync Lambda
echo ""
echo "Packaging Canvas Sync Lambda..."
cd app/serverless/canvas-sync-lambda

# Install dependencies and package
pip install -r requirements.txt -t package/
cp src/handler.py package/
cd package && zip -r ../function.zip . && cd ..
rm -rf package

echo "Deploying Canvas Sync Lambda..."
$AWS_CMD lambda create-function \
  --function-name canvas-sync-lambda \
  --runtime python3.11 \
  --role arn:aws:iam::000000000000:role/lambda-execution-role \
  --handler handler.lambda_handler \
  --zip-file fileb://function.zip \
  --timeout 30 \
  --memory-size 512 \
  --environment "Variables={
    USER_SERVICE_URL=http://host.docker.internal:8081,
    CANVAS_API_BASE_URL=https://canvas.instructure.com/api/v1
  }" || echo "Canvas Sync Lambda already exists, updating..."

# Update if function already exists
$AWS_CMD lambda update-function-code \
  --function-name canvas-sync-lambda \
  --zip-file fileb://function.zip || true

cd ../../..

# 2. Deploy LLM Lambda
echo ""
echo "Packaging LLM Lambda..."
cd app/serverless/llm-lambda

pip install -r requirements.txt -t package/
cp src/handler.py package/
cd package && zip -r ../function.zip . && cd ..
rm -rf package

echo "Deploying LLM Lambda..."
$AWS_CMD lambda create-function \
  --function-name llm-lambda \
  --runtime python3.11 \
  --role arn:aws:iam::000000000000:role/lambda-execution-role \
  --handler handler.lambda_handler \
  --zip-file fileb://function.zip \
  --timeout 30 \
  --memory-size 512 || echo "LLM Lambda already exists, updating..."

$AWS_CMD lambda update-function-code \
  --function-name llm-lambda \
  --zip-file fileb://function.zip || true

cd ../../..

echo ""
echo "Lambda deployment completed!"
echo ""
echo "Deployed Lambda functions:"
$AWS_CMD lambda list-functions --query 'Functions[?starts_with(FunctionName, `canvas`) || starts_with(FunctionName, `llm`)].FunctionName'