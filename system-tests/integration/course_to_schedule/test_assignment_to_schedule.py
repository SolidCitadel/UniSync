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
        USER_ASSIGNMENTS_CREATED 배치 → Schedule 자동 생성 플로우

        Given: Course-Service가 사용자별 assignments 배치를 발행함
        When: USER_ASSIGNMENTS_CREATED 배치 메시지를 SQS로 발행
        Then:
          1. Schedule-Service가 배치 메시지 consume
          2. Canvas 카테고리 자동 생성
          3. Schedule이 DB에 저장됨
        """
        cognito_sub = "test-user-123"
        due_at = datetime.now() + timedelta(days=7)

        assignment_payload = {
            "assignmentId": 12345,
            "canvasAssignmentId": 98765,
            "canvasCourseId": 789,
            "courseId": 101,
            "courseName": "웹 프로그래밍",
            "title": "Spring Boot 중간고사 프로젝트",
            "description": "Spring Boot 애플리케이션을 작성하세요.",
            "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
            "pointsPossible": 100.0
        }

        batch_message = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": [assignment_payload]
        }

        print(f"\n📤 Publishing assignment message to SQS...")
        print(f"   Message: {json.dumps(batch_message, indent=2, ensure_ascii=False)}")

        # When: SQS 메시지 발행
        response = sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(batch_message)
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
                    headers={"X-Cognito-Sub": cognito_sub},
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

                        # Schedule 검증 (Phase 1.1: 제목은 과제 원본, 과목 정보는 카테고리로 구분)
                        assert schedule['cognitoSub'] == 'test-user-123'
                        assert schedule['title'] == 'Spring Boot 중간고사 프로젝트'
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
        cognito_sub = "test-user-456"

        batch_message = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": [
                {
                    "assignmentId": 22222,
                    "canvasAssignmentId": 11111,
                    "canvasCourseId": 789,
                    "title": "데이터베이스 과제",
                    "description": "SQL 쿼리 작성",
                    "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
                    "pointsPossible": 50.0,
                    "courseId": 102,
                    "courseName": "데이터베이스"
                }
            ]
        }

        # 첫 번째 메시지 발행
        print(f"\n📤 First message publication...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(batch_message)
        )

        time.sleep(10)

        # 첫 번째 처리 후 Schedule 개수 확인
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
        schedules = response.json()
        first_count = len([s for s in schedules if s.get('sourceId') == 'canvas-assignment-11111-test-user-456'])

        print(f"   → First schedule count: {first_count}")
        assert first_count == 1, "첫 번째 Schedule 생성 실패"

        # 두 번째 메시지 발행 (중복)
        print(f"\n📤 Second message publication (duplicate)...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(batch_message)
        )

        time.sleep(10)

        # 두 번째 처리 후에도 개수가 동일해야 함
        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
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
        cognito_sub = "test-user-789"
        due_at = datetime.now() + timedelta(days=3)

        create_message = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": [
                {
                    "assignmentId": 33333,
                    "canvasAssignmentId": 44444,
                    "canvasCourseId": 789,
                    "title": "알고리즘 과제 1",
                    "description": "정렬 알고리즘 구현",
                    "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
                    "pointsPossible": 80.0,
                    "courseId": 103,
                    "courseName": "알고리즘"
                }
            ]
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
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
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
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": [
                {
                    "assignmentId": 33333,
                    "canvasAssignmentId": 44444,
                    "canvasCourseId": 789,
                    "title": "알고리즘 과제 1 (수정됨)",
                    "description": "정렬 및 탐색 알고리즘 구현",
                    "dueAt": updated_due_at.strftime("%Y-%m-%dT%H:%M:%S"),
                    "pointsPossible": 90.0,
                    "courseId": 103,
                    "courseName": "알고리즘"
                }
            ]
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
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
        schedules = response.json()

        matching_schedules = [s for s in schedules if s.get('sourceId') == 'canvas-assignment-44444-test-user-789']
        updated_schedule = matching_schedules[0] if matching_schedules else None

        assert len(matching_schedules) == 1, f"Schedule이 중복 생성됨: {len(matching_schedules)}개"
        assert updated_schedule['scheduleId'] == initial_schedule_id
        # Phase 1.1: 제목은 과제 제목만 유지 (과목명은 카테고리로 표현)
        assert updated_schedule['title'] == '알고리즘 과제 1 (수정됨)'
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
        cognito_sub = "test-user-999"
        due_at = datetime.now() + timedelta(days=2)

        create_message = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": [
                {
                    "assignmentId": 55555,
                    "canvasAssignmentId": 66666,
                    "canvasCourseId": 789,
                    "title": "네트워크 과제",
                    "description": "TCP/IP 프로토콜 분석",
                    "dueAt": due_at.strftime("%Y-%m-%dT%H:%M:%S"),
                    "pointsPossible": 70.0,
                    "courseId": 104,
                    "courseName": "컴퓨터 네트워크"
                }
            ]
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
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
        schedules = response.json()
        initial_count = len([s for s in schedules if s.get('sourceId') == 'canvas-assignment-66666-test-user-999'])

        assert initial_count == 1, "초기 Schedule 생성 실패"
        print(f"✅ Initial schedule created")

        # Assignment 삭제: 배치에서 해당 assignment를 제거 (빈 assignments로 prune)
        delete_message = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": []
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
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
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
        cognito_sub = "test-user-category"

        assignment_1 = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": [
                {
                    "assignmentId": 77777,
                    "canvasAssignmentId": 88881,
                    "canvasCourseId": 789,
                    "title": "과제 1",
                    "description": "첫 번째 과제",
                    "dueAt": due_at_1.strftime("%Y-%m-%dT%H:%M:%S"),
                    "pointsPossible": 100.0,
                    "courseId": 105,
                    "courseName": "테스트 과목"
                }
            ]
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
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
        schedules = response.json()
        canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']

        assert len(canvas_schedules) == 1, "첫 번째 Schedule 생성 실패"
        first_category_id = canvas_schedules[0]['categoryId']
        print(f"✅ Canvas category created: ID={first_category_id}")

        # 두 번째 Assignment
        due_at_2 = datetime.now() + timedelta(days=6)

        assignment_2 = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": cognito_sub,
            "syncedAt": datetime.now().isoformat(),
            "assignments": [
                {
                    "assignmentId": 77778,
                    "canvasAssignmentId": 88882,
                    "canvasCourseId": 789,
                    "title": "과제 2",
                    "description": "두 번째 과제",
                    "dueAt": due_at_2.strftime("%Y-%m-%dT%H:%M:%S"),
                    "pointsPossible": 100.0,
                    "courseId": 105,
                    "courseName": "테스트 과목"
                }
            ]
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
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"
        schedules = response.json()
        canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']

        category_ids = list(set(s['categoryId'] for s in canvas_schedules))

        assert len(canvas_schedules) == 1, f"두 번째 배치 후 Schedule 개수 불일치: {len(canvas_schedules)}개"
        assert len(category_ids) == 1, f"Schedule들이 서로 다른 카테고리를 사용함: {category_ids}"
        assert category_ids[0] == first_category_id

        print(f"\n✅ Canvas 카테고리 재사용 검증 완료")
        print(f"   - Schedule count: {len(canvas_schedules)}")
        print(f"   - Reused Category ID: {first_category_id}")

    @pytest.mark.usefixtures("clean_schedule_database")
    def test_assignment_with_null_due_date_is_skipped(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_service_url
    ):
        """
        dueAt가 없는 Assignment 메시지는 Schedule로 생성되지 않아야 한다.
        """
        assignment_message = {
            "eventType": "USER_ASSIGNMENTS_CREATED",
            "cognitoSub": "test-user-nodue",
            "syncedAt": datetime.now().isoformat(),
            "assignments": [
                {
                    "assignmentId": 88888,
                    "canvasAssignmentId": 99999,
                    "canvasCourseId": 789,
                    "title": "기한 없는 과제",
                    "description": "dueAt가 없으면 무시되어야 함",
                    "dueAt": None,
                    "pointsPossible": 10.0,
                    "courseId": 106,
                    "courseName": "무기한 과목"
                }
            ]
        }

        print(f"\n🕒 Publishing assignment without dueAt...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_message)
        )

        time.sleep(10)

        response = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": "test-user-nodue"},
            timeout=5
        )
        assert response.status_code == 200, \
            f"Schedule 조회 실패: {response.status_code} - {response.text}"

        schedules = response.json()
        created = [s for s in schedules if s.get('sourceId') == 'canvas-assignment-99999-test-user-nodue']

        assert len(created) == 0, f"dueAt 없는 과제가 Schedule로 생성됨: {created}"
        print("✅ dueAt가 없는 Assignment는 생성되지 않음")
