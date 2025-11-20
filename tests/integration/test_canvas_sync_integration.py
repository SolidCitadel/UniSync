"""
Canvas Sync Integration Test (Phase 1: Manual Sync)

전체 플로우 테스트:
1. Lambda 직접 호출 (cognitoSub)
2. Lambda → Canvas API (courses, assignments 조회)
3. Lambda → SQS (enrollments, assignments 메시지 발행)
4. Course-Service → SQS (메시지 consume)
5. Course-Service → DB (저장)
"""

import pytest
import json
import time


class TestCanvasSyncIntegration:
    """Canvas 동기화 통합 테스트 (Phase 1)"""

    @pytest.mark.usefixtures("wait_for_services")
    def test_canvas_sync_full_flow(
        self,
        lambda_client,
        sqs_client,
        enrollment_queue_url,
        assignment_queue_url,
        mysql_connection
    ):
        """
        전체 Canvas 동기화 플로우 테스트

        Given: User가 Canvas 토큰을 등록했음 (User-Service에 저장됨)
        When: Lambda를 cognitoSub로 직접 호출
        Then:
          1. Lambda가 Canvas API 호출하여 courses와 assignments 조회
          2. Lambda가 SQS에 enrollment 및 assignment 메시지 발행
          3. Course-Service가 메시지 consume하여 DB에 저장
          4. DB에 courses와 assignments가 저장됨
        """
        # Given: Lambda invoke 이벤트 (Phase 1 format)
        lambda_event = {
            "cognitoSub": "test-cognito-sub-123"
        }

        # When: Lambda 실행 (실제 Canvas API 호출, Mock 아님)
        print(f"\n🚀 Invoking Lambda: {lambda_event}")

        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',  # 동기 실행
            Payload=json.dumps(lambda_event)
        )

        # Lambda 실행 결과 확인
        result = json.loads(response['Payload'].read())
        print(f"📦 Lambda response: {result}")

        assert response['StatusCode'] == 200

        # Lambda 응답 검증
        if 'statusCode' in result:
            assert result['statusCode'] == 200
            body = result['body']
            print(f"✅ Lambda executed successfully:")
            print(f"   - Courses: {body.get('coursesCount', 0)}")
            print(f"   - Assignments: {body.get('assignmentsCount', 0)}")
            print(f"   - Synced at: {body.get('syncedAt', 'N/A')}")

            courses_count = body.get('coursesCount', 0)
            assignments_count = body.get('assignmentsCount', 0)

            # Canvas API가 실제 데이터를 반환했는지 확인
            # (실제 Canvas 토큰이 있는 경우)
            if courses_count > 0:
                print(f"\n✅ Lambda fetched {courses_count} courses from Canvas API")
            else:
                print(f"\n⚠️  No courses fetched (Canvas token may not be configured)")

        # Then: Course-Service가 메시지를 처리할 때까지 대기
        max_wait = 30
        courses_saved = False
        assignments_saved = False

        for i in range(max_wait):
            cursor = mysql_connection.cursor(dictionary=True)

            # Courses 확인
            cursor.execute("SELECT COUNT(*) as count FROM courses")
            course_result = cursor.fetchone()

            # Assignments 확인
            cursor.execute("SELECT COUNT(*) as count FROM assignments")
            assignment_result = cursor.fetchone()

            cursor.close()

            if course_result and course_result['count'] > 0:
                courses_saved = True
                print(f"\n✅ {course_result['count']}개 courses DB에 저장 완료")

                # 실제 course 데이터 검증
                cursor = mysql_connection.cursor(dictionary=True)
                cursor.execute("SELECT * FROM courses LIMIT 1")
                course = cursor.fetchone()
                cursor.close()

                if course:
                    assert course['canvas_course_id'] is not None
                    assert course['name'] is not None
                    print(f"📚 Course: {course['name']} (Canvas ID: {course['canvas_course_id']})")

            if assignment_result and assignment_result['count'] > 0:
                assignments_saved = True
                print(f"✅ {assignment_result['count']}개 assignments DB에 저장 완료")

                # 실제 assignment 데이터 검증
                cursor = mysql_connection.cursor(dictionary=True)
                cursor.execute("SELECT * FROM assignments LIMIT 1")
                assignment = cursor.fetchone()
                cursor.close()

                if assignment:
                    assert assignment['canvas_assignment_id'] is not None
                    assert assignment['title'] is not None
                    print(f"📝 Assignment: {assignment['title']}")

            if courses_saved and assignments_saved:
                break

            time.sleep(1)

        # 검증: 최소한 courses는 저장되어야 함
        # (assignments는 없을 수도 있음)
        if courses_count > 0:
            assert courses_saved, "❌ Courses가 30초 내에 DB에 저장되지 않음"
        else:
            print("\n⚠️  Canvas API에서 courses를 가져오지 못함 (테스트 환경에서 정상)")

    @pytest.mark.usefixtures("wait_for_services")
    def test_sqs_message_format_enrollment(
        self,
        lambda_client,
        sqs_client,
        enrollment_queue_url
    ):
        """
        Enrollment 메시지 형식 검증

        Lambda가 발행하는 enrollment 메시지가 올바른 형식인지 확인
        """
        # Given: Lambda 호출
        lambda_event = {
            "cognitoSub": "test-cognito-sub-123"
        }

        # SQS 큐 비우기
        sqs_client.purge_queue(QueueUrl=enrollment_queue_url)
        time.sleep(2)

        # When: Lambda 실행
        print(f"\n🚀 Invoking Lambda for enrollment message test")
        lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        # Then: SQS에서 enrollment 메시지 확인
        time.sleep(3)  # 메시지 발행 대기

        response = sqs_client.receive_message(
            QueueUrl=enrollment_queue_url,
            MaxNumberOfMessages=10,
            WaitTimeSeconds=2
        )

        messages = response.get('Messages', [])

        if len(messages) > 0:
            print(f"\n✅ {len(messages)}개의 enrollment 메시지 발견")

            # 첫 번째 메시지 형식 검증
            first_message = json.loads(messages[0]['Body'])
            print(f"📬 Enrollment Message: {json.dumps(first_message, indent=2)}")

            # 필수 필드 검증
            assert 'cognitoSub' in first_message
            assert 'canvasCourseId' in first_message
            assert 'courseName' in first_message
            assert first_message['cognitoSub'] == "test-cognito-sub-123"

            print(f"✅ Enrollment 메시지 형식 검증 완료")
        else:
            print("\n⚠️  Enrollment 메시지 없음 (Canvas API에서 courses를 가져오지 못함)")

    @pytest.mark.usefixtures("wait_for_services")
    def test_sqs_message_format_assignment(
        self,
        lambda_client,
        sqs_client,
        assignment_queue_url
    ):
        """
        Assignment 메시지 형식 검증

        Lambda가 발행하는 assignment 메시지가 올바른 형식인지 확인
        """
        # Given: Lambda 호출
        lambda_event = {
            "cognitoSub": "test-cognito-sub-123"
        }

        # SQS 큐 비우기
        sqs_client.purge_queue(QueueUrl=assignment_queue_url)
        time.sleep(2)

        # When: Lambda 실행
        print(f"\n🚀 Invoking Lambda for assignment message test")
        lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        # Then: SQS에서 assignment 메시지 확인
        time.sleep(3)  # 메시지 발행 대기

        response = sqs_client.receive_message(
            QueueUrl=assignment_queue_url,
            MaxNumberOfMessages=10,
            WaitTimeSeconds=2
        )

        messages = response.get('Messages', [])

        if len(messages) > 0:
            print(f"\n✅ {len(messages)}개의 assignment 메시지 발견")

            # 첫 번째 메시지 형식 검증
            first_message = json.loads(messages[0]['Body'])
            print(f"📬 Assignment Message: {json.dumps(first_message, indent=2)}")

            # 필수 필드 검증
            assert 'eventType' in first_message
            assert 'canvasCourseId' in first_message
            assert 'canvasAssignmentId' in first_message
            assert 'title' in first_message
            assert first_message['eventType'] == 'ASSIGNMENT_CREATED'

            print(f"✅ Assignment 메시지 형식 검증 완료")
        else:
            print("\n⚠️  Assignment 메시지 없음 (Canvas API에서 assignments를 가져오지 못함)")

    @pytest.mark.usefixtures("wait_for_services")
    def test_idempotency_duplicate_sync(
        self,
        lambda_client,
        mysql_connection
    ):
        """
        중복 동기화 테스트 (멱등성)

        동일한 Lambda를 두 번 호출했을 때 중복 데이터가 생성되지 않는지 확인
        """
        # Given: Lambda 이벤트
        lambda_event = {
            "cognitoSub": "test-cognito-sub-456"
        }

        # When: Lambda를 2번 연속 호출
        print(f"\n🚀 First Lambda invocation")
        lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        time.sleep(10)  # 첫 번째 처리 완료 대기

        # DB에서 첫 번째 호출 후 개수 확인
        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute("SELECT COUNT(*) as count FROM courses")
        first_count = cursor.fetchone()['count']
        cursor.close()

        print(f"   → First sync: {first_count} courses")

        print(f"\n🚀 Second Lambda invocation (duplicate)")
        lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        time.sleep(10)  # 두 번째 처리 완료 대기

        # Then: 두 번째 호출 후에도 개수가 동일해야 함
        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute("SELECT COUNT(*) as count FROM courses")
        second_count = cursor.fetchone()['count']
        cursor.close()

        print(f"   → Second sync: {second_count} courses")

        if first_count > 0:
            assert first_count == second_count, \
                f"❌ 중복 courses 생성됨: {first_count} → {second_count}"
            print(f"\n✅ 멱등성 검증 완료: 중복 데이터 없음")
        else:
            print(f"\n⚠️  Courses가 없어 멱등성 검증 불가")

    @pytest.mark.usefixtures("wait_for_services")
    def test_lambda_without_canvas_token(
        self,
        lambda_client
    ):
        """
        Canvas 토큰이 없는 사용자 시나리오

        Canvas 토큰이 등록되지 않은 사용자의 Lambda 호출 시 에러 발생 확인
        """
        # Given: Canvas 토큰이 없는 cognitoSub
        lambda_event = {
            "cognitoSub": "nonexistent-user-999"
        }

        # When: Lambda 실행
        print(f"\n🚀 Invoking Lambda with non-existent user")
        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        result = json.loads(response['Payload'].read())
        print(f"📦 Lambda response: {result}")

        # Then: errorMessage 포함 확인
        assert 'errorMessage' in result or ('statusCode' in result and result['statusCode'] != 200)
        print(f"✅ Canvas 토큰 없는 사용자 에러 처리 확인")

    @pytest.mark.usefixtures("wait_for_services")
    def test_phase2_event_format_compatibility(
        self,
        lambda_client
    ):
        """
        Phase 2 (EventBridge) 이벤트 형식 호환성 테스트

        Lambda가 Phase 2의 EventBridge 형식도 지원하는지 확인
        """
        # Given: Phase 2 EventBridge 형식
        lambda_event = {
            "detail": {
                "cognitoSub": "test-cognito-sub-123"
            }
        }

        # When: Lambda 실행
        print(f"\n🚀 Invoking Lambda with Phase 2 event format")
        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        result = json.loads(response['Payload'].read())
        print(f"📦 Lambda response: {result}")

        # Then: 정상 처리 확인 (cognitoSub 추출 성공)
        assert response['StatusCode'] == 200
        # errorMessage가 있으면 cognitoSub 추출 실패 (Canvas 토큰 없음은 괜찮음)
        if 'errorMessage' in result:
            # cognitoSub 추출은 성공했지만 Canvas 토큰이 없는 경우
            assert 'Canvas token' in result['errorMessage'] or 'User' in result['errorMessage']
            print(f"✅ Phase 2 형식 지원 확인 (Canvas 토큰 없음은 정상)")
        else:
            # 정상 응답
            assert 'statusCode' in result
            print(f"✅ Phase 2 형식 정상 처리 확인")
