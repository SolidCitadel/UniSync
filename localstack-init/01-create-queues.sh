#!/bin/bash

echo "========================================="
echo "SQS Queue Init Start"
echo "========================================="

ENDPOINT="http://localhost:4566"
REGION="ap-northeast-2"

# 1. DLQ 생성
echo "Creating DLQ..."
awslocal sqs create-queue \
  --queue-name dlq-queue \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=1209600

# 2. Main Queues 생성
echo "Creating Main Queues..."

# 하드코딩된 Attributes JSON (이스케이핑 포함)
# RedrivePolicy 값은 JSON 문자열이어야 하므로 내부 따옴표를 \" 로 이스케이프해야 함.
# 전체를 홑따옴표(')로 감싸서 쉘의 변수 확장을 방지하고 문자열 그대로 전달.
ATTRIBUTES='{"VisibilityTimeout":"30","MessageRetentionPeriod":"345600","RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:ap-northeast-2:000000000000:dlq-queue\",\"maxReceiveCount\":\"3\"}"}'

# Lambda → Course-Service: 통합 동기화 메시지 (단일 메시지에 모든 course + assignments 포함)
awslocal sqs create-queue \
  --queue-name lambda-to-courseservice-sync \
  --region $REGION \
  --attributes "$ATTRIBUTES"

# Course-Service → Schedule-Service: Assignment 이벤트
awslocal sqs create-queue \
  --queue-name courseservice-to-scheduleservice-assignments \
  --region $REGION \
  --attributes "$ATTRIBUTES"

# 3. S3 버킷 생성
echo "Creating S3 Buckets..."
awslocal s3 mb s3://unisync-attachments --region $REGION
awslocal s3 mb s3://unisync-lambda-code --region $REGION

# 4. 확인
echo "Listing Queues:"
awslocal sqs list-queues --region $REGION

echo "========================================="
echo "SQS Queue Init Complete"
echo "========================================="
