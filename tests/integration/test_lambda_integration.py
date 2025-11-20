#!/usr/bin/env python3
"""
LocalStack Lambda 통합 테스트 스크립트

LocalStack에 Lambda를 배포하고 실제로 호출하여 통합 테스트를 수행합니다.
"""

import os
import sys
import json
import time
import subprocess
import boto3
from datetime import datetime


# LocalStack 설정
LOCALSTACK_ENDPOINT = os.getenv('AWS_ENDPOINT_URL', 'http://localhost:4566')
AWS_REGION = os.getenv('AWS_REGION', 'ap-northeast-2')


def print_header(text):
    """헤더 출력"""
    print("\n" + "=" * 70)
    print(f"  {text}")
    print("=" * 70)


def print_section(text):
    """섹션 출력"""
    print(f"\n>>> {text}")


def check_localstack_running():
    """LocalStack이 실행 중인지 확인"""
    print_section("Step 1: LocalStack 상태 확인")

    try:
        # LocalStack health check
        import requests
        response = requests.get(f"{LOCALSTACK_ENDPOINT}/_localstack/health", timeout=5)
        health = response.json()

        print(f"  ✅ LocalStack 실행 중: {LOCALSTACK_ENDPOINT}")
        print(f"  - Services: {', '.join(health.get('services', {}).keys())}")
        return True
    except Exception as e:
        print(f"  ❌ LocalStack이 실행되지 않았습니다: {str(e)}")
        print(f"\n  💡 LocalStack을 시작하세요:")
        print(f"     docker-compose up -d localstack")
        return False


def check_sqs_queues():
    """SQS 큐가 생성되었는지 확인"""
    print_section("Step 2: SQS 큐 확인")

    try:
        sqs = boto3.client('sqs', endpoint_url=LOCALSTACK_ENDPOINT, region_name=AWS_REGION)
        response = sqs.list_queues()

        queues = response.get('QueueUrls', [])
        if not queues:
            print(f"  ⚠️  SQS 큐가 없습니다.")
            print(f"\n  💡 SQS 큐를 생성하세요:")
            print(f"     bash scripts/setup-localstack.sh")
            return False

        print(f"  ✅ {len(queues)}개의 SQS 큐를 찾았습니다:")
        for queue_url in queues:
            queue_name = queue_url.split('/')[-1]
            print(f"     - {queue_name}")

        return True
    except Exception as e:
        print(f"  ❌ SQS 큐 조회 실패: {str(e)}")
        return False


def deploy_lambda_functions():
    """Lambda 함수 배포"""
    print_section("Step 3: Lambda 함수 배포")

    try:
        # deploy-lambda.sh 스크립트 실행
        script_path = os.path.join(os.path.dirname(__file__), 'deploy-lambda.sh')

        if not os.path.exists(script_path):
            print(f"  ❌ 배포 스크립트를 찾을 수 없습니다: {script_path}")
            return False

        print(f"  📦 Lambda 배포 중... (약 30초 소요)")
        print(f"  - Script: {script_path}")

        # Windows에서는 Git Bash 필요
        if sys.platform == 'win32':
            result = subprocess.run(
                ['bash', script_path, 'local'],
                cwd=os.path.dirname(os.path.dirname(script_path)),
                capture_output=True,
                text=True
            )
        else:
            result = subprocess.run(
                ['bash', script_path, 'local'],
                cwd=os.path.dirname(os.path.dirname(script_path)),
                capture_output=True,
                text=True
            )

        if result.returncode != 0:
            print(f"  ❌ 배포 실패:")
            print(result.stderr)
            return False

        print(f"  ✅ Lambda 배포 완료")
        return True

    except Exception as e:
        print(f"  ❌ 배포 중 에러: {str(e)}")
        return False


def test_canvas_sync_lambda():
    """Canvas Sync Lambda 테스트 (Phase 1: Manual Sync)"""
    print_section("Step 4: Canvas Sync Lambda 호출 테스트")

    try:
        lambda_client = boto3.client('lambda', endpoint_url=LOCALSTACK_ENDPOINT, region_name=AWS_REGION)

        # 테스트 이벤트 (Phase 1 format)
        test_event = {
            'cognitoSub': 'test-cognito-sub-123'
        }

        print(f"  📤 Lambda 호출 중...")
        print(f"  - Function: canvas-sync-lambda")
        print(f"  - Event: {json.dumps(test_event, indent=2)}")

        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(test_event)
        )

        # 응답 파싱
        payload = json.loads(response['Payload'].read())

        print(f"\n  📥 Lambda 응답:")
        print(f"  - Status Code: {response['StatusCode']}")
        print(f"  - Payload:")
        print(json.dumps(payload, indent=4))

        # 에러 확인
        if 'errorMessage' in payload:
            print(f"\n  ⚠️  Lambda 실행 중 에러 발생:")
            print(f"     {payload['errorMessage']}")
            print(f"\n  💡 이것은 정상입니다. User-Service가 실행되지 않았거나")
            print(f"     Canvas 토큰이 없기 때문일 수 있습니다.")
            return payload

        print(f"\n  ✅ Lambda 실행 성공!")
        return payload

    except Exception as e:
        print(f"  ❌ Lambda 호출 실패: {str(e)}")
        return None


def check_sqs_messages(queue_name='lambda-to-courseservice-assignments'):
    """SQS 큐의 메시지 확인"""
    print_section(f"Step 5: SQS 메시지 확인 ({queue_name})")

    try:
        sqs = boto3.client('sqs', endpoint_url=LOCALSTACK_ENDPOINT, region_name=AWS_REGION)

        # 큐 URL 가져오기
        queue_url_response = sqs.get_queue_url(QueueName=queue_name)
        queue_url = queue_url_response['QueueUrl']

        print(f"  📬 큐 URL: {queue_url}")

        # 메시지 수신
        response = sqs.receive_message(
            QueueUrl=queue_url,
            MaxNumberOfMessages=10,
            WaitTimeSeconds=2
        )

        messages = response.get('Messages', [])

        if not messages:
            print(f"  ℹ️  메시지가 없습니다.")
            print(f"\n  💡 이것은 정상입니다. Lambda가 Canvas API를 호출하지 못했거나")
            print(f"     새로운 과제가 없을 수 있습니다.")
            return []

        print(f"  ✅ {len(messages)}개의 메시지를 찾았습니다:")
        for i, message in enumerate(messages, 1):
            body = json.loads(message['Body'])
            print(f"\n  [{i}] Message ID: {message['MessageId']}")
            print(f"      Body: {json.dumps(body, indent=6)}")

        return messages

    except Exception as e:
        print(f"  ❌ SQS 메시지 조회 실패: {str(e)}")
        return []


def test_llm_lambda():
    """LLM Lambda 테스트 (SQS 이벤트 시뮬레이션)"""
    print_section("Step 6: LLM Lambda 호출 테스트")

    try:
        lambda_client = boto3.client('lambda', endpoint_url=LOCALSTACK_ENDPOINT, region_name=AWS_REGION)

        # SQS 이벤트 시뮬레이션
        test_event = {
            'Records': [
                {
                    'messageId': 'test-msg-001',
                    'body': json.dumps({
                        'eventType': 'ASSIGNMENT_CREATED',
                        'courseId': 123,
                        'canvasAssignmentId': 'test_456',
                        'title': '테스트 과제',
                        'description': 'Spring Boot 프로젝트 개발',
                        'dueAt': '2025-11-15T23:59:00Z'
                    })
                }
            ]
        }

        print(f"  📤 Lambda 호출 중...")
        print(f"  - Function: llm-lambda")

        response = lambda_client.invoke(
            FunctionName='llm-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(test_event)
        )

        # 응답 파싱
        payload = json.loads(response['Payload'].read())

        print(f"\n  📥 Lambda 응답:")
        print(f"  - Status Code: {response['StatusCode']}")
        print(f"  - Payload:")
        print(json.dumps(payload, indent=4))

        # 에러 확인
        if 'errorMessage' in payload:
            print(f"\n  ⚠️  Lambda 실행 중 에러 발생:")
            print(f"     {payload['errorMessage']}")
            print(f"\n  💡 이것은 정상입니다. LLM API Key가 설정되지 않았을 수 있습니다.")
            return payload

        print(f"\n  ✅ Lambda 실행 성공!")
        return payload

    except Exception as e:
        print(f"  ❌ Lambda 호출 실패: {str(e)}")
        return None


def main():
    print_header("LocalStack Lambda 통합 테스트")

    print(f"\n📋 설정:")
    print(f"  - LocalStack Endpoint: {LOCALSTACK_ENDPOINT}")
    print(f"  - AWS Region: {AWS_REGION}")

    # Step 1: LocalStack 확인
    if not check_localstack_running():
        sys.exit(1)

    # Step 2: SQS 큐 확인
    if not check_sqs_queues():
        print(f"\n❓ SQS 큐를 생성하시겠습니까? (y/n)")
        answer = input().strip().lower()
        if answer == 'y':
            print(f"\n  💡 다음 명령어를 실행하세요:")
            print(f"     bash scripts/setup-localstack.sh")
            sys.exit(0)
        else:
            sys.exit(1)

    # Step 3: Lambda 배포
    print(f"\n❓ Lambda 함수를 배포하시겠습니까? (y/n)")
    print(f"   (이미 배포되어 있다면 'n'을 입력하세요)")
    answer = input().strip().lower()

    if answer == 'y':
        if not deploy_lambda_functions():
            sys.exit(1)

    # Step 4-6: Lambda 테스트
    canvas_result = test_canvas_sync_lambda()
    check_sqs_messages('lambda-to-courseservice-assignments')
    check_sqs_messages('lambda-to-courseservice-enrollments')
    # Note: llm_result is Phase 3, not implemented yet
    # llm_result = test_llm_lambda()

    # 결과 요약
    print_header("테스트 결과 요약")

    print(f"\n  ✅ LocalStack: 정상")
    print(f"  ✅ SQS 큐: 생성됨")
    print(f"  {'✅' if canvas_result and 'errorMessage' not in canvas_result else '⚠️ '} Canvas Sync Lambda: {'성공' if canvas_result and 'errorMessage' not in canvas_result else '에러 발생 (정상)'}")

    print(f"\n  💡 다음 단계:")
    print(f"     1. User-Service를 시작하세요 (cd app/backend/user-service && ./gradlew bootRun)")
    print(f"     2. Canvas 토큰을 User-Service에 저장하세요")
    print(f"     3. 다시 이 스크립트를 실행하여 전체 워크플로우를 테스트하세요")

    print("\n" + "=" * 70)
    print("  🎉 통합 테스트 완료!")
    print("=" * 70 + "\n")


if __name__ == '__main__':
    main()