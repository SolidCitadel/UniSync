#!/bin/bash
# LocalStack 초기화 스크립트
# SQS 큐, Step Functions, Lambda 함수 생성

set -e

echo "🚀 LocalStack 초기화 시작..."

# LocalStack 엔드포인트 설정
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=ap-northeast-2

# AWS CLI alias 설정 (localstack 사용)
alias awslocal="aws --endpoint-url=http://localhost:4566"

echo "📦 SQS 큐 생성 중..."

# 1. assignment-events-queue
awslocal sqs create-queue --queue-name assignment-events-queue || echo "assignment-events-queue already exists"

# 2. submission-events-queue
awslocal sqs create-queue --queue-name submission-events-queue || echo "submission-events-queue already exists"

# 3. task-creation-queue
awslocal sqs create-queue --queue-name task-creation-queue || echo "task-creation-queue already exists"

# 4. external-sync-queue
awslocal sqs create-queue --queue-name external-sync-queue || echo "external-sync-queue already exists"

echo "✅ SQS 큐 생성 완료"

echo "🔧 IAM 역할 생성 중..."

# Lambda 실행 역할 생성
awslocal iam create-role \
  --role-name lambda-execution-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "lambda.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }' || echo "lambda-execution-role already exists"

# Step Functions 실행 역할 생성
awslocal iam create-role \
  --role-name stepfunctions-execution-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "states.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }' || echo "stepfunctions-execution-role already exists"

echo "✅ IAM 역할 생성 완료"

echo "📋 생성된 리소스 확인:"
echo ""
echo "SQS 큐 목록:"
awslocal sqs list-queues

echo ""
echo "✨ LocalStack 초기화 완료!"
echo ""
echo "다음 명령어로 Lambda 배포:"
echo "  bash scripts/deploy-lambda.sh"