"""
Canvas Sync Lambda Handler (Phase 1: Manual Sync)
Fetch courses and assignments from Canvas API and send to SQS

Supports both:
- terraform: Course별 개별 메시지 전송 (메시지 크기 제한 방지)
- main: 전체 배치 메시지 전송 (성능 최적화)
"""

import json
import os
import boto3
import requests
from datetime import datetime
from typing import Dict, List, Any

# Environment variables
USER_SERVICE_URL = os.environ['USER_SERVICE_URL']
COURSE_SERVICE_URL = os.environ.get('COURSE_SERVICE_URL', '')  # Optional for terraform
CANVAS_API_BASE_URL = os.environ['CANVAS_API_BASE_URL']
AWS_REGION = os.environ['AWS_REGION']
SQS_ENDPOINT = os.environ.get('SQS_ENDPOINT')  # Optional: For LocalStack
CANVAS_SYNC_API_KEY_SECRET_ARN = os.environ.get('CANVAS_SYNC_API_KEY_SECRET_ARN')
SQS_QUEUE_URL = os.environ.get('SQS_QUEUE_URL')  # Optional: prefer explicit URL when available

# AWS clients
sqs = boto3.client('sqs', region_name=AWS_REGION, endpoint_url=SQS_ENDPOINT)
secretsmanager = boto3.client('secretsmanager', region_name=AWS_REGION)

# Queue URL 캐시 (Lambda 실행 기간 동안 캐싱)
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
        print(f"Failed to get secret from Secrets Manager: {e}")
        # Fallback to environment variable
        _canvas_sync_api_key_cache = os.environ.get('CANVAS_SYNC_API_KEY', 'local-dev-token')
        return _canvas_sync_api_key_cache


def lambda_handler(event, context):
    """
    Canvas 동기화 요청 처리 (Phase 1/2/3 공통)

    입력 예시:
    - direct invoke: {"cognitoSub": "...", "syncMode": "assignments"}
    - EventBridge: {"detail": {"cognitoSub": "...", "syncMode": "courses"}}
    - SQS: {"Records": [{"body": "{\"cognitoSub\": \"...\", \"syncMode\": \"assignments\"}"}]}
    """
    print(f"DEBUG: Received event: {json.dumps(event)}")

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

        cognito_sub = extract_cognito_sub(event)
        sync_mode = extract_sync_mode(event)

        if sync_mode not in ("courses", "assignments"):
            raise ValueError(f"Invalid syncMode: {sync_mode}")

        print(f"Canvas sync started for cognitoSub={cognito_sub}, syncMode={sync_mode}")

        enabled_canvas_ids = set()
        if sync_mode == 'assignments' and COURSE_SERVICE_URL:
            # assignments 모드에서는 활성화된 수강 과목만 동기화
            enabled_courses = fetch_enabled_enrollments(cognito_sub)
            enabled_canvas_ids = {c["canvasCourseId"] for c in enabled_courses}

            if not enabled_courses:
                print("  - No enabled enrollments found, skipping Canvas fetch")
                return {
                    'statusCode': 200,
                    'body': {
                        'coursesCount': 0,
                        'assignmentsCount': 0,
                        'syncedAt': datetime.utcnow().isoformat()
                    }
                }

        # 2. User-Service에서 Canvas Token 조회 (복호화된 토큰)
        canvas_token = get_canvas_token(cognito_sub)

        # 3. Canvas API: 사용자 과목 조회
        courses = fetch_user_courses(canvas_token)
        total_assignments = 0

        print(f"  - Fetched {len(courses)} courses")

        courses_data = []

        # 4. 과목 처리
        for course in courses:
            # assignments 모드일 때만 활성 과목 필터링
            if sync_mode == 'assignments' and enabled_canvas_ids and course['id'] not in enabled_canvas_ids:
                continue

            course_data = {
                'canvasCourseId': course['id'],
                'courseName': course['name'],
                'courseCode': course.get('course_code', ''),
                'workflowState': course.get('workflow_state', 'available'),
                'startAt': course.get('start_at'),
                'endAt': course.get('end_at'),
                'assignments': []
            }

            # courses 모드면 과제 조회 스킵
            if sync_mode == 'courses':
                courses_data.append(course_data)
                continue

            assignments = fetch_canvas_assignments(canvas_token, str(course['id']))
            total_assignments += len(assignments)

            print(f"  - Course {course['id']}: {len(assignments)} assignments")

            for assignment in assignments:
                submission_types = assignment.get('submission_types', [])
                submission_types_str = ','.join(submission_types) if submission_types else ''

                due_at = assignment.get('due_at')
                due_at_formatted = due_at.replace('Z', '').split('.')[0] if due_at else None

                created_at = assignment.get('created_at')
                created_at_formatted = created_at.replace('Z', '').split('.')[0] if created_at else None

                updated_at = assignment.get('updated_at')
                updated_at_formatted = updated_at.replace('Z', '').split('.')[0] if updated_at else None

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

        # 활성화된 과목이 없을 경우 처리
        if enabled_canvas_ids and not courses_data:
            print("  - No enabled enrollments found, skipping sync message")
            return {
                'statusCode': 200,
                'body': {
                    'coursesCount': 0,
                    'assignmentsCount': 0,
                    'syncedAt': datetime.utcnow().isoformat()
                }
            }

        # 5. 메시지 송신 (배치 방식 - main 브랜치 호환)
        event_type = 'CANVAS_COURSES_SYNCED' if sync_mode == 'courses' else 'CANVAS_SYNC_COMPLETED'

        sync_message = {
            'eventType': event_type,
            'cognitoSub': cognito_sub,
            'syncedAt': datetime.utcnow().isoformat(),
            'courses': courses_data,
            'syncMode': sync_mode
        }

        send_to_sqs('lambda-to-courseservice-sync', sync_message)

        print(f"Canvas sync completed: {len(courses_data)} courses, {total_assignments} assignments (mode={sync_mode})")

        # 6. 응답
        return {
            'statusCode': 200,
            'body': {
                'coursesCount': len(courses_data),
                'assignmentsCount': total_assignments,
                'syncedAt': datetime.utcnow().isoformat()
            }
        }

    except Exception as e:
        print(f"Error in lambda_handler: {str(e)}")
        raise


def extract_cognito_sub(event: Dict[str, Any]) -> str:
    """
    호출별 페이로드 형식 처리
    - EventBridge: {"detail": {"cognitoSub": "..."}}
    - SQS: {"Records": [{"body": "{\"cognitoSub\": \"...\"}"}]}
    - direct invoke: {"cognitoSub": "..."}
    """
    if 'detail' in event:
        return event['detail']['cognitoSub']

    if 'Records' in event and len(event['Records']) > 0:
        body = json.loads(event['Records'][0]['body'])
        return body['cognitoSub']

    return event['cognitoSub']


def extract_sync_mode(event: Dict[str, Any]) -> str:
    """
    syncMode 추출 (기본값: assignments)
    """
    default_mode = 'assignments'

    if 'detail' in event and 'syncMode' in event['detail']:
        return event['detail']['syncMode']

    if 'Records' in event and len(event['Records']) > 0:
        try:
            body = json.loads(event['Records'][0]['body'])
            return body.get('syncMode', default_mode)
        except Exception:
            return default_mode

    return event.get('syncMode', default_mode)


def get_canvas_token(cognito_sub: str) -> str:
    """User-Service 내부 API로 Canvas 토큰 조회 (복호화된 토큰 반환)"""
    url = f"{USER_SERVICE_URL}/internal/v1/credentials/canvas/by-cognito-sub/{cognito_sub}"
    headers = {
        'X-Api-Key': get_canvas_sync_api_key()
    }

    response = requests.get(url, headers=headers, timeout=5)
    response.raise_for_status()

    data = response.json()
    return data['canvasToken']


def fetch_enabled_enrollments(cognito_sub: str) -> List[Dict[str, Any]]:
    """Course-Service 내부 API에서 sync 활성화된 수강 목록 조회"""
    if not COURSE_SERVICE_URL:
        return []

    url = f"{COURSE_SERVICE_URL}/internal/v1/enrollments/enabled"
    headers = {
        'X-Cognito-Sub': cognito_sub
    }

    response = requests.get(url, headers=headers, timeout=5)
    response.raise_for_status()
    return response.json()


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
            url_part = link.split(';')[0].strip()
            return url_part.strip('<>')

    return None


def fetch_user_courses(token: str) -> List[Dict[str, Any]]:
    """사용자의 수강 중인 Course 목록 가져오기 (페이지네이션 처리)"""
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

        link_header = response.headers.get('Link', '')
        url = extract_next_url(link_header)
        params = None  # 다음 페이지 URL에는 파라미터 불필요
    return all_courses


def fetch_canvas_assignments(token: str, canvas_course_id: str) -> List[Dict[str, Any]]:
    """특정 Course의 Assignment 목록 가져오기 (페이지네이션 처리)"""
    url = f"{CANVAS_API_BASE_URL}/courses/{canvas_course_id}/assignments"
    headers = {'Authorization': f'Bearer {token}'}
    params = {'per_page': 100}

    all_assignments = []
    while url:
        response = requests.get(url, headers=headers, params=params, timeout=10)
        response.raise_for_status()

        assignments = response.json()
        all_assignments.extend(assignments)

        link_header = response.headers.get('Link', '')
        url = extract_next_url(link_header)
        params = None
    return all_assignments


def get_queue_url_cached(queue_name: str) -> str:
    """
    Queue URL 조회 (캐싱)

    Lambda 실행 간에는 한 번만 조회하여 성능 향상
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
    """SQS 에 메시지 발행 (개별)"""
    queue_url = get_queue_url_cached(queue_name)

    print(f"DEBUG: sending to queue_url={queue_url}")

    try:
        sqs.send_message(
            QueueUrl=queue_url,
            MessageBody=json.dumps(message)
        )
        print(f"  -> SQS sent: {queue_name}")
    except Exception as e:
        print(f"ERROR: SQS send failed: {e}")
        raise


def send_batch_to_sqs(queue_name: str, messages: List[Dict[str, Any]]):
    """
    SQS 에 메시지 배치 발행 (최대 10개씩)

    성능 향상: 100개 메시지 = 10번 API 호출
    """
    if not messages:
        return

    queue_url = get_queue_url_cached(queue_name)

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
