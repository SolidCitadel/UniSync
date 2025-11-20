"""
Canvas Sync Lambda Handler (Phase 1: Manual Sync)
Fetch courses and assignments from Canvas API and send to SQS
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


def lambda_handler(event, context):
    """
    Canvas 동기화 핸들러 (Phase 1/2/3 공통)

    호출자:
    - Phase 1: Spring (AWS SDK invoke) - 직접 호출
    - Phase 2: EventBridge → Dispatcher Lambda → 이 Lambda
    - Phase 3: 동일

    Input: {"cognitoSub": "abc-123-def-456"}
    Output: {
        "statusCode": 200,
        "body": {
            "coursesCount": 5,
            "assignmentsCount": 23,
            "syncedAt": "2025-11-20T12:00:00Z"
        }
    }
    """
    try:
        # 1. 입력 정규화 (호출자별 형식 차이 흡수)
        cognito_sub = extract_cognito_sub(event)

        print(f"🚀 Canvas sync started for cognitoSub={cognito_sub}")

        # 2. User-Service에서 Canvas Token 조회 (복호화됨)
        canvas_token = get_canvas_token(cognito_sub)

        # 3. Canvas API: 사용자의 전체 Course 조회
        courses = fetch_user_courses(canvas_token)
        total_assignments = 0

        print(f"  - Fetched {len(courses)} courses")

        # 4. 각 Course 처리
        for course in courses:
            # 4-1. Course 데이터 SQS 발행
            send_to_sqs('lambda-to-courseservice-enrollments', {
                'cognitoSub': cognito_sub,
                'canvasCourseId': course['id'],
                'courseName': course['name'],
                'courseCode': course.get('course_code', ''),
                'workflowState': course.get('workflow_state', 'available'),
                'startAt': course.get('start_at'),
                'endAt': course.get('end_at'),
                'publishedAt': datetime.utcnow().isoformat()
            })

            # 4-2. 해당 Course의 Assignments 조회
            assignments = fetch_canvas_assignments(canvas_token, str(course['id']))
            total_assignments += len(assignments)

            print(f"  - Course {course['id']}: {len(assignments)} assignments")

            # 4-3. 각 Assignment 데이터 SQS 발행
            for assignment in assignments:
                submission_types = assignment.get('submission_types', [])
                submission_types_str = ','.join(submission_types) if submission_types else ''

                due_at = assignment.get('due_at')
                due_at_formatted = None
                if due_at:
                    # ISO 8601 (2025-11-15T23:59:00Z) → LocalDateTime
                    due_at_formatted = due_at.replace('Z', '').split('.')[0]

                created_at = assignment.get('created_at')
                created_at_formatted = None
                if created_at:
                    created_at_formatted = created_at.replace('Z', '').split('.')[0]

                updated_at = assignment.get('updated_at')
                updated_at_formatted = None
                if updated_at:
                    updated_at_formatted = updated_at.replace('Z', '').split('.')[0]

                send_to_sqs('lambda-to-courseservice-assignments', {
                    'eventType': 'ASSIGNMENT_CREATED',
                    'canvasCourseId': course['id'],
                    'canvasAssignmentId': assignment['id'],
                    'title': assignment['name'],
                    'description': assignment.get('description', ''),
                    'dueAt': due_at_formatted,
                    'pointsPossible': assignment.get('points_possible'),
                    'submissionTypes': submission_types_str,
                    'htmlUrl': assignment.get('html_url'),
                    'createdAt': created_at_formatted,
                    'updatedAt': updated_at_formatted
                })

        print(f"✅ Canvas sync completed: {len(courses)} courses, {total_assignments} assignments")

        # 5. 동기 응답 (Spring은 즉시 사용, EventBridge는 무시)
        return {
            'statusCode': 200,
            'body': {
                'coursesCount': len(courses),
                'assignmentsCount': total_assignments,
                'syncedAt': datetime.utcnow().isoformat()
            }
        }

    except Exception as e:
        print(f"❌ Error in lambda_handler: {str(e)}")
        raise


def extract_cognito_sub(event: Dict[str, Any]) -> str:
    """
    호출자별 입력 형식 정규화

    지원 형식:
    - 직접 호출 (Phase 1: Spring, Dispatcher Lambda): {"cognitoSub": "..."}
    - EventBridge (Phase 2): {"detail": {"cognitoSub": "..."}}
    - SQS (옵션): {"Records": [{"body": "{"cognitoSub": "..."}"}]}
    """
    # EventBridge 형식 (Phase 2)
    if 'detail' in event:
        return event['detail']['cognitoSub']

    # SQS 형식 (혹시 사용하는 경우)
    if 'Records' in event and len(event['Records']) > 0:
        body = json.loads(event['Records'][0]['body'])
        return body['cognitoSub']

    # 직접 호출 형식 (Phase 1: Spring, Dispatcher Lambda)
    return event['cognitoSub']


def get_canvas_token(cognito_sub: str) -> str:
    """User-Service 내부 API로 Canvas 토큰 조회 (복호화됨)"""
    url = f"{USER_SERVICE_URL}/internal/v1/credentials/canvas/by-cognito-sub/{cognito_sub}"
    headers = {
        'X-Api-Key': os.environ.get('CANVAS_SYNC_API_KEY', 'local-dev-token')
    }

    response = requests.get(url, headers=headers, timeout=5)
    response.raise_for_status()

    data = response.json()
    return data['canvasToken']


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


def fetch_canvas_assignments(token: str, canvas_course_id: str) -> List[Dict[str, Any]]:
    """특정 Course의 Assignment 목록 가져오기"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/assignments"
    headers = {'Authorization': f'Bearer {token}'}

    response = requests.get(url, headers=headers, timeout=10)
    response.raise_for_status()

    return response.json()


def send_to_sqs(queue_name: str, message: Dict[str, Any]):
    """SQS 큐에 메시지 발행"""
    response = sqs.get_queue_url(QueueName=queue_name)
    queue_url = response['QueueUrl']

    sqs.send_message(
        QueueUrl=queue_url,
        MessageBody=json.dumps(message)
    )

    print(f"  -> SQS sent: {queue_name}")
