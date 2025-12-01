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
USER_SERVICE_URL = os.environ['USER_SERVICE_URL']
CANVAS_API_BASE_URL = os.environ['CANVAS_API_BASE_URL']
AWS_REGION = os.environ['AWS_REGION']
SQS_ENDPOINT = os.environ.get('SQS_ENDPOINT')  # Optional: For LocalStack
CANVAS_SYNC_API_KEY_SECRET_ARN = os.environ.get('CANVAS_SYNC_API_KEY_SECRET_ARN')
SQS_QUEUE_URL = os.environ.get('SQS_QUEUE_URL')  # Optional: prefer explicit URL when available

# AWS clients
sqs = boto3.client('sqs', region_name=AWS_REGION, endpoint_url=SQS_ENDPOINT)
secretsmanager = boto3.client('secretsmanager', region_name=AWS_REGION)

# Queue URL 캐시 (Lambda 실행 기간 동안 재사용)
_queue_url_cache = {}

# Canvas Sync API Key 캐시 (Lambda 실행 기간 동안 재사용)
_canvas_sync_api_key_cache = None


def get_canvas_sync_api_key() -> str:
    """Secrets Manager에서 Canvas Sync API Key 조회 (캐시 사용)"""
    global _canvas_sync_api_key_cache
    
    if _canvas_sync_api_key_cache is not None:
        return _canvas_sync_api_key_cache
    
    # LocalStack 또는 Secrets Manager ARN이 없는 경우 환경변수에서 직접 읽기
    if not CANVAS_SYNC_API_KEY_SECRET_ARN or SQS_ENDPOINT:
        _canvas_sync_api_key_cache = os.environ.get('CANVAS_SYNC_API_KEY', 'local-dev-token')
        return _canvas_sync_api_key_cache
    
    # Secrets Manager에서 조회
    try:
        response = secretsmanager.get_secret_value(SecretId=CANVAS_SYNC_API_KEY_SECRET_ARN)
        _canvas_sync_api_key_cache = response['SecretString']
        return _canvas_sync_api_key_cache
    except Exception as e:
        print(f"⚠️ Failed to get secret from Secrets Manager: {e}")
        # Fallback to environment variable
        _canvas_sync_api_key_cache = os.environ.get('CANVAS_SYNC_API_KEY', 'local-dev-token')
        return _canvas_sync_api_key_cache


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
        # 0. 테스트 모드: Secrets + SQS만 검증
        if event.get("testMode") == "SQS_ONLY":
            print(event)
            sync_message = {
                "eventType": "CANVAS_SYNC_TEST",
                "cognitoSub": event.get("cognitoSub", "test-sub"),
                "syncedAt": datetime.utcnow().isoformat(),
                "courses": []
            }
            send_to_sqs("lambda-to-courseservice-sync", sync_message)
            return {
                "statusCode": 200,
                "body": {"test": "SQS_ONLY_OK"}
            }

        # 1. 입력 정규화 (호출자별 형식 차이 흡수)
        cognito_sub = extract_cognito_sub(event)

        print(f"🚀 Canvas sync started for cognitoSub={cognito_sub}")

        # 2. User-Service에서 Canvas Token 조회 (복호화됨)
        canvas_token = get_canvas_token(cognito_sub)

        # 3. Canvas API: 사용자의 전체 Course 조회
        courses = fetch_user_courses(canvas_token)
        total_assignments = 0

        print(f"  - Fetched {len(courses)} courses")

        # 4. 단일 동기화 메시지 구성
        courses_data = []

        # 5. 각 Course 처리
        for course in courses:
            # 5-1. Course 정보 수집
            course_data = {
                'canvasCourseId': course['id'],
                'courseName': course['name'],
                'courseCode': course.get('course_code', ''),
                'workflowState': course.get('workflow_state', 'available'),
                'startAt': course.get('start_at'),
                'endAt': course.get('end_at'),
                'assignments': []
            }

            # 5-2. 해당 Course의 Assignments 조회
            assignments = fetch_canvas_assignments(canvas_token, str(course['id']))
            total_assignments += len(assignments)

            print(f"  - Course {course['id']}: {len(assignments)} assignments")

            # 5-3. Assignment 데이터 수집
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

                course_data['assignments'].append({
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

            courses_data.append(course_data)

        # 6. 단일 메시지 전송
        sync_message = {
            'eventType': 'CANVAS_SYNC_COMPLETED',
            'cognitoSub': cognito_sub,
            'syncedAt': datetime.utcnow().isoformat(),
            'courses': courses_data
        }

        send_to_sqs('lambda-to-courseservice-sync', sync_message)

        print(f"✅ Canvas sync completed: {len(courses)} courses, {total_assignments} assignments")

        # 7. 동기 응답 (Spring은 즉시 사용, EventBridge는 무시)
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
        'X-Api-Key': get_canvas_sync_api_key()
    }

    response = requests.get(url, headers=headers, timeout=5)
    response.raise_for_status()

    data = response.json()
    return data['canvasToken']


def extract_next_url(link_header: str) -> str:
    """
    Link 헤더에서 rel="next" URL 추출

    Link: <URL>; rel="current", <URL>; rel="next"
    """
    if not link_header:
        return None

    links = link_header.split(',')
    for link in links:
        if 'rel="next"' in link:
            # <URL>; rel="next" → URL 추출
            url_part = link.split(';')[0].strip()
            return url_part.strip('<>')

    return None


def fetch_user_courses(token: str) -> List[Dict[str, Any]]:
    """사용자가 수강 중인 Course 목록 가져오기 (페이지네이션 순회)"""
    url = f"{CANVAS_API_BASE_URL}/courses"
    headers = {'Authorization': f'Bearer {token}'}
    params = {
        'enrollment_type': 'student',
        'enrollment_state': 'active',
        'include[]': ['term', 'course_progress'],
        'per_page': 100
    }

    all_courses = []
    while url:
        response = requests.get(url, headers=headers, params=params, timeout=10)
        response.raise_for_status()

        courses = response.json()
        all_courses.extend(courses)

        # Link 헤더에서 다음 페이지 확인
        link_header = response.headers.get('Link', '')
        url = extract_next_url(link_header)
        params = None  # 다음 페이지 URL에 이미 파라미터 포함됨

    return all_courses


def fetch_canvas_assignments(token: str, canvas_course_id: str) -> List[Dict[str, Any]]:
    """특정 Course의 Assignment 목록 가져오기 (페이지네이션 순회)"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/assignments"
    headers = {'Authorization': f'Bearer {token}'}
    params = {'per_page': 100}

    all_assignments = []
    while url:
        response = requests.get(url, headers=headers, params=params, timeout=10)
        response.raise_for_status()

        assignments = response.json()
        all_assignments.extend(assignments)

        # Link 헤더에서 다음 페이지 확인
        link_header = response.headers.get('Link', '')
        url = extract_next_url(link_header)
        params = None  # 다음 페이지 URL에 이미 파라미터 포함됨

    return all_assignments


def get_queue_url_cached(queue_name: str) -> str:
    """
    Queue URL 조회 (캐싱)

    Lambda 실행 중 동일한 큐는 한 번만 조회하여 성능 향상
    """
    # 1) 환경변수에 URL이 있으면 그걸 바로 사용
    if SQS_QUEUE_URL:
        return SQS_QUEUE_URL

    # 2) 아니면 기존 방식대로 이름으로 조회
    if queue_name not in _queue_url_cache:
        response = sqs.get_queue_url(QueueName=queue_name)
        _queue_url_cache[queue_name] = response['QueueUrl']
        print(f"  -> Queue URL cached: {queue_name}")

    return _queue_url_cache[queue_name]


def send_to_sqs(queue_name: str, message: Dict[str, Any]):
    """SQS 큐에 메시지 발행 (개별)"""
    queue_url = get_queue_url_cached(queue_name)

    sqs.send_message(
        QueueUrl=queue_url,
        MessageBody=json.dumps(message)
    )

    print(f"  -> SQS sent: {queue_name}")


def send_batch_to_sqs(queue_name: str, messages: List[Dict[str, Any]]):
    """
    SQS 큐에 메시지 배치 발행 (최대 10개씩)

    성능 향상: 100개 메시지 = 10번 API 호출
    """
    if not messages:
        return

    queue_url = get_queue_url_cached(queue_name)

    # 10개씩 나눠서 전송 (SQS 배치 제한)
    for i in range(0, len(messages), 10):
        batch = messages[i:i + 10]

        entries = [
            {
                'Id': str(idx),
                'MessageBody': json.dumps(msg)
            }
            for idx, msg in enumerate(batch)
        ]

        sqs.send_message_batch(
            QueueUrl=queue_url,
            Entries=entries
        )

    print(f"  -> SQS batch sent: {queue_name} ({len(messages)} messages)")
