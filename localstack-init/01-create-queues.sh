#!/bin/bash

echo "========================================="
echo "LocalStack 초기화 시작"
echo "========================================="

# AWS CLI endpoint 설정
ENDPOINT="http://localhost:4566"
REGION="ap-northeast-2"

# SQS 큐 생성
echo "SQS 큐 생성 중..."

# Phase 1: Manual Sync (필요한 큐만 생성)

# lambda-to-courseservice-enrollments: Lambda → Course-Service (Course 데이터 전달)
awslocal sqs create-queue \
  --queue-name lambda-to-courseservice-enrollments \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# lambda-to-courseservice-assignments: Lambda → Course-Service (Assignment 데이터 전달)
awslocal sqs create-queue \
  --queue-name lambda-to-courseservice-assignments \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# courseservice-to-scheduleservice-assignments: Course-Service → Schedule-Service (Assignment → Schedule 변환)
awslocal sqs create-queue \
  --queue-name courseservice-to-scheduleservice-assignments \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# DLQ (Dead Letter Queue)
awslocal sqs create-queue \
  --queue-name dlq-queue \
  --region $REGION \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=1209600

echo "SQS 큐 생성 완료 (Phase 1: 4개)"

# Phase 2/3: 향후 추가 예정
# - submission-events-queue (제출물 감지)
# - task-creation-queue (LLM 기반 Task 생성)

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
