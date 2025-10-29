#!/bin/bash
# Lambda 함수 배포 스크립트 (LocalStack / AWS)

set -e

# 환경 변수 설정 (기본값: local)
ENVIRONMENT=${1:-local}

if [ "$ENVIRONMENT" = "local" ]; then
  echo "🔧 LocalStack에 배포합니다..."
  export AWS_ENDPOINT_URL=http://localhost:4566
  alias awslocal="aws --endpoint-url=http://localhost:4566"
  AWS_CMD="awslocal"
else
  echo "☁️  AWS에 배포합니다..."
  AWS_CMD="aws"
fi

# 1. Canvas Sync Lambda 배포
echo ""
echo "📦 Canvas Sync Lambda 패키징 중..."
cd app/serverless/canvas-sync-lambda

# 의존성 설치 및 패키징
pip install -r requirements.txt -t package/
cp src/handler.py package/
cd package && zip -r ../function.zip . && cd ..
rm -rf package

echo "🚀 Canvas Sync Lambda 배포 중..."
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

# 함수가 이미 존재하면 업데이트
$AWS_CMD lambda update-function-code \
  --function-name canvas-sync-lambda \
  --zip-file fileb://function.zip || true

cd ../../..

# 2. LLM Lambda 배포
echo ""
echo "📦 LLM Lambda 패키징 중..."
cd app/serverless/llm-lambda

pip install -r requirements.txt -t package/
cp src/handler.py package/
cd package && zip -r ../function.zip . && cd ..
rm -rf package

echo "🚀 LLM Lambda 배포 중..."
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
echo "✅ Lambda 배포 완료!"
echo ""
echo "배포된 Lambda 함수:"
$AWS_CMD lambda list-functions --query 'Functions[?starts_with(FunctionName, `canvas`) || starts_with(FunctionName, `llm`)].FunctionName'