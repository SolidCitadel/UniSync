#!/bin/sh
# LocalStack 초기화 완료 대기 스크립트
# docker-compose.acceptance.yml에서 entrypoint로 사용

echo "Waiting for LocalStack Cognito initialization..."

max_attempts=60
attempt=0
env_local_file="/workspace/.env.local"

while [ $attempt -lt $max_attempts ]; do
    # Cognito가 실행 중인지 확인
    if wget -q -O- http://localstack:4566/_localstack/health 2>/dev/null | grep -q '"cognito-idp": "running"'; then
        # .env.local 파일이 업데이트되었는지 확인
        if [ -f "$env_local_file" ]; then
            # .env.local에서 COGNITO_* 값 읽기
            new_pool_id=$(grep "^COGNITO_USER_POOL_ID=" "$env_local_file" | cut -d '=' -f 2)
            new_client_id=$(grep "^COGNITO_CLIENT_ID=" "$env_local_file" | cut -d '=' -f 2)

            # 유효한 값인지 확인 (xxxxx가 아닌 실제 값)
            if [ -n "$new_pool_id" ] && [ "$new_pool_id" != "xxxxx" ] && \
               [ -n "$new_client_id" ] && [ "$new_client_id" != "xxxxx" ]; then
                echo "✓ LocalStack Cognito ready"
                echo "  Reading from: $env_local_file"
                echo "  User Pool ID: $new_pool_id"
                echo "  Client ID: $new_client_id"

                # 환경변수로 export (Java 프로세스가 사용)
                export COGNITO_USER_POOL_ID="$new_pool_id"
                export COGNITO_CLIENT_ID="$new_client_id"
                break
            fi
        fi
    fi

    attempt=$((attempt + 1))
    echo "  Waiting for LocalStack Cognito... ($attempt/$max_attempts)"
    sleep 2
done

if [ $attempt -eq $max_attempts ]; then
    echo "⚠️  Warning: LocalStack timeout, but continuing with existing env vars..."
fi

# Spring Boot 실행
exec "$@"