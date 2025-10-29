#!/bin/bash
# SQS 큐 생성 및 관리 스크립트

set -e

ENVIRONMENT=${1:-local}

if [ "$ENVIRONMENT" = "local" ]; then
  AWS_CMD="aws --endpoint-url=http://localhost:4566"
else
  AWS_CMD="aws"
fi

echo "📦 SQS 큐 생성 중..."

QUEUES=(
  "assignment-events-queue"
  "submission-events-queue"
  "task-creation-queue"
  "external-sync-queue"
)

for queue in "${QUEUES[@]}"; do
  echo "  - $queue"
  $AWS_CMD sqs create-queue --queue-name "$queue" 2>/dev/null || echo "    (이미 존재함)"
done

echo ""
echo "✅ SQS 큐 생성 완료"
echo ""
echo "큐 URL 목록:"
$AWS_CMD sqs list-queues