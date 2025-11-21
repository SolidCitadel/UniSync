"""
Course-Service → Schedule-Service Integration Test

Assignment → Schedule 변환 통합 테스트

플로우:
1. Course-Service → SQS (courseservice-to-scheduleservice-assignments 메시지 발행)
2. Schedule-Service → SQS (메시지 consume)
3. Schedule-Service → DB (Schedule 저장)
4. Canvas 카테고리 자동 생성 검증
5. 멱등성 검증 (중복 메시지 처리)
"""

import pytest
import json
import time
import requests
from datetime import datetime, timedelta


class TestAssignmentToScheduleIntegration:
    """Assignment → Schedule 자동 변환 통합 테스트"""

    @pytest.mark.usefixtures("clean_schedule_database")
    def test_assignment_to_schedule_creation(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_service_url
    ):
        """
        Assignment 생성 → Schedule 자동 생성 플로우

        Given: Course-Service가 Assignment를 저장함
        When: Assignment 생성 이벤트를 SQS로 발행
        Then:
          1. Schedule-Service가 메시지 consume
          2. Canvas 카테고리 자동 생성
          3. Schedule이 DB에 저장됨
        """
        # Given: Assignment 생성 이벤트 메시지
        due_at = datetime.now() + timedelta(days=7)

        assignment_message = {
            "eventType": "ASSIGNMENT_CREATED",
            "assignmentId": 12345,
            "cognitoSub": "test-user-123",
            "canvasAssignmentId": 98765,
            "canvasCourseId": 789,
            "title": "Spring Boot 중간고사 프로젝트",
            "description": "Spring Boot 애플리케이션을 작성하세요.",
            "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 100,
            "courseId": 101,
            "courseName": "웹 프로그래밍"
        }

        print(f"\n📤 Publishing assignment message to SQS...")
        print(f"   Message: {json.dumps(assignment_message, indent=2)}")

        # When: SQS 메시지 발행
        response = sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_message)
        )

        assert response['MessageId'], "SQS 메시지 발행 실패"
        print(f"✅ Message published: MessageId={response['MessageId']}")

        # Then: Schedule-Service가 메시지를 처리할 때까지 대기
        max_wait = 30
        schedule_created = False

        for i in range(max_wait):
            try:
                response = requests.get(
                    f"{schedule_service_url}/v1/schedules",
                    headers={"X-Cognito-Sub": "test-user-123"},
                    timeout=5
                )

                if response.status_code == 200:
                    schedules = response.json()
                    schedule = next(
                        (s for s in schedules
                         if s.get('source') == 'CANVAS' and
                         s.get('sourceId') == 'canvas-assignment-98765-test-user-123'),
                        None
                    )

                    if schedule:
                        schedule_created = True
                        print(f"\n✅ Schedule created via API:")
                        print(f"   - Schedule ID: {schedule['scheduleId']}")
                        print(f"   - Title: {schedule['title']}")
                        print(f"   - Source: {schedule['source']}")
                        print(f"   - Category ID: {schedule['categoryId']}")

                        # Schedule 검증
                        assert schedule['cognitoSub'] == 'test-user-123'
                        assert schedule['title'] == '[웹 프로그래밍] Spring Boot 중간고사 프로젝트'
                        assert schedule['source'] == 'CANVAS'
                        assert schedule['sourceId'] == 'canvas-assignment-98765-test-user-123'
                        assert schedule['categoryId'] is not None
                        break

            except Exception as e:
                if i == max_wait - 1:
                    raise
                pass

            time.sleep(1)

        assert schedule_created, "Schedule이 30초 내에 생성되지 않음"
        print(f"\n✅ Assignment → Schedule 변환 성공!")

    @pytest.mark.usefixtures("clean_schedule_database")
    def test_idempotency_duplicate_assignment_message(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_service_url
    ):
        """
        중복 메시지 처리 테스트 (멱등성)

        동일한 Assignment 메시지를 두 번 발행했을 때
        Schedule이 중복 생성되지 않는지 확인
        """
        due_at = datetime.now() + timedelta(days=5)

        assignment_message = {
            "eventType": "ASSIGNMENT_CREATED",
            "assignmentId": 22222,
            "cognitoSub": "test-user-456",
            "canvasAssignmentId": 11111,
            "canvasCourseId": 789,
            "title": "데이터베이스 과제",
            "description": "SQL 쿼리 작성",
            "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 50,
            "courseId": 102,
            "courseName": "데이터베이스"
        }

        # 첫 번째 메시지 발행
        print(f"\n📤 First message publication...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_message)
        )

        time.sleep(10)

        # 첫 번째 처리 후 Schedule 개수 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-456"},
            timeout=5
        )
        schedules = response.json()
        first_count = len([s for s in schedules if s.get('sourceId') == 'canvas-assignment-11111-test-user-456'])

        print(f"   → First schedule count: {first_count}")
        assert first_count == 1, "첫 번째 Schedule 생성 실패"

        # 두 번째 메시지 발행 (중복)
        print(f"\n📤 Second message publication (duplicate)...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_message)
        )

        time.sleep(10)

        # 두 번째 처리 후에도 개수가 동일해야 함
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-456"},
            timeout=5
        )
        schedules = response.json()
        second_count = len([s for s in schedules if s.get('sourceId') == 'canvas-assignment-11111-test-user-456'])

        print(f"   → Second schedule count: {second_count}")

        assert second_count == 1, f"중복 Schedule 생성됨: {first_count} → {second_count}"
        print(f"\n✅ 멱등성 검증 완료: 중복 데이터 없음")

    @pytest.mark.usefixtures("clean_schedule_database")
    def test_assignment_update_updates_schedule(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_service_url
    ):
        """
        Assignment 업데이트 → Schedule 업데이트 플로우

        Given: Schedule이 이미 생성되어 있음
        When: Assignment 업데이트 이벤트 발행
        Then: 기존 Schedule이 업데이트됨 (새로 생성되지 않음)
        """
        due_at = datetime.now() + timedelta(days=3)

        create_message = {
            "eventType": "ASSIGNMENT_CREATED",
            "assignmentId": 33333,
            "cognitoSub": "test-user-789",
            "canvasAssignmentId": 44444,
            "canvasCourseId": 789,
            "title": "알고리즘 과제 1",
            "description": "정렬 알고리즘 구현",
            "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 80,
            "courseId": 103,
            "courseName": "알고리즘"
        }

        print(f"\n📤 Creating initial schedule...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(create_message)
        )

        time.sleep(10)

        # 초기 Schedule 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-789"},
            timeout=5
        )
        schedules = response.json()
        initial_schedule = next(
            (s for s in schedules if s.get('sourceId') == 'canvas-assignment-44444-test-user-789'),
            None
        )

        assert initial_schedule is not None, "초기 Schedule 생성 실패"
        initial_schedule_id = initial_schedule['scheduleId']
        print(f"✅ Initial schedule created: ID={initial_schedule_id}")

        # Assignment 업데이트
        updated_due_at = datetime.now() + timedelta(days=5)

        update_message = {
            "eventType": "ASSIGNMENT_UPDATED",
            "assignmentId": 33333,
            "cognitoSub": "test-user-789",
            "canvasAssignmentId": 44444,
            "canvasCourseId": 789,
            "title": "알고리즘 과제 1 (수정됨)",
            "description": "정렬 및 탐색 알고리즘 구현",
            "dueAt": updated_due_at.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 80,
            "courseId": 103,
            "courseName": "알고리즘"
        }

        print(f"\n📤 Updating schedule...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(update_message)
        )

        time.sleep(10)

        # Schedule이 업데이트되었는지 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-789"},
            timeout=5
        )
        schedules = response.json()

        matching_schedules = [s for s in schedules if s.get('sourceId') == 'canvas-assignment-44444-test-user-789']
        updated_schedule = matching_schedules[0] if matching_schedules else None

        assert len(matching_schedules) == 1, f"Schedule이 중복 생성됨: {len(matching_schedules)}개"
        assert updated_schedule['scheduleId'] == initial_schedule_id
        assert updated_schedule['title'] == '[알고리즘] 알고리즘 과제 1 (수정됨)'
        assert updated_schedule['description'] == '정렬 및 탐색 알고리즘 구현'

        print(f"\n✅ Schedule 업데이트 검증 완료")

    @pytest.mark.usefixtures("clean_schedule_database")
    def test_assignment_deletion_deletes_schedule(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_service_url
    ):
        """
        Assignment 삭제 → Schedule 삭제 플로우

        Given: Schedule이 이미 생성되어 있음
        When: Assignment 삭제 이벤트 발행
        Then: 해당 Schedule이 DB에서 삭제됨
        """
        due_at = datetime.now() + timedelta(days=2)

        create_message = {
            "eventType": "ASSIGNMENT_CREATED",
            "assignmentId": 55555,
            "cognitoSub": "test-user-999",
            "canvasAssignmentId": 66666,
            "canvasCourseId": 789,
            "title": "네트워크 과제",
            "description": "TCP/IP 프로토콜 분석",
            "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 70,
            "courseId": 104,
            "courseName": "컴퓨터 네트워크"
        }

        print(f"\n📤 Creating schedule to be deleted...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(create_message)
        )

        time.sleep(10)

        # Schedule 생성 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-999"},
            timeout=5
        )
        schedules = response.json()
        initial_count = len([s for s in schedules if s.get('sourceId') == 'canvas-assignment-66666-test-user-999'])

        assert initial_count == 1, "초기 Schedule 생성 실패"
        print(f"✅ Initial schedule created")

        # Assignment 삭제
        delete_message = {
            "eventType": "ASSIGNMENT_DELETED",
            "assignmentId": 55555,
            "cognitoSub": "test-user-999",
            "canvasAssignmentId": 66666,
            "canvasCourseId": 789,
            "title": "네트워크 과제",
            "description": "TCP/IP 프로토콜 분석",
            "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 70,
            "courseId": 104,
            "courseName": "컴퓨터 네트워크"
        }

        print(f"\n📤 Deleting schedule...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(delete_message)
        )

        time.sleep(10)

        # Schedule이 삭제되었는지 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-999"},
            timeout=5
        )
        schedules = response.json()
        final_count = len([s for s in schedules if s.get('sourceId') == 'canvas-assignment-66666-test-user-999'])

        assert final_count == 0, f"Schedule이 삭제되지 않음: {final_count}개 존재"
        print(f"\n✅ Schedule 삭제 검증 완료")

    @pytest.mark.usefixtures("clean_schedule_database")
    def test_canvas_category_reuse(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_service_url
    ):
        """
        Canvas 카테고리 재사용 테스트

        동일한 사용자의 여러 Assignment가
        동일한 Canvas 카테고리를 재사용하는지 확인
        """
        due_at_1 = datetime.now() + timedelta(days=4)

        assignment_1 = {
            "eventType": "ASSIGNMENT_CREATED",
            "assignmentId": 77777,
            "cognitoSub": "test-user-category",
            "canvasAssignmentId": 88881,
            "canvasCourseId": 789,
            "title": "과제 1",
            "description": "첫 번째 과제",
            "dueAt": due_at_1.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 100,
            "courseId": 105,
            "courseName": "테스트 과목"
        }

        print(f"\n📤 Creating first schedule (should create Canvas category)...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_1)
        )

        time.sleep(10)

        # Canvas 카테고리 ID 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-category"},
            timeout=5
        )
        schedules = response.json()
        canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']

        assert len(canvas_schedules) == 1, "첫 번째 Schedule 생성 실패"
        first_category_id = canvas_schedules[0]['categoryId']
        print(f"✅ Canvas category created: ID={first_category_id}")

        # 두 번째 Assignment
        due_at_2 = datetime.now() + timedelta(days=6)

        assignment_2 = {
            "eventType": "ASSIGNMENT_CREATED",
            "assignmentId": 77778,
            "cognitoSub": "test-user-category",
            "canvasAssignmentId": 88882,
            "canvasCourseId": 789,
            "title": "과제 2",
            "description": "두 번째 과제",
            "dueAt": due_at_2.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 100,
            "courseId": 105,
            "courseName": "테스트 과목"
        }

        print(f"\n📤 Creating second schedule (should reuse Canvas category)...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_2)
        )

        time.sleep(10)

        # Canvas 카테고리 재사용 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-category"},
            timeout=5
        )
        schedules = response.json()
        canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']

        category_ids = list(set(s['categoryId'] for s in canvas_schedules))

        assert len(canvas_schedules) == 2, f"두 번째 Schedule 생성 실패: {len(canvas_schedules)}개"
        assert len(category_ids) == 1, f"Schedule들이 서로 다른 카테고리를 사용함: {category_ids}"
        assert category_ids[0] == first_category_id

        print(f"\n✅ Canvas 카테고리 재사용 검증 완료")
        print(f"   - Schedule count: {len(canvas_schedules)}")
        print(f"   - Reused Category ID: {first_category_id}")
