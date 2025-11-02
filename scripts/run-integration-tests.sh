#!/bin/bash

# Integration Test Runner
# E2E 통합 테스트 실행 스크립트

set -e

echo "🚀 UniSync Integration Tests"
echo "================================"

# 색상 정의
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Docker Compose로 테스트 환경 구축
echo ""
echo "${YELLOW}📦 Starting test environment...${NC}"
docker-compose -f docker-compose.test.yml down -v
docker-compose -f docker-compose.test.yml up -d

# 2. 서비스 준비 대기
echo ""
echo "${YELLOW}⏳ Waiting for services to be ready...${NC}"
sleep 10

# 3. 테스트 실행
echo ""
echo "${YELLOW}🧪 Running integration tests...${NC}"

if python -m pytest tests/integration/ -v --tb=short; then
    echo ""
    echo "${GREEN}✅ All tests passed!${NC}"
    TEST_RESULT=0
else
    echo ""
    echo "${RED}❌ Some tests failed${NC}"
    TEST_RESULT=1
fi

# 4. 로그 출력 (실패 시)
if [ $TEST_RESULT -ne 0 ]; then
    echo ""
    echo "${YELLOW}📋 Course-Service logs:${NC}"
    docker-compose -f docker-compose.test.yml logs course-service --tail=50
fi

# 5. 정리
echo ""
echo "${YELLOW}🧹 Cleaning up...${NC}"
docker-compose -f docker-compose.test.yml down -v

echo ""
if [ $TEST_RESULT -eq 0 ]; then
    echo "${GREEN}================================${NC}"
    echo "${GREEN}✨ Integration tests completed successfully!${NC}"
    echo "${GREEN}================================${NC}"
    exit 0
else
    echo "${RED}================================${NC}"
    echo "${RED}💥 Integration tests failed${NC}"
    echo "${RED}================================${NC}"
    exit 1
fi