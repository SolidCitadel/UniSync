#!/bin/bash

echo "========================================="
echo "LocalStack 초기화 시작"
echo "========================================="

# AWS CLI endpoint 설정
ENDPOINT="http://localhost:4566"
REGION="ap-northeast-2"

# SQS 큐 생성
echo "SQS 큐 생성 중..."

# assignment-events-queue: 새 과제 감지 이벤트
awslocal sqs create-queue \
  --queue-name assignment-events-queue \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# submission-events-queue: 제출물 감지 이벤트
awslocal sqs create-queue \
  --queue-name submission-events-queue \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# task-creation-queue: LLM 분석 후 Task 생성 이벤트
awslocal sqs create-queue \
  --queue-name task-creation-queue \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# DLQ (Dead Letter Queue) 생성
awslocal sqs create-queue \
  --queue-name dlq-queue \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=1209600

echo "SQS 큐 생성 완료"

# 생성된 큐 목록 출력
echo ""
echo "생성된 SQS 큐 목록:"
awslocal sqs list-queues --region $REGION

# S3 버킷 생성 (과제 첨부파일 등 저장용)
echo ""
echo "S3 버킷 생성 중..."
awslocal s3 mb s3://unisync-attachments --region $REGION
awslocal s3 mb s3://unisync-lambda-code --region $REGION

echo "S3 버킷 생성 완료"
awslocal s3 ls

echo ""
echo "========================================="
echo "LocalStack 초기화 완료!"
echo "========================================="
