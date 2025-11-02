"""
Assignment Flow E2E Test
Lambda → SQS → Course-Service → DB 전체 플로우 테스트
"""

import pytest
import json
import time
import requests
from datetime import datetime


class TestAssignmentFlow:
    """Assignment 생성/수정 E2E 테스트"""

    @pytest.mark.usefixtures("wait_for_services", "clean_sqs_queue", "test_course")
    def test_assignment_create_flow(
        self, sqs_client, assignment_queue_url, mysql_connection, test_course
    ):
        """
        시나리오: Canvas-Sync-Lambda가 새 과제를 감지하여 SQS에 발행
        → course-service가 consume하여 DB에 저장
        """
        # Given: Lambda가 발행할 Assignment 이벤트 메시지
        message = {
            "eventType": "ASSIGNMENT_CREATED",
            "canvasAssignmentId": 123456,
            "canvasCourseId": test_course['canvas_course_id'],
            "title": "중간고사 프로젝트",
            "description": "Spring Boot로 REST API 구현",
            "dueAt": "2025-11-15T23:59:59",
            "pointsPossible": 100,
            "submissionTypes": "online_upload",
            "createdAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "updatedAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        }

        # When: SQS에 메시지 발행 (Canvas-Sync-Lambda 역할)
        response = sqs_client.send_message(
            QueueUrl=assignment_queue_url,
            MessageBody=json.dumps(message)
        )

        assert response['MessageId'] is not None
        print(f"[OK] 메시지 발송 완료: {response['MessageId']}")

        # Then: course-service API를 통해 Assignment 생성 확인
        max_wait = 60  # 최대 60초 대기 (SQS long polling 고려)
        assignment_created = False
        api_url = f"http://localhost:8082/api/assignments/canvas/{message['canvasAssignmentId']}"

        for i in range(max_wait):
            try:
                api_response = requests.get(api_url, timeout=2)

                if api_response.status_code == 200:
                    result = api_response.json()
                    assignment_created = True
                    print(f"[OK] Assignment 저장 완료 (API 응답): {result}")

                    # 검증
                    assert result['canvasAssignmentId'] == message['canvasAssignmentId']
                    assert result['title'] == message['title']
                    assert result['description'] == message['description']
                    assert result['pointsPossible'] == message['pointsPossible']
                    assert result['submissionTypes'] == message['submissionTypes']
                    break
                elif api_response.status_code == 404:
                    # 아직 생성 안됨, 계속 대기
                    pass
            except requests.exceptions.RequestException:
                # 네트워크 오류, 재시도
                pass

            time.sleep(1)

        assert assignment_created, "[ERROR] Assignment가 60초 내에 생성되지 않음 (API 확인)"

    @pytest.mark.usefixtures("wait_for_services", "clean_sqs_queue", "test_course")
    def test_assignment_update_flow(
        self, sqs_client, assignment_queue_url, mysql_connection, test_course
    ):
        """
        시나리오: 기존 과제가 수정되었을 때 UPDATE 이벤트 처리
        """
        # Given: 먼저 Assignment 생성
        cursor = mysql_connection.cursor()
        cursor.execute("""
            INSERT INTO assignments (
                canvas_assignment_id, course_id, title, description,
                due_at, points_possible, submission_types, created_at, updated_at
            ) VALUES (
                123456, %s, '기존 제목', '기존 설명',
                '2025-11-10 23:59:59', 80, 'online_text_entry', NOW(), NOW()
            )
        """, (test_course['id'],))
        mysql_connection.commit()
        cursor.close()

        # When: UPDATE 메시지 발행
        update_message = {
            "eventType": "ASSIGNMENT_UPDATED",
            "canvasAssignmentId": 123456,
            "canvasCourseId": test_course['canvas_course_id'],
            "title": "수정된 제목",
            "description": "수정된 설명",
            "dueAt": "2025-11-20T23:59:59",
            "pointsPossible": 100,
            "submissionTypes": "online_upload",
            "createdAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "updatedAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        }

        sqs_client.send_message(
            QueueUrl=assignment_queue_url,
            MessageBody=json.dumps(update_message)
        )

        # Then: 업데이트 반영 확인
        time.sleep(5)  # 처리 대기

        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute(
            "SELECT * FROM assignments WHERE canvas_assignment_id = %s",
            (123456,)
        )
        result = cursor.fetchone()
        cursor.close()

        assert result is not None
        assert result['title'] == "수정된 제목"
        assert result['description'] == "수정된 설명"
        assert result['points_possible'] == 100
        assert result['submission_types'] == "online_upload"
        print(f"[OK] Assignment 업데이트 완료: {result}")

    @pytest.mark.usefixtures("wait_for_services", "clean_sqs_queue", "test_course")
    def test_duplicate_assignment_ignored(
        self, sqs_client, assignment_queue_url, mysql_connection, test_course
    ):
        """
        시나리오: 중복된 Assignment 생성 시도 시 무시됨
        """
        # Given: Assignment 먼저 생성
        cursor = mysql_connection.cursor()
        cursor.execute("""
            INSERT INTO assignments (
                canvas_assignment_id, course_id, title, description,
                due_at, points_possible, submission_types, created_at, updated_at
            ) VALUES (
                123456, %s, '원본 과제', '원본 설명',
                '2025-11-15 23:59:59', 100, 'online_upload', NOW(), NOW()
            )
        """, (test_course['id'],))
        mysql_connection.commit()
        cursor.close()

        # When: 동일한 canvas_assignment_id로 CREATE 메시지 발행
        duplicate_message = {
            "eventType": "ASSIGNMENT_CREATED",
            "canvasAssignmentId": 123456,
            "canvasCourseId": test_course['canvas_course_id'],
            "title": "중복 과제",
            "description": "중복 설명",
            "dueAt": "2025-11-15T23:59:59",
            "pointsPossible": 100,
            "submissionTypes": "online_upload",
            "createdAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "updatedAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        }

        sqs_client.send_message(
            QueueUrl=assignment_queue_url,
            MessageBody=json.dumps(duplicate_message)
        )

        # Then: 기존 데이터가 유지되고 개수가 증가하지 않음
        time.sleep(5)

        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute("SELECT COUNT(*) as count FROM assignments WHERE canvas_assignment_id = 123456")
        count_result = cursor.fetchone()
        assert count_result['count'] == 1, "중복 Assignment가 생성됨"

        cursor.execute("SELECT * FROM assignments WHERE canvas_assignment_id = 123456")
        result = cursor.fetchone()
        assert result['title'] == "원본 과제", "기존 데이터가 변경됨"
        cursor.close()

        print(f"[OK] 중복 Assignment 무시 완료")

    @pytest.mark.usefixtures("wait_for_services", "clean_sqs_queue")
    def test_assignment_without_course_fails(
        self, sqs_client, assignment_queue_url, mysql_connection
    ):
        """
        시나리오: Course가 없는 Assignment 생성 시도 → 실패 (DLQ 이동 대상)
        """
        # Given: Course 없이 Assignment 메시지 발행
        message = {
            "eventType": "ASSIGNMENT_CREATED",
            "canvasAssignmentId": 999999,
            "canvasCourseId": 99999,  # 존재하지 않는 Course
            "title": "잘못된 과제",
            "description": "Course가 없는 과제",
            "dueAt": "2025-11-15T23:59:59",
            "pointsPossible": 100,
            "submissionTypes": "online_upload",
            "createdAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "updatedAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        }

        # When: SQS에 메시지 발행
        sqs_client.send_message(
            QueueUrl=assignment_queue_url,
            MessageBody=json.dumps(message)
        )

        # Then: Assignment가 생성되지 않음
        time.sleep(5)

        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute("SELECT * FROM assignments WHERE canvas_assignment_id = 999999")
        result = cursor.fetchone()
        cursor.close()

        assert result is None, "Course 없는 Assignment가 생성됨"
        print(f"[OK] Course 없는 Assignment 생성 실패 (정상 동작)")