#!/bin/bash
# Docker 이미지 빌드 및 Push 스크립트
# Usage:
#   ./scripts/docker-push.sh              # 빌드 후 푸시
#   ./scripts/docker-push.sh --tag-only   # 기존 이미지에 태그만 붙여서 푸시
#
# 모든 백엔드 서비스를 빌드하고 solicditadel/unisync-서비스명:dev 형식으로 Docker Hub에 푸시합니다.

set -e  # 에러 발생 시 스크립트 종료

# 색상 정의
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Docker Hub 사용자명
DOCKER_USERNAME="solidcitadel"
TAG="dev"
SOURCE_TAG="latest"

# 옵션 파싱
TAG_ONLY=false
if [[ "$1" == "--tag-only" ]]; then
  TAG_ONLY=true
  echo -e "${YELLOW}⚡ 태그 전용 모드: 기존 이미지에 태그만 붙여서 푸시합니다.${NC}"
fi

# 프로젝트 루트로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Docker 이미지 빌드 및 Push${NC}"
echo -e "${BLUE}========================================${NC}"

# 서비스 목록 정의
SERVICES=(
  "api-gateway"
  "user-service"
  "course-service"
  "schedule-service"
)

# Docker 로그인 확인 (config.json 파일 체크)
echo -e "${YELLOW}Docker Hub 로그인 상태 확인 중...${NC}"
if [ ! -f "$HOME/.docker/config.json" ] || ! grep -q "auths" "$HOME/.docker/config.json" 2>/dev/null; then
  echo -e "${RED}Docker Hub에 로그인되어 있지 않습니다.${NC}"
  echo -e "${YELLOW}docker login 명령어로 먼저 로그인하세요.${NC}"
  exit 1
fi
echo -e "${GREEN}✅ Docker Hub 로그인 확인됨${NC}"

# 각 서비스별로 빌드 및 푸시
for SERVICE in "${SERVICES[@]}"; do
  TARGET_IMAGE="${DOCKER_USERNAME}/unisync-${SERVICE}:${TAG}"
  SOURCE_IMAGE="unisync-${SERVICE}:${SOURCE_TAG}"
  DOCKERFILE_PATH="app/backend/${SERVICE}/Dockerfile"

  echo ""
  echo -e "${GREEN}========================================${NC}"
  echo -e "${GREEN}처리 중: ${SERVICE}${NC}"
  echo -e "${GREEN}========================================${NC}"

  if [ "$TAG_ONLY" = true ]; then
    # 태그 전용 모드: 기존 이미지 확인 후 태그만 붙임
    if ! docker image inspect "$SOURCE_IMAGE" &> /dev/null; then
      echo -e "${RED}❌ 소스 이미지를 찾을 수 없습니다: ${SOURCE_IMAGE}${NC}"
      echo -e "${YELLOW}   먼저 이미지를 빌드하거나 --tag-only 옵션 없이 실행하세요.${NC}"
      exit 1
    fi

    echo -e "${BLUE}🏷️  태그 추가 중: ${SOURCE_IMAGE} → ${TARGET_IMAGE}${NC}"
    docker tag "$SOURCE_IMAGE" "$TARGET_IMAGE"

    if [ $? -eq 0 ]; then
      echo -e "${GREEN}✅ 태그 추가 성공${NC}"
    else
      echo -e "${RED}❌ 태그 추가 실패: ${SERVICE}${NC}"
      exit 1
    fi
  else
    # 일반 모드: 빌드 후 태그
    # Dockerfile 존재 확인
    if [ ! -f "$DOCKERFILE_PATH" ]; then
      echo -e "${RED}❌ Dockerfile을 찾을 수 없습니다: $DOCKERFILE_PATH${NC}"
      continue
    fi

    # 이미지 빌드
    echo -e "${BLUE}📦 이미지 빌드 중: ${TARGET_IMAGE}${NC}"
    docker build \
      -t "$TARGET_IMAGE" \
      -f "$DOCKERFILE_PATH" \
      ./app

    if [ $? -eq 0 ]; then
      echo -e "${GREEN}✅ 빌드 성공: ${TARGET_IMAGE}${NC}"
    else
      echo -e "${RED}❌ 빌드 실패: ${SERVICE}${NC}"
      exit 1
    fi
  fi

  # 이미지 푸시
  echo -e "${BLUE}⬆️  이미지 푸시 중: ${TARGET_IMAGE}${NC}"
  docker push "$TARGET_IMAGE"

  if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 푸시 성공: ${TARGET_IMAGE}${NC}"
  else
    echo -e "${RED}❌ 푸시 실패: ${SERVICE}${NC}"
    exit 1
  fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 모든 이미지 빌드 및 푸시 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}푸시된 이미지:${NC}"
for SERVICE in "${SERVICES[@]}"; do
  echo -e "  - ${DOCKER_USERNAME}/unisync-${SERVICE}:${TAG}"
done
