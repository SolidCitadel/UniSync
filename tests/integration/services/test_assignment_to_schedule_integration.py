"""
Assignment → Schedule 변환 통합 테스트 (Phase 1)

전체 플로우 테스트:
1. Course-Service → SQS (courseservice-to-scheduleservice-assignments 메시지 발행)
2. Schedule-Service → SQS (메시지 consume)
3. Schedule-Service → DB (Schedule 저장)
4. Canvas 카테고리 자동 생성 검증
5. 멱등성 검증 (중복 메시지 처리)
"""

import pytest
import json
import time
from datetime import datetime, timedelta


class TestAssignmentToScheduleIntegration:
    """Assignment → Schedule 자동 변환 통합 테스트"""

    @pytest.mark.usefixtures("wait_for_services", "clean_schedule_database")
    def test_assignment_to_schedule_creation(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_db_connection
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

        assert response['MessageId'], "❌ SQS 메시지 발행 실패"
        print(f"✅ Message published: MessageId={response['MessageId']}")

        # Then: Schedule-Service가 메시지를 처리할 때까지 대기
        max_wait = 30
        schedule_created = False
        category_created = False

        for i in range(max_wait):
            cursor = schedule_db_connection.cursor(dictionary=True)

            # Schedules 확인
            cursor.execute("""
                SELECT * FROM schedules
                WHERE source = 'CANVAS'
                AND source_id = %s
            """, (f"canvas-assignment-98765-test-user-123",))
            schedule = cursor.fetchone()

            # Categories 확인
            cursor.execute("""
                SELECT * FROM categories
                WHERE cognito_sub = %s
                AND name = 'Canvas'
            """, ("test-user-123",))
            category = cursor.fetchone()

            cursor.close()

            if schedule:
                schedule_created = True
                print(f"\n✅ Schedule created in DB:")
                print(f"   - Schedule ID: {schedule['schedule_id']}")
                print(f"   - Title: {schedule['title']}")
                print(f"   - Start: {schedule['start_time']}")
                print(f"   - End: {schedule['end_time']}")
                print(f"   - Source: {schedule['source']}")
                print(f"   - Source ID: {schedule['source_id']}")
                print(f"   - Category ID: {schedule['category_id']}")

                # Schedule 검증
                assert schedule['cognito_sub'] == 'test-user-123'
                assert schedule['title'] == '[웹 프로그래밍] Spring Boot 중간고사 프로젝트'
                assert schedule['description'] == 'Spring Boot 애플리케이션을 작성하세요.'
                assert schedule['source'] == 'CANVAS'
                assert schedule['source_id'] == 'canvas-assignment-98765-test-user-123'
                assert schedule['status'] == 'TODO'
                assert schedule['is_all_day'] == 0

            if category:
                category_created = True
                print(f"\n✅ Canvas category auto-created:")
                print(f"   - Category ID: {category['category_id']}")
                print(f"   - Name: {category['name']}")
                print(f"   - Color: {category['color']}")
                print(f"   - Icon: {category['icon']}")
                print(f"   - Is Default: {category['is_default']}")

                # Category 검증
                assert category['name'] == 'Canvas'
                assert category['color'] == '#FF6B6B'
                assert category['icon'] == '📚'
                assert category['is_default'] == 1

            if schedule_created and category_created:
                break

            time.sleep(1)

        # 검증
        assert schedule_created, "❌ Schedule이 30초 내에 생성되지 않음"
        assert category_created, "❌ Canvas 카테고리가 자동 생성되지 않음"

        print(f"\n✅ Assignment → Schedule 변환 성공!")

    @pytest.mark.usefixtures("wait_for_services", "clean_schedule_database")
    def test_idempotency_duplicate_assignment_message(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_db_connection
    ):
        """
        중복 메시지 처리 테스트 (멱등성)

        동일한 Assignment 메시지를 두 번 발행했을 때
        Schedule이 중복 생성되지 않는지 확인
        """
        # Given: Assignment 메시지
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

        # When: 첫 번째 메시지 발행
        print(f"\n📤 First message publication...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_message)
        )

        # 처리 완료 대기
        time.sleep(10)

        # 첫 번째 처리 후 Schedule 개수 확인
        cursor = schedule_db_connection.cursor(dictionary=True)
        cursor.execute("""
            SELECT COUNT(*) as count FROM schedules
            WHERE source_id = %s
        """, (f"canvas-assignment-11111-test-user-456",))
        first_count = cursor.fetchone()['count']
        cursor.close()

        print(f"   → First schedule count: {first_count}")
        assert first_count == 1, "❌ 첫 번째 Schedule 생성 실패"

        # When: 두 번째 메시지 발행 (중복)
        print(f"\n📤 Second message publication (duplicate)...")
        sqs_client.send_message(
            QueueUrl=assignment_to_schedule_queue_url,
            MessageBody=json.dumps(assignment_message)
        )

        # 처리 완료 대기
        time.sleep(10)

        # Then: 두 번째 처리 후에도 개수가 동일해야 함
        cursor = schedule_db_connection.cursor(dictionary=True)
        cursor.execute("""
            SELECT COUNT(*) as count FROM schedules
            WHERE source_id = %s
        """, (f"canvas-assignment-11111-test-user-456",))
        second_count = cursor.fetchone()['count']
        cursor.close()

        print(f"   → Second schedule count: {second_count}")

        assert second_count == 1, \
            f"❌ 중복 Schedule 생성됨: {first_count} → {second_count}"
        print(f"\n✅ 멱등성 검증 완료: 중복 데이터 없음")

    @pytest.mark.usefixtures("wait_for_services", "clean_schedule_database")
    def test_assignment_update_updates_schedule(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_db_connection
    ):
        """
        Assignment 업데이트 → Schedule 업데이트 플로우

        Given: Schedule이 이미 생성되어 있음
        When: Assignment 업데이트 이벤트 발행
        Then: 기존 Schedule이 업데이트됨 (새로 생성되지 않음)
        """
        # Given: 기존 Assignment 생성
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

        # 생성 완료 대기
        time.sleep(10)

        # 초기 Schedule 확인
        cursor = schedule_db_connection.cursor(dictionary=True)
        cursor.execute("""
            SELECT * FROM schedules
            WHERE source_id = %s
        """, (f"canvas-assignment-44444-test-user-789",))
        initial_schedule = cursor.fetchone()
        cursor.close()

        assert initial_schedule is not None, "❌ 초기 Schedule 생성 실패"
        initial_schedule_id = initial_schedule['schedule_id']
        print(f"✅ Initial schedule created: ID={initial_schedule_id}, Title={initial_schedule['title']}")

        # When: Assignment 업데이트
        updated_due_at = datetime.now() + timedelta(days=5)  # 마감일 연장

        update_message = {
            "eventType": "ASSIGNMENT_UPDATED",
            "assignmentId": 33333,
            "cognitoSub": "test-user-789",
            "canvasAssignmentId": 44444,
            "canvasCourseId": 789,
            "title": "알고리즘 과제 1 (수정됨)",  # 제목 변경
            "description": "정렬 및 탐색 알고리즘 구현",  # 설명 변경
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

        # 업데이트 완료 대기
        time.sleep(10)

        # Then: Schedule이 업데이트되었는지 확인
        cursor = schedule_db_connection.cursor(dictionary=True)

        # Schedule 개수 확인 (여전히 1개여야 함)
        cursor.execute("""
            SELECT COUNT(*) as count FROM schedules
            WHERE source_id = %s
        """, (f"canvas-assignment-44444-test-user-789",))
        schedule_count = cursor.fetchone()['count']

        # 업데이트된 Schedule 조회
        cursor.execute("""
            SELECT * FROM schedules
            WHERE source_id = %s
        """, (f"canvas-assignment-44444-test-user-789",))
        updated_schedule = cursor.fetchone()
        cursor.close()

        # 검증
        assert schedule_count == 1, f"❌ Schedule이 중복 생성됨: {schedule_count}개"
        assert updated_schedule['schedule_id'] == initial_schedule_id, \
            "❌ 새로운 Schedule이 생성됨 (기존 Schedule이 업데이트되어야 함)"
        assert updated_schedule['title'] == '[알고리즘] 알고리즘 과제 1 (수정됨)', \
            f"❌ 제목이 업데이트되지 않음: {updated_schedule['title']}"
        assert updated_schedule['description'] == '정렬 및 탐색 알고리즘 구현', \
            f"❌ 설명이 업데이트되지 않음: {updated_schedule['description']}"

        print(f"\n✅ Schedule 업데이트 검증 완료:")
        print(f"   - Same Schedule ID: {initial_schedule_id}")
        print(f"   - Updated Title: {updated_schedule['title']}")
        print(f"   - Updated Description: {updated_schedule['description']}")

    @pytest.mark.usefixtures("wait_for_services", "clean_schedule_database")
    def test_assignment_deletion_deletes_schedule(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_db_connection
    ):
        """
        Assignment 삭제 → Schedule 삭제 플로우

        Given: Schedule이 이미 생성되어 있음
        When: Assignment 삭제 이벤트 발행
        Then: 해당 Schedule이 DB에서 삭제됨
        """
        # Given: 기존 Assignment 생성
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

        # 생성 완료 대기
        time.sleep(10)

        # Schedule 생성 확인
        cursor = schedule_db_connection.cursor(dictionary=True)
        cursor.execute("""
            SELECT COUNT(*) as count FROM schedules
            WHERE source_id = %s
        """, (f"canvas-assignment-66666-test-user-999",))
        initial_count = cursor.fetchone()['count']
        cursor.close()

        assert initial_count == 1, "❌ 초기 Schedule 생성 실패"
        print(f"✅ Initial schedule created")

        # When: Assignment 삭제
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

        # 삭제 완료 대기
        time.sleep(10)

        # Then: Schedule이 삭제되었는지 확인
        cursor = schedule_db_connection.cursor(dictionary=True)
        cursor.execute("""
            SELECT COUNT(*) as count FROM schedules
            WHERE source_id = %s
        """, (f"canvas-assignment-66666-test-user-999",))
        final_count = cursor.fetchone()['count']
        cursor.close()

        assert final_count == 0, f"❌ Schedule이 삭제되지 않음: {final_count}개 존재"
        print(f"\n✅ Schedule 삭제 검증 완료")

    @pytest.mark.usefixtures("wait_for_services", "clean_schedule_database")
    def test_canvas_category_reuse(
        self,
        sqs_client,
        assignment_to_schedule_queue_url,
        schedule_db_connection
    ):
        """
        Canvas 카테고리 재사용 테스트

        동일한 사용자의 여러 Assignment가
        동일한 Canvas 카테고리를 재사용하는지 확인
        """
        # Given: 첫 번째 Assignment
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
        cursor = schedule_db_connection.cursor(dictionary=True)
        cursor.execute("""
            SELECT category_id FROM categories
            WHERE cognito_sub = %s AND name = 'Canvas'
        """, ("test-user-category",))
        category = cursor.fetchone()
        cursor.close()

        assert category is not None, "❌ Canvas 카테고리 생성 실패"
        first_category_id = category['category_id']
        print(f"✅ Canvas category created: ID={first_category_id}")

        # When: 두 번째 Assignment
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

        # Then: Canvas 카테고리 개수 및 재사용 확인
        cursor = schedule_db_connection.cursor(dictionary=True)

        # Canvas 카테고리 개수 확인 (여전히 1개여야 함)
        cursor.execute("""
            SELECT COUNT(*) as count FROM categories
            WHERE cognito_sub = %s AND name = 'Canvas'
        """, ("test-user-category",))
        category_count = cursor.fetchone()['count']

        # 두 Schedule이 동일한 category_id를 사용하는지 확인
        cursor.execute("""
            SELECT DISTINCT category_id FROM schedules
            WHERE cognito_sub = %s AND source = 'CANVAS'
        """, ("test-user-category",))
        category_ids = [row['category_id'] for row in cursor.fetchall()]
        cursor.close()

        assert category_count == 1, f"❌ Canvas 카테고리가 중복 생성됨: {category_count}개"
        assert len(category_ids) == 1, \
            f"❌ Schedule들이 서로 다른 카테고리를 사용함: {category_ids}"
        assert category_ids[0] == first_category_id, \
            f"❌ 기존 Canvas 카테고리가 재사용되지 않음"

        print(f"\n✅ Canvas 카테고리 재사용 검증 완료:")
        print(f"   - Category count: {category_count}")
        print(f"   - Reused Category ID: {first_category_id}")
