"""
SQS DLQ (Dead Letter Queue) 동작 검증 테스트

이 테스트는 SQS의 RedrivePolicy가 올바르게 설정되어 있는지,
그리고 실패한 메시지가 실제로 DLQ로 이동하는지 검증합니다.
"""

import pytest
import json
import time
import uuid
import os

class TestSqsDlq:
    """SQS DLQ 및 RedrivePolicy 검증"""

    @pytest.fixture(scope="function")
    def test_queue_with_dlq(self, sqs_client):
        """
        테스트 전용 큐와 DLQ를 생성하고 반환
        (실제 운영 큐를 건드리지 않고 격리된 환경에서 테스트)
        """
        # 1. 고유한 이름으로 DLQ 생성
        dlq_name = f"test-dlq-{uuid.uuid4().hex[:8]}"
        dlq_response = sqs_client.create_queue(QueueName=dlq_name)
        dlq_url = dlq_response['QueueUrl']
        
        # DLQ ARN 조회 (RedrivePolicy 설정용)
        dlq_attrs = sqs_client.get_queue_attributes(
            QueueUrl=dlq_url,
            AttributeNames=['QueueArn']
        )
        dlq_arn = dlq_attrs['Attributes']['QueueArn']

        # 2. 메인 테스트 큐 생성 (maxReceiveCount=2로 설정하여 빠른 테스트)
        queue_name = f"test-queue-{uuid.uuid4().hex[:8]}"
        redrive_policy = {
            "deadLetterTargetArn": dlq_arn,
            "maxReceiveCount": "2"  # 2번 받으면(실패하면) DLQ로 이동
        }
        
        queue_response = sqs_client.create_queue(
            QueueName=queue_name,
            Attributes={
                'RedrivePolicy': json.dumps(redrive_policy),
                'VisibilityTimeout': '1'  # 1초 후 다시 보임 (빠른 재시도)
            }
        )
        queue_url = queue_response['QueueUrl']

        yield {
            "queue_url": queue_url,
            "dlq_url": dlq_url,
            "queue_name": queue_name,
            "dlq_name": dlq_name
        }

        # 3. 정리 (Tear down)
        try:
            sqs_client.delete_queue(QueueUrl=queue_url)
            sqs_client.delete_queue(QueueUrl=dlq_url)
        except Exception as e:
            print(f"Cleanup failed: {e}")

    def test_message_moves_to_dlq_after_max_receives(self, sqs_client, test_queue_with_dlq):
        """
        메시지가 maxReceiveCount를 초과하면 DLQ로 이동하는지 검증
        """
        queue_url = test_queue_with_dlq['queue_url']
        dlq_url = test_queue_with_dlq['dlq_url']
        message_body = "This message is destined to fail"

        # 1. 메시지 발행
        print(f"\n🚀 Sending message to test queue: {queue_url}")
        sqs_client.send_message(
            QueueUrl=queue_url,
            MessageBody=message_body
        )

        # 2. 첫 번째 수신 (Fail Count: 1)
        print("📥 1st Receive (Simulating failure)...")
        response1 = sqs_client.receive_message(
            QueueUrl=queue_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=2,
            VisibilityTimeout=1  # 1초 뒤 다시 보임
        )
        assert 'Messages' in response1
        assert len(response1['Messages']) == 1
        # 메시지를 삭제하지 않음 (= 처리 실패 시뮬레이션)
        
        # VisibilityTimeout 대기
        time.sleep(1.5)

        # 3. 두 번째 수신 (Fail Count: 2) -> 여기서 maxReceiveCount(2) 도달
        print("📥 2nd Receive (Simulating failure)...")
        response2 = sqs_client.receive_message(
            QueueUrl=queue_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=2,
            VisibilityTimeout=1
        )
        assert 'Messages' in response2
        assert len(response2['Messages']) == 1
        # 여전히 삭제하지 않음

        # VisibilityTimeout 대기
        time.sleep(1.5)

        # 4. 세 번째 수신 시도 -> 메인 큐에는 없어야 함
        print("📥 3rd Receive (Should be empty)...")
        response3 = sqs_client.receive_message(
            QueueUrl=queue_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=2
        )
        # 메인 큐에서는 사라져야 함
        assert 'Messages' not in response3 or len(response3.get('Messages', [])) == 0
        print("✅ Main queue is empty")

        # 5. DLQ 확인 -> 메시지가 여기 있어야 함
        print("🔎 Checking DLQ...")
        dlq_response = sqs_client.receive_message(
            QueueUrl=dlq_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=2
        )
        
        assert 'Messages' in dlq_response
        assert len(dlq_response['Messages']) == 1
        dlq_message = dlq_response['Messages'][0]
        assert dlq_message['Body'] == message_body
        print(f"✅ Message found in DLQ: {dlq_message['Body']}")

    def test_main_queues_have_redrive_policy(self, sqs_client):
        """
        실제 운영 큐들이 RedrivePolicy를 가지고 있는지 검증 (Infra Check)
        """
        # 검사할 메인 큐 목록
        target_queues = [
            os.environ.get('SQS_CANVAS_SYNC_QUEUE', 'lambda-to-courseservice-sync'),
            os.environ.get('SQS_ASSIGNMENT_TO_SCHEDULE_QUEUE', 'courseservice-to-scheduleservice-assignments')
        ]

        print("\n🔍 Verifying RedrivePolicy on main queues...")
        
        for queue_name in target_queues:
            try:
                queue_url = sqs_client.get_queue_url(QueueName=queue_name)['QueueUrl']
                attrs = sqs_client.get_queue_attributes(
                    QueueUrl=queue_url,
                    AttributeNames=['RedrivePolicy']
                )
                
                if 'Attributes' in attrs and 'RedrivePolicy' in attrs['Attributes']:
                    policy = json.loads(attrs['Attributes']['RedrivePolicy'])
                    print(f"✅ {queue_name}: RedrivePolicy found")
                    print(f"   - deadLetterTargetArn: {policy.get('deadLetterTargetArn')}")
                    print(f"   - maxReceiveCount: {policy.get('maxReceiveCount')}")
                    
                    assert 'deadLetterTargetArn' in policy
                    assert 'maxReceiveCount' in policy
                else:
                    pytest.fail(f"❌ {queue_name}: RedrivePolicy NOT configured")
                    
            except sqs_client.exceptions.QueueDoesNotExist:
                print(f"⚠️ {queue_name}: Queue does not exist (Skipping)")
