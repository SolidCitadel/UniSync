"""
Assignment Flow E2E Test with Lambda
실제 Lambda 실행 → SQS → Course-Service → DB 전체 플로우 테스트
"""

import pytest
import json
import time
import os


class TestAssignmentFlowWithLambda:
    """Lambda를 실제로 실행하는 E2E 테스트"""

    @pytest.mark.usefixtures("wait_for_services", "clean_sqs_queue", "test_course")
    def test_lambda_canvas_to_db_flow(
        self, lambda_client, sqs_client, assignment_queue_url, mysql_connection, test_course
    ):
        """
        진짜 E2E 테스트:
        1. Lambda invoke
        2. Lambda가 실제 Canvas API 호출
        3. Lambda가 SQS에 메시지 발행
        4. course-service가 consume
        5. DB에 저장
        6. 검증
        """
        # Given: Lambda invoke 이벤트
        lambda_event = {
            "courseId": test_course['id'],
            "canvasCourseId": str(test_course['canvas_course_id']),
            "leaderUserId": 1,
            "lastSyncedAt": None
        }

        # When: Lambda 실행 (실제 Canvas API 호출!)
        print(f"🚀 Invoking lambda with event: {lambda_event}")

        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',  # 동기 실행
            Payload=json.dumps(lambda_event)
        )

        # Lambda 실행 결과 확인
        result = json.loads(response['Payload'].read())
        print(f"📦 Lambda response: {result}")

        assert response['StatusCode'] == 200

        if 'body' in result:
            body = result['body'] if isinstance(result['body'], dict) else json.loads(result['body'])
            print(f"✅ Lambda executed: {body.get('assignmentsCount', 0)} assignments fetched")

        # Then: course-service가 메시지를 처리할 때까지 대기
        max_wait = 30
        assignments_saved = False

        for i in range(max_wait):
            cursor = mysql_connection.cursor(dictionary=True)
            cursor.execute(
                "SELECT COUNT(*) as count FROM assignments WHERE course_id = %s",
                (test_course['id'],)
            )
            result = cursor.fetchone()
            cursor.close()

            if result and result['count'] > 0:
                assignments_saved = True
                print(f"✅ {result['count']}개 assignments DB에 저장 완료")

                # 실제 데이터 검증
                cursor = mysql_connection.cursor(dictionary=True)
                cursor.execute(
                    "SELECT * FROM assignments WHERE course_id = %s LIMIT 1",
                    (test_course['id'],)
                )
                assignment = cursor.fetchone()
                cursor.close()

                # 검증
                assert assignment is not None
                assert assignment['canvas_assignment_id'] is not None
                assert assignment['title'] is not None
                assert assignment['course_id'] == test_course['id']
                print(f"📝 Assignment: {assignment['title']}")
                break

            time.sleep(1)

        assert assignments_saved, "❌ Assignments가 30초 내에 DB에 저장되지 않음"

    @pytest.mark.usefixtures("wait_for_services")
    def test_lambda_without_course_in_db(
        self, lambda_client, mysql_connection
    ):
        """
        시나리오: DB에 Course가 없을 때 Lambda 실행
        → Assignment는 생성되지 않음 (Course 없음)
        """
        # Given: DB에 Course 생성 안 함
        lambda_event = {
            "courseId": 99999,  # 존재하지 않는 Course ID
            "canvasCourseId": "99999",
            "leaderUserId": 1,
            "lastSyncedAt": None
        }

        # When: Lambda 실행
        print(f"🚀 Invoking lambda with non-existent course...")

        try:
            response = lambda_client.invoke(
                FunctionName='canvas-sync-lambda',
                InvocationType='RequestResponse',
                Payload=json.dumps(lambda_event)
            )

            result = json.loads(response['Payload'].read())
            print(f"📦 Lambda response: {result}")
        except Exception as e:
            print(f"⚠️ Lambda execution might have issues: {e}")

        # Then: DB에 Assignment가 생성되지 않음
        time.sleep(5)

        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute("SELECT COUNT(*) as count FROM assignments WHERE course_id = 99999")
        result = cursor.fetchone()
        cursor.close()

        assert result['count'] == 0, "Course 없는 Assignment가 생성됨"
        print(f"✅ Course 없이 Assignment 생성 안 됨 (정상 동작)")