#!/bin/bash

echo "========================================="
echo "Cognito User Pool 생성 시작"
echo "========================================="

# AWS CLI endpoint 설정
ENDPOINT="http://localhost:4566"
REGION="ap-northeast-2"

# User Pool 생성
echo "Cognito User Pool 생성 중..."

USER_POOL_ID=$(awslocal cognito-idp create-user-pool \
  --pool-name unisync-user-pool \
  --region $REGION \
  --policies '{"PasswordPolicy":{"MinimumLength":8,"RequireUppercase":true,"RequireLowercase":true,"RequireNumbers":true,"RequireSymbols":false}}' \
  --auto-verified-attributes email \
  --username-attributes email \
  --schema Name=email,Required=true,Mutable=false Name=name,Required=true,Mutable=true \
  --mfa-configuration OFF \
  --query 'UserPool.Id' \
  --output text)

echo "User Pool 생성 완료: $USER_POOL_ID"

# User Pool Client 생성
echo "User Pool Client 생성 중..."

CLIENT_ID=$(awslocal cognito-idp create-user-pool-client \
  --user-pool-id $USER_POOL_ID \
  --client-name unisync-app-client \
  --region $REGION \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --query 'UserPoolClient.ClientId' \
  --output text)

echo "User Pool Client 생성 완료: $CLIENT_ID"

# 환경 변수 파일 생성 (애플리케이션에서 사용)
echo "환경 변수 파일 생성 중..."

cat > /tmp/localstack/cognito-config.env << EOF
# Cognito Configuration (LocalStack)
COGNITO_USER_POOL_ID=$USER_POOL_ID
COGNITO_CLIENT_ID=$CLIENT_ID
COGNITO_REGION=$REGION
COGNITO_ENDPOINT=$ENDPOINT
EOF

echo "환경 변수 파일 생성 완료: /tmp/localstack/cognito-config.env"

# 정보 출력
echo ""
echo "========================================="
echo "Cognito User Pool 생성 완료!"
echo "========================================="
echo "User Pool ID: $USER_POOL_ID"
echo "Client ID: $CLIENT_ID"
echo "Region: $REGION"
echo ""
echo "다음 명령어로 User Pool 정보 확인:"
echo "  awslocal cognito-idp describe-user-pool --user-pool-id $USER_POOL_ID --region $REGION"
echo "========================================="
