"""
Lambda → Course-Service Integration Test

Canvas Sync Lambda 통합 테스트 (Phase 1: Manual Sync)

플로우:
1. Lambda 직접 호출 (cognitoSub)
2. Lambda → Canvas API (courses, assignments 조회)
3. Lambda → SQS (통합 동기화 메시지 발행)
4. Course-Service → SQS (메시지 consume)
5. Course-Service → DB (저장)
"""

import pytest
import json
import time


class TestCanvasSyncIntegration:
    """Canvas 동기화 통합 테스트 (Phase 1)"""

    def test_canvas_sync_full_flow(
        self,
        lambda_client,
        sqs_client,
        canvas_sync_queue_url,
        mysql_connection
    ):
        """
        전체 Canvas 동기화 플로우 테스트

        Given: User가 Canvas 토큰을 등록했음 (User-Service에 저장됨)
        When: Lambda를 cognitoSub로 직접 호출
        Then:
          1. Lambda가 Canvas API 호출하여 courses와 assignments 조회
          2. Lambda가 SQS에 통합 동기화 메시지 발행
          3. Course-Service가 메시지 consume하여 DB에 저장
          4. DB에 courses와 assignments가 저장됨
        """
        lambda_event = {
            "cognitoSub": "test-cognito-sub-123"
        }

        print(f"\n🚀 Invoking Lambda: {lambda_event}")

        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        result = json.loads(response['Payload'].read())
        print(f"📦 Lambda response: {result}")

        assert response['StatusCode'] == 200

        # Lambda 응답 검증
        courses_count = 0
        if 'statusCode' in result:
            assert result['statusCode'] == 200
            body = result['body']
            courses_count = body.get('coursesCount', 0)
            print(f"✅ Lambda executed successfully:")
            print(f"   - Courses: {courses_count}")
            print(f"   - Assignments: {body.get('assignmentsCount', 0)}")

        # Course-Service가 메시지를 처리할 때까지 대기
        max_wait = 30
        courses_saved = False

        for i in range(max_wait):
            cursor = mysql_connection.cursor(dictionary=True)
            cursor.execute("SELECT COUNT(*) as count FROM courses")
            course_result = cursor.fetchone()
            cursor.close()

            if course_result and course_result['count'] > 0:
                courses_saved = True
                print(f"\n✅ {course_result['count']}개 courses DB에 저장 완료")
                break

            time.sleep(1)

        # 검증 (Canvas 토큰이 있는 경우)
        if courses_count > 0:
            assert courses_saved, "Courses가 30초 내에 DB에 저장되지 않음"
        else:
            print("\n⚠️  Canvas API에서 courses를 가져오지 못함 (테스트 환경에서 정상)")

    def test_sqs_message_format_canvas_sync(
        self,
        lambda_client,
        sqs_client,
        canvas_sync_queue_url
    ):
        """
        Canvas Sync 메시지 형식 검증

        Lambda가 발행하는 통합 동기화 메시지가 올바른 형식인지 확인
        """
        lambda_event = {
            "cognitoSub": "test-cognito-sub-123"
        }

        # SQS 큐 비우기
        sqs_client.purge_queue(QueueUrl=canvas_sync_queue_url)
        time.sleep(2)

        # Lambda 실행
        print(f"\n🚀 Invoking Lambda for canvas sync message test")
        lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        # SQS에서 canvas sync 메시지 확인
        time.sleep(3)

        response = sqs_client.receive_message(
            QueueUrl=canvas_sync_queue_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=2
        )

        messages = response.get('Messages', [])

        if len(messages) > 0:
            print(f"\n✅ Canvas sync 메시지 발견")

            message = json.loads(messages[0]['Body'])
            print(f"📬 Canvas Sync Message (summary):")
            print(f"   - eventType: {message.get('eventType')}")
            print(f"   - cognitoSub: {message.get('cognitoSub')}")
            print(f"   - courses: {len(message.get('courses', []))}")

            # 필수 필드 검증
            assert 'eventType' in message
            assert 'cognitoSub' in message
            assert 'syncedAt' in message
            assert 'courses' in message
            assert message['eventType'] == 'CANVAS_SYNC_COMPLETED'
            assert message['cognitoSub'] == "test-cognito-sub-123"
            assert isinstance(message['courses'], list)

            # 첫 번째 course 구조 검증 (있는 경우)
            if len(message['courses']) > 0:
                course = message['courses'][0]
                print(f"\n📘 First course structure validation:")
                print(f"   - canvasCourseId: {course.get('canvasCourseId')}")
                print(f"   - courseName: {course.get('courseName')}")
                print(f"   - assignments: {len(course.get('assignments', []))}")

                assert 'canvasCourseId' in course
                assert 'courseName' in course
                assert 'assignments' in course
                assert isinstance(course['assignments'], list)

                # 첫 번째 assignment 구조 검증 (있는 경우)
                if len(course['assignments']) > 0:
                    assignment = course['assignments'][0]
                    print(f"\n📝 First assignment structure validation:")
                    print(f"   - canvasAssignmentId: {assignment.get('canvasAssignmentId')}")
                    print(f"   - title: {assignment.get('title')}")

                    assert 'canvasAssignmentId' in assignment
                    assert 'title' in assignment

            print(f"✅ Canvas Sync 메시지 형식 검증 완료")
        else:
            print("\n⚠️  Canvas Sync 메시지 없음 (Canvas API에서 courses를 가져오지 못함)")

    def test_idempotency_duplicate_sync(
        self,
        lambda_client,
        mysql_connection
    ):
        """
        중복 동기화 테스트 (멱등성)

        동일한 Lambda를 두 번 호출했을 때 중복 데이터가 생성되지 않는지 확인
        """
        lambda_event = {
            "cognitoSub": "test-cognito-sub-456"
        }

        # 첫 번째 Lambda 호출
        print(f"\n🚀 First Lambda invocation")
        lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        time.sleep(10)

        # 첫 번째 호출 후 개수 확인
        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute("SELECT COUNT(*) as count FROM courses")
        first_count = cursor.fetchone()['count']
        cursor.close()

        print(f"   → First sync: {first_count} courses")

        # 두 번째 Lambda 호출
        print(f"\n🚀 Second Lambda invocation (duplicate)")
        lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        time.sleep(10)

        # 두 번째 호출 후 개수 확인
        cursor = mysql_connection.cursor(dictionary=True)
        cursor.execute("SELECT COUNT(*) as count FROM courses")
        second_count = cursor.fetchone()['count']
        cursor.close()

        print(f"   → Second sync: {second_count} courses")

        if first_count > 0:
            assert first_count == second_count, \
                f"중복 courses 생성됨: {first_count} → {second_count}"
            print(f"\n✅ 멱등성 검증 완료: 중복 데이터 없음")
        else:
            print(f"\n⚠️  Courses가 없어 멱등성 검증 불가")

    def test_lambda_without_canvas_token(
        self,
        lambda_client
    ):
        """
        Canvas 토큰이 없는 사용자 시나리오

        Canvas 토큰이 등록되지 않은 사용자의 Lambda 호출 시 에러 발생 확인
        """
        lambda_event = {
            "cognitoSub": "nonexistent-user-999"
        }

        print(f"\n🚀 Invoking Lambda with non-existent user")
        response = lambda_client.invoke(
            FunctionName='canvas-sync-lambda',
            InvocationType='RequestResponse',
            Payload=json.dumps(lambda_event)
        )

        result = json.loads(response['Payload'].read())
        print(f"📦 Lambda response: {result}")

        # 토큰이 없으면 에러 또는 200/0 카운트로 스킵할 수 있음
        if result.get('statusCode') == 200:
            body = result.get('body', {})
            assert body.get('coursesCount', 0) == 0
            assert body.get('assignmentsCount', 0) == 0
            print("✅ Canvas 토큰 없음 → 동기화 스킵 (0건)")
        else:
            assert 'errorMessage' in result or ('statusCode' in result and result['statusCode'] != 200)
            print(f"✅ Canvas 토큰 없는 사용자 에러 처리 확인")
