"""
Canvas Sync Lambda Handler
Fetch assignments/announcements/submissions from Canvas API and send to SQS
"""

import json
import os
import boto3
import requests
from datetime import datetime
from typing import Dict, List, Any

# Environment variables
USER_SERVICE_URL = os.environ.get('USER_SERVICE_URL', 'http://localhost:8081')
CANVAS_API_BASE_URL = os.environ.get('CANVAS_API_BASE_URL', 'https://canvas.instructure.com/api/v1')
AWS_REGION = os.environ.get('AWS_REGION', 'ap-northeast-2')
SQS_ENDPOINT = os.environ.get('SQS_ENDPOINT', None)  # For LocalStack

# SQS client
sqs = boto3.client('sqs', region_name=AWS_REGION, endpoint_url=SQS_ENDPOINT)


def initial_sync_handler(event, context):
    """
    사용자의 Canvas 토큰 등록 시 최초 동기화

    Input (from user-token-registered-queue):
        {
            "cognitoSub": "abc-123-def-456",
            "provider": "CANVAS",
            "registeredAt": "2025-11-05T12:00:00Z",
            "externalUserId": "99040",
            "externalUsername": "2021105636"
        }

    Output:
        - course-enrollment-queue에 사용자의 전체 Course 발행
    """
    try:
        # SQS event에서 메시지 파싱
        records = event.get('Records', [])
        if not records:
            print("No SQS records found in event")
            return {'statusCode': 200, 'body': 'No records'}

        for record in records:
            message_body = json.loads(record['body'])
            cognito_sub = message_body['cognitoSub']

            print(f"🚀 Initial sync started for cognitoSub={cognito_sub}")

            # 1. User-Service에서 Canvas 토큰 조회
            canvas_token = get_canvas_token(cognito_sub)

            # 2. Canvas API: 사용자의 전체 Course 조회
            courses = fetch_user_courses(canvas_token)
            print(f"  - Fetched {len(courses)} courses")

            # 3. 각 Course마다 SQS 이벤트 발행
            for course in courses:
                send_to_sqs('course-enrollment-queue', {
                    'eventType': 'COURSE_ENROLLMENT',
                    'cognitoSub': cognito_sub,
                    'canvasCourseId': course['id'],
                    'courseName': course['name'],
                    'courseCode': course.get('course_code', ''),
                    'workflowState': course.get('workflow_state', 'available'),
                    'startAt': course.get('start_at'),
                    'endAt': course.get('end_at'),
                    'publishedAt': datetime.utcnow().isoformat()
                })

            print(f"✅ Initial sync completed: {len(courses)} courses published")

        return {
            'statusCode': 200,
            'body': json.dumps({'message': 'Initial sync completed'})
        }

    except Exception as e:
        print(f"Error in initial_sync_handler: {str(e)}")
        raise


def assignment_sync_handler(event, context):
    """
    새 Course가 등록되면 Assignment 동기화 수행

    Input (from assignment-sync-needed-queue):
        {
            "courseId": 123,
            "canvasCourseId": 789,
            "leaderCognitoSub": "abc-123-def-456"
        }

    Output:
        - assignment-events-queue에 각 Assignment 발행
    """
    try:
        # SQS event에서 메시지 파싱
        records = event.get('Records', [])
        if not records:
            print("No SQS records found in event")
            return {'statusCode': 200, 'body': 'No records'}

        for record in records:
            message_body = json.loads(record['body'])
            course_id = message_body['courseId']
            canvas_course_id = message_body['canvasCourseId']
            leader_cognito_sub = message_body['leaderCognitoSub']

            print(f"📥 Assignment sync started: courseId={course_id}, canvasCourseId={canvas_course_id}")

            # 1. User-Service에서 Leader의 Canvas 토큰 조회
            canvas_token = get_canvas_token(leader_cognito_sub)

            # 2. Canvas API: 해당 Course의 Assignments 조회
            assignments = fetch_canvas_assignments(canvas_token, str(canvas_course_id))
            print(f"  - Fetched {len(assignments)} assignments")

            # 3. 각 Assignment마다 SQS 이벤트 발행
            for assignment in assignments:
                send_to_sqs('assignment-events-queue', {
                    'eventType': 'ASSIGNMENT_CREATED',
                    'courseId': course_id,
                    'canvasAssignmentId': assignment['id'],
                    'title': assignment['name'],
                    'description': assignment.get('description', ''),
                    'dueAt': assignment.get('due_at'),
                    'pointsPossible': assignment.get('points_possible'),
                    'submissionTypes': assignment.get('submission_types', []),
                    'htmlUrl': assignment.get('html_url'),
                    'publishedAt': datetime.utcnow().isoformat()
                })

            print(f"✅ Assignment sync completed: {len(assignments)} assignments published")

        return {
            'statusCode': 200,
            'body': json.dumps({'message': 'Assignment sync completed'})
        }

    except Exception as e:
        print(f"Error in assignment_sync_handler: {str(e)}")
        raise


def lambda_handler(event, context):
    """
    Main handler called from Step Functions (기존 Assignment sync용)

    Input:
        {
            "courseId": 123,
            "canvasCourseId": "canvas_456",
            "leaderUserId": 5,
            "lastSyncedAt": "2025-10-29T12:00:00Z"
        }
    """
    try:
        course_id = event['courseId']
        canvas_course_id = event['canvasCourseId']
        leader_user_id = event['leaderUserId']
        last_synced_at = event.get('lastSyncedAt')

        print(f"Canvas sync started: courseId={course_id}, canvasCourseId={canvas_course_id}")

        # 1. Retrieve Leader's Canvas token
        canvas_token = get_canvas_token(leader_user_id)

        # 2. Call Canvas API: Fetch Assignments
        assignments = fetch_canvas_assignments(canvas_token, canvas_course_id, last_synced_at)
        print(f"  - Assignments fetched: {len(assignments)}")

        # 3. Call Canvas API: Fetch Announcements
        announcements = fetch_canvas_announcements(canvas_token, canvas_course_id, last_synced_at)
        print(f"  - Announcements fetched: {len(announcements)}")

        # 4. Call Canvas API: Fetch Submissions (per student)
        submissions = fetch_canvas_submissions(canvas_token, canvas_course_id, leader_user_id)
        print(f"  - Submissions fetched: {len(submissions)}")

        # 5. Send events to SQS
        sent_count = 0

        for assignment in assignments:
            send_to_sqs('assignment-events-queue', {
                'eventType': 'ASSIGNMENT_CREATED',
                'courseId': course_id,
                'canvasAssignmentId': assignment['id'],
                'title': assignment['name'],
                'description': assignment.get('description', ''),
                'dueAt': assignment.get('due_at'),
                'pointsPossible': assignment.get('points_possible'),
                'submissionTypes': assignment.get('submission_types', []),
                'htmlUrl': assignment.get('html_url')
            })
            sent_count += 1

        for submission in submissions:
            send_to_sqs('submission-events-queue', {
                'eventType': 'SUBMISSION_DETECTED',
                'userId': leader_user_id,
                'courseId': course_id,
                'assignmentId': submission['assignment_id'],
                'submissionMetadata': {
                    'submittedAt': submission.get('submitted_at'),
                    'attachments': submission.get('attachments', []),
                    'submissionType': submission.get('submission_type')
                }
            })
            sent_count += 1

        print(f"Sync completed: {sent_count} events sent")

        return {
            'statusCode': 200,
            'body': {
                'courseId': course_id,
                'assignmentsCount': len(assignments),
                'announcementsCount': len(announcements),
                'submissionsCount': len(submissions),
                'eventsSent': sent_count,
                'syncedAt': datetime.utcnow().isoformat()
            }
        }

    except Exception as e:
        print(f"Error occurred: {str(e)}")
        raise


def get_canvas_token(cognito_sub: str) -> str:
    """Call User-Service API to retrieve Canvas token (decrypted) by cognitoSub"""
    url = f"{USER_SERVICE_URL}/api/v1/credentials/canvas/by-cognito-sub/{cognito_sub}"
    headers = {
        'X-Api-Key': os.environ.get('CANVAS_SYNC_API_KEY', 'local-dev-token')
    }

    response = requests.get(url, headers=headers, timeout=5)
    response.raise_for_status()

    data = response.json()
    return data['canvasToken']


def fetch_canvas_assignments(token: str, canvas_course_id: str, since: str = None) -> List[Dict[str, Any]]:
    """Fetch assignment list from Canvas API"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/assignments"
    headers = {'Authorization': f'Bearer {token}'}
    params = {}

    # Incremental sync: based on updated_at
    if since:
        params['updated_since'] = since

    response = requests.get(url, headers=headers, params=params, timeout=10)
    response.raise_for_status()

    return response.json()


def fetch_canvas_announcements(token: str, canvas_course_id: str, since: str = None) -> List[Dict[str, Any]]:
    """Fetch announcements from Canvas API"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/discussion_topics"
    headers = {'Authorization': f'Bearer {token}'}
    params = {'only_announcements': 'true'}

    if since:
        params['updated_since'] = since

    response = requests.get(url, headers=headers, params=params, timeout=10)
    response.raise_for_status()

    return response.json()


def fetch_canvas_submissions(token: str, canvas_course_id: str, user_id: int) -> List[Dict[str, Any]]:
    """Fetch submissions from Canvas API"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/students/submissions"
    headers = {'Authorization': f'Bearer {token}'}
    params = {'student_ids[]': 'all', 'include[]': 'user'}

    response = requests.get(url, headers=headers, params=params, timeout=10)
    response.raise_for_status()

    # Filter only recently submitted items (TODO: actual comparison with DB needed)
    all_submissions = response.json()
    return [s for s in all_submissions if s.get('workflow_state') == 'submitted']


def fetch_user_courses(token: str) -> List[Dict[str, Any]]:
    """사용자가 수강 중인 Course 목록 가져오기"""
    url = f"{CANVAS_API_BASE_URL}/courses"
    headers = {'Authorization': f'Bearer {token}'}
    params = {
        'enrollment_type': 'student',
        'enrollment_state': 'active',
        'include[]': ['term', 'course_progress']
    }

    response = requests.get(url, headers=headers, params=params, timeout=10)
    response.raise_for_status()

    return response.json()


def send_to_sqs(queue_name: str, message: Dict[str, Any]):
    """Send message to SQS queue"""
    # Retrieve queue URL
    response = sqs.get_queue_url(QueueName=queue_name)
    queue_url = response['QueueUrl']

    # Send message
    sqs.send_message(
        QueueUrl=queue_url,
        MessageBody=json.dumps(message)
    )

    print(f"  -> SQS sent: {queue_name}")