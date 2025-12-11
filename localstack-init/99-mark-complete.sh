#!/bin/bash

echo "========================================="
echo "LocalStack 초기화 완료 마커 생성"
echo "========================================="

# 초기화 완료 마커 파일 생성
touch /tmp/localstack-init-complete

echo "✓ 초기화 완료 마커 생성됨: /tmp/localstack-init-complete"
echo ""
echo "이제 Spring Boot 서비스들이 시작될 수 있습니다."
echo "========================================="
