#!/bin/bash

echo "========================================="
echo "Cognito User Pool 생성/확인 시작"
echo "========================================="

# AWS CLI endpoint 설정
REGION="ap-northeast-2"
POOL_NAME="unisync-user-pool"
CLIENT_NAME="unisync-app-client"
ENV_FILE="/workspace/.env"

# 기존 User Pool 확인
echo "기존 User Pool 확인 중..."

EXISTING_POOL_ID=$(awslocal cognito-idp list-user-pools \
  --max-results 10 \
  --region $REGION \
  --query "UserPools[?Name=='${POOL_NAME}'].Id | [0]" \
  --output text 2>/dev/null || echo "")

# AWS CLI가 결과 없을 때 "None" 반환 → 빈 문자열로 변환
if [ "$EXISTING_POOL_ID" = "None" ]; then
  EXISTING_POOL_ID=""
fi

if [ -n "$EXISTING_POOL_ID" ]; then
  echo "✓ 기존 User Pool 발견: $EXISTING_POOL_ID"
  USER_POOL_ID=$EXISTING_POOL_ID

  # 기존 Client ID 조회
  CLIENT_ID=$(awslocal cognito-idp list-user-pool-clients \
    --user-pool-id $USER_POOL_ID \
    --region $REGION \
    --query "UserPoolClients[?ClientName=='${CLIENT_NAME}'].ClientId | [0]" \
    --output text)

  # "None" 체크
  if [ "$CLIENT_ID" = "None" ]; then
    CLIENT_ID=""
  fi

  if [ -n "$CLIENT_ID" ]; then
    echo "✓ 기존 Client 발견: $CLIENT_ID"
  else
    echo "기존 Client가 없습니다. 새로 생성합니다..."
    # User Pool Client 생성
    CLIENT_ID=$(awslocal cognito-idp create-user-pool-client \
      --user-pool-id $USER_POOL_ID \
      --client-name $CLIENT_NAME \
      --region $REGION \
      --no-generate-secret \
      --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
      --query 'UserPoolClient.ClientId' \
      --output text)
    echo "✓ User Pool Client 생성 완료: $CLIENT_ID"
  fi
else
  echo "기존 User Pool이 없습니다. 새로 생성합니다..."

  # User Pool 생성
  USER_POOL_ID=$(awslocal cognito-idp create-user-pool \
    --pool-name $POOL_NAME \
    --region $REGION \
    --policies '{"PasswordPolicy":{"MinimumLength":8,"RequireUppercase":true,"RequireLowercase":true,"RequireNumbers":true,"RequireSymbols":false}}' \
    --auto-verified-attributes email \
    --username-attributes email \
    --schema Name=email,Required=true,Mutable=false Name=name,Required=true,Mutable=true \
    --mfa-configuration OFF \
    --query 'UserPool.Id' \
    --output text)

  echo "✓ User Pool 생성 완료: $USER_POOL_ID"

  # User Pool Client 생성
  CLIENT_ID=$(awslocal cognito-idp create-user-pool-client \
    --user-pool-id $USER_POOL_ID \
    --client-name $CLIENT_NAME \
    --region $REGION \
    --no-generate-secret \
    --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
    --query 'UserPoolClient.ClientId' \
    --output text)

  echo "✓ User Pool Client 생성 완료: $CLIENT_ID"
fi

# .env 파일 업데이트
echo ""
echo ".env 파일 업데이트 중..."

if [ -f "$ENV_FILE" ]; then
  # COGNITO_USER_POOL_ID 업데이트
  sed -i "s|^COGNITO_USER_POOL_ID=.*|COGNITO_USER_POOL_ID=$USER_POOL_ID|" $ENV_FILE
  # COGNITO_CLIENT_ID 업데이트
  sed -i "s|^COGNITO_CLIENT_ID=.*|COGNITO_CLIENT_ID=$CLIENT_ID|" $ENV_FILE

  echo "✓ .env 파일 업데이트 완료: $ENV_FILE"
else
  echo "⚠ .env 파일을 찾을 수 없습니다: $ENV_FILE"
  echo "다음 값을 .env 파일에 수동으로 추가하세요:"
  echo "COGNITO_USER_POOL_ID=$USER_POOL_ID"
  echo "COGNITO_CLIENT_ID=$CLIENT_ID"
fi

# 정보 출력
echo ""
echo "========================================="
echo "Cognito 설정 완료!"
echo "========================================="
echo "User Pool ID: $USER_POOL_ID"
echo "Client ID: $CLIENT_ID"
echo "Region: $REGION"
echo ""
echo "이제 애플리케이션을 시작할 수 있습니다:"
echo "  docker-compose -f docker-compose-app.yml up"
echo "========================================="