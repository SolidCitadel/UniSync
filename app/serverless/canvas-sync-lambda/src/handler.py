"""
Canvas Sync Lambda Handler
Canvas API를 호출하여 과제/공지/제출물을 조회하고 SQS로 전송
"""

import json
import os
import boto3
import requests
from datetime import datetime
from typing import Dict, List, Any

# 환경 변수
USER_SERVICE_URL = os.environ.get('USER_SERVICE_URL', 'http://localhost:8081')
CANVAS_API_BASE_URL = os.environ.get('CANVAS_API_BASE_URL', 'https://canvas.instructure.com/api/v1')
AWS_REGION = os.environ.get('AWS_REGION', 'ap-northeast-2')
SQS_ENDPOINT = os.environ.get('SQS_ENDPOINT', None)  # LocalStack용

# SQS 클라이언트
sqs = boto3.client('sqs', region_name=AWS_REGION, endpoint_url=SQS_ENDPOINT)


def lambda_handler(event, context):
    """
    Step Functions에서 호출되는 메인 핸들러

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

        print(f"📚 Canvas 동기화 시작: courseId={course_id}, canvasCourseId={canvas_course_id}")

        # 1. Leader의 Canvas 토큰 조회
        canvas_token = get_canvas_token(leader_user_id)

        # 2. Canvas API 호출: Assignments 조회
        assignments = fetch_canvas_assignments(canvas_token, canvas_course_id, last_synced_at)
        print(f"  - 조회된 과제 수: {len(assignments)}")

        # 3. Canvas API 호출: Announcements 조회
        announcements = fetch_canvas_announcements(canvas_token, canvas_course_id, last_synced_at)
        print(f"  - 조회된 공지 수: {len(announcements)}")

        # 4. Canvas API 호출: Submissions 조회 (학생별)
        submissions = fetch_canvas_submissions(canvas_token, canvas_course_id, leader_user_id)
        print(f"  - 조회된 제출물 수: {len(submissions)}")

        # 5. SQS로 이벤트 전송
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

        print(f"✅ 동기화 완료: {sent_count}개 이벤트 전송")

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
        print(f"❌ 에러 발생: {str(e)}")
        raise


def get_canvas_token(user_id: int) -> str:
    """User-Service API를 호출하여 Canvas 토큰 조회 (복호화된 상태)"""
    url = f"{USER_SERVICE_URL}/credentials/{user_id}/canvas"
    headers = {
        'X-Service-Token': os.environ.get('SERVICE_AUTH_TOKEN', 'local-dev-token')
    }

    response = requests.get(url, headers=headers, timeout=5)
    response.raise_for_status()

    data = response.json()
    return data['accessToken']


def fetch_canvas_assignments(token: str, canvas_course_id: str, since: str = None) -> List[Dict[str, Any]]:
    """Canvas API에서 과제 목록 조회"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/assignments"
    headers = {'Authorization': f'Bearer {token}'}
    params = {}

    # 증분 동기화: updated_at 기준
    if since:
        params['updated_since'] = since

    response = requests.get(url, headers=headers, params=params, timeout=10)
    response.raise_for_status()

    return response.json()


def fetch_canvas_announcements(token: str, canvas_course_id: str, since: str = None) -> List[Dict[str, Any]]:
    """Canvas API에서 공지사항 조회"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/discussion_topics"
    headers = {'Authorization': f'Bearer {token}'}
    params = {'only_announcements': 'true'}

    if since:
        params['updated_since'] = since

    response = requests.get(url, headers=headers, params=params, timeout=10)
    response.raise_for_status()

    return response.json()


def fetch_canvas_submissions(token: str, canvas_course_id: str, user_id: int) -> List[Dict[str, Any]]:
    """Canvas API에서 제출물 조회"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/students/submissions"
    headers = {'Authorization': f'Bearer {token}'}
    params = {'student_ids[]': 'all', 'include[]': 'user'}

    response = requests.get(url, headers=headers, params=params, timeout=10)
    response.raise_for_status()

    # 최근 제출된 항목만 필터링 (TODO: 실제로는 DB와 비교 필요)
    all_submissions = response.json()
    return [s for s in all_submissions if s.get('workflow_state') == 'submitted']


def send_to_sqs(queue_name: str, message: Dict[str, Any]):
    """SQS 큐로 메시지 전송"""
    # 큐 URL 조회
    response = sqs.get_queue_url(QueueName=queue_name)
    queue_url = response['QueueUrl']

    # 메시지 전송
    sqs.send_message(
        QueueUrl=queue_url,
        MessageBody=json.dumps(message)
    )

    print(f"  → SQS 전송: {queue_name}")