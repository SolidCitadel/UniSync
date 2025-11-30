"""
Lambda → Course-Service Integration: disabled enrollments are skipped

If a user has no sync-enabled enrollments, the Lambda should exit early without
emitting SQS messages or hitting Canvas.
"""

import json
import time
import pytest


@pytest.mark.usefixtures("clean_database")
class TestDisabledEnrollments:
    def test_no_enabled_enrollments_skips_sync(
        self,
        lambda_client,
        sqs_client,
        canvas_sync_queue_url
    ):
        """
        No enabled enrollments → Lambda returns counts=0 and does not publish to SQS.
        """
        # SQS 초기화
        sqs_client.purge_queue(QueueUrl=canvas_sync_queue_url)
        time.sleep(1)

        lambda_event = {
            "cognitoSub": "test-cognito-sub-123",
            "syncMode": "assignments"
        }

        print("\n🚫 Invoking Lambda with no enabled enrollments...")
        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        result = json.loads(response['Payload'].read())
        print(f"Lambda result: {result}")

        assert response['StatusCode'] == 200
        assert result.get('statusCode') == 200
        assert result.get('body', {}).get('coursesCount', 0) == 0
        assert result.get('body', {}).get('assignmentsCount', 0) == 0

        # 큐에 메시지가 없어야 함
        sqs_response = sqs_client.receive_message(
            QueueUrl=canvas_sync_queue_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=2
        )
        messages = sqs_response.get('Messages', [])
        assert len(messages) == 0, f"Unexpected SQS messages: {messages}"
        print("✅ No SQS messages published when enrollments are disabled/absent")
