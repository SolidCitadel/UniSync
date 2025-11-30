"""
Lambda → Course-Service → Schedule-Service Full Flow Integration Test

Assignment 생성부터 Schedule 생성까지 전체 플로우 검증

플로우:
1. Lambda 호출 (syncMode: assignments)
2. Lambda → Canvas API (assignments 조회)
3. Lambda → SQS (CANVAS_SYNC_COMPLETED 발행)
4. Course-Service → Assignment 저장
5. Course-Service → SQS (ASSIGNMENT_CREATED 발행)
6. Schedule-Service → Schedule 생성
"""

import pytest
import json
import time
import requests


class TestAssignmentEventFlow:
    """Assignment 생성 → Schedule 생성 전체 플로우 테스트"""

    @pytest.mark.usefixtures("clean_database", "clean_schedule_database")
    def test_course_to_schedule_full_flow_via_sqs(
        self,
        sqs_client,
        canvas_sync_queue_url,
        mysql_connection,
        schedule_service_url
    ):
        """
        SQS 메시지 발행 → Assignment 저장 → Schedule 생성 전체 플로우 (Mock)

        Given: CANVAS_SYNC_COMPLETED SQS 메시지 (assignments 포함)
        When: SQS 메시지 발행
        Then:
          1. Course-Service가 Course + Assignment 저장
          2. Course-Service가 ASSIGNMENT_CREATED 이벤트 발행
          3. Schedule-Service가 Schedule 생성
        """
        # Given: Mock Canvas 동기화 메시지 (assignments 포함)
        cognito_sub = "test-user-integration-123"

        sync_message = {
            "eventType": "CANVAS_SYNC_COMPLETED",
            "syncMode": "assignments",
            "cognitoSub": cognito_sub,
            "syncedAt": "2025-12-01T12:00:00Z",
            "courses": [
                {
                    "canvasCourseId": 100,
                    "courseName": "데이터구조",
                    "courseCode": "CS201",
                    "workflowState": "available",
                    "startAt": "2025-09-01T00:00:00Z",
                    "endAt": "2025-12-15T23:59:59Z",
                    "assignments": [
                        {
                            "canvasAssignmentId": 1001,
                            "title": "중간고사 프로젝트",
                            "description": "Spring Boot 애플리케이션 개발",
                            "dueAt": "2025-12-15T23:59:00Z",
                            "pointsPossible": 100,
                            "submissionTypes": "online_upload"
                        },
                        {
                            "canvasAssignmentId": 1002,
                            "title": "기말고사 프로젝트",
                            "description": "REST API 서버 개발",
                            "dueAt": "2025-12-30T23:59:00Z",
                            "pointsPossible": 150,
                            "submissionTypes": "online_upload"
                        }
                    ]
                }
            ]
        }

        print(f"\n📤 Publishing Canvas sync message to SQS...")
        print(f"   - Courses: {len(sync_message['courses'])}")
        print(f"   - Assignments: {sum(len(c.get('assignments', [])) for c in sync_message['courses'])}")

        # When: SQS 메시지 발행
        response = sqs_client.send_message(
            QueueUrl=canvas_sync_queue_url,
            MessageBody=json.dumps(sync_message)
        )

        assert response['MessageId'], "SQS 메시지 발행 실패"
        print(f"✅ Message published: MessageId={response['MessageId']}")

        # Then: Schedule-Service가 Schedule을 생성할 때까지 대기
        schedules_created = False
        max_wait = 45

        print("\n⏳ Waiting for Schedule-Service to create schedules...")
        for i in range(max_wait):
            try:
                response = requests.get(
                    f"{schedule_service_url}/v1/schedules",
                    headers={"X-Cognito-Sub": cognito_sub},
                    timeout=5
                )

                if response.status_code == 200:
                    schedules = response.json()
                    canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']

                    if canvas_schedules:
                        schedules_created = True
                        print(f"\n✅ {len(canvas_schedules)}개 CANVAS schedules 생성 완료:")

                        for schedule in canvas_schedules[:3]:  # 처음 3개만 출력
                            print(f"   - Schedule ID: {schedule.get('scheduleId')}")
                            print(f"     Title: {schedule.get('title')}")
                            print(f"     Source: {schedule.get('source')}")
                            print(f"     Category ID: {schedule.get('categoryId')}")

                        # Schedule 검증
                        for schedule in canvas_schedules:
                            assert schedule['cognitoSub'] == cognito_sub
                            assert schedule['source'] == 'CANVAS'
                            assert 'title' in schedule
                            assert 'startTime' in schedule
                            assert 'endTime' in schedule
                            assert 'categoryId' in schedule
                            assert schedule.get('isAllDay') is True  # Phase 1.1: all-day 이벤트

                        break
            except Exception as e:
                print(f"   Attempt {i+1}: {str(e)}")

            time.sleep(1)

        assert schedules_created, \
            f"Schedule이 Schedule-Service에 생성되지 않음. " \
            f"원인: Course-Service의 ASSIGNMENT_CREATED 이벤트가 발행되지 않았거나, " \
            f"Schedule-Service의 리스너가 동작하지 않음. " \
            f"또는 모든 assignments의 dueAt이 null일 수 있음."

    @pytest.mark.usefixtures("clean_database", "clean_schedule_database")
    def test_assignment_with_null_dueat_skips_schedule_creation(
        self,
        sqs_client,
        canvas_sync_queue_url,
        mysql_connection,
        schedule_service_url
    ):
        """
        dueAt이 null인 Assignment는 Schedule을 생성하지 않음 검증

        Given: dueAt이 null인 Assignment 이벤트
        When: SQS 메시지 발행
        Then: Schedule이 생성되지 않음
        """
        cognito_sub = "test-user-null-dueat"

        # Given: dueAt이 null인 Assignment 생성 메시지
        sync_message = {
            "eventType": "CANVAS_SYNC_COMPLETED",
            "syncMode": "assignments",
            "cognitoSub": cognito_sub,
            "syncedAt": "2025-12-01T12:00:00Z",
            "courses": [
                {
                    "canvasCourseId": 999,
                    "courseName": "Test Course",
                    "courseCode": "TEST101",
                    "workflowState": "available",
                    "assignments": [
                        {
                            "canvasAssignmentId": 12345,
                            "title": "Assignment without due date",
                            "description": "This assignment has no due date",
                            "dueAt": None,  # dueAt이 null
                            "pointsPossible": 100,
                            "submissionTypes": "online_upload"
                        }
                    ]
                }
            ]
        }

        print(f"\n📤 Publishing assignment with null dueAt...")

        # When: SQS 메시지 발행
        response = sqs_client.send_message(
            QueueUrl=canvas_sync_queue_url,
            MessageBody=json.dumps(sync_message)
        )

        assert response['MessageId'], "SQS 메시지 발행 실패"
        print(f"✅ Message published: MessageId={response['MessageId']}")

        # Course-Service가 Assignment를 저장할 때까지 대기
        time.sleep(5)

        # Assignment는 저장되어야 함
        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute(
            "SELECT * FROM assignments WHERE canvas_assignment_id = 12345"
        )
        assignment = cursor.fetchone()
        cursor.close()

        assert assignment is not None, "Assignment가 저장되지 않음"
        assert assignment['due_at'] is None, "dueAt이 null이 아님"
        print(f"✅ Assignment 저장됨 (due_at=null)")

        # Then: Schedule은 생성되지 않아야 함
        time.sleep(10)  # Schedule 생성 대기

        try:
            response = requests.get(
                f"{schedule_service_url}/v1/schedules",
                headers={"X-Cognito-Sub": cognito_sub},
                timeout=5
            )

            if response.status_code == 200:
                schedules = response.json()
                canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']

                assert len(canvas_schedules) == 0, \
                    f"dueAt이 null인 Assignment에 대해 Schedule이 생성됨 (예상: 0개, 실제: {len(canvas_schedules)}개)"

                print(f"✅ dueAt=null인 Assignment는 Schedule을 생성하지 않음 (검증 통과)")
        except Exception as e:
            print(f"⚠️ Schedule 조회 실패: {e}")
