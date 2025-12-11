"""
System Tests 공통 Fixtures + 실행 순서 정렬

이 conftest.py는 system-tests/ 전체의 공통 설정을 담당합니다:
1. 테스트 실행 순서 강제 (infra → component → integration → scenarios)
2. 공통 fixtures (AWS 클라이언트, DB 연결, 서비스 URL 등)
3. 테스트 데이터 관리
"""

import pytest
import boto3
import time
import mysql.connector
import os
import requests
from pathlib import Path
from dotenv import load_dotenv
from typing import Generator

# 환경변수 로드
# pytest는 호스트에서 실행
project_root = Path(__file__).parent.parent
load_dotenv(project_root / '.env.local')  # 비밀 + localhost
load_dotenv(project_root / '.env.common')  # compose 환경으로 덮어씌움
load_dotenv(project_root / '.env.acceptance', override=True)  # 향후 추가 대비

# 호스트에서 docker-compose 포트 포워딩을 이용해 DB에 연결하기 위한 고정 값
TEST_DB_HOST = "127.0.0.1"
TEST_DB_PORT = 3307  # docker-compose.acceptance.yml에서 3307:3306 포워딩
TEST_DB_USER = os.environ.get("MYSQL_USER", "unisync")
TEST_DB_PASSWORD = os.environ.get("MYSQL_PASSWORD", "unisync_password")
TEST_DB_NAMES = {
    "user": os.environ.get("USER_SERVICE_DB_NAME", "user_db"),
    "course": os.environ.get("COURSE_SERVICE_DB_NAME", "course_db"),
    "schedule": os.environ.get("SCHEDULE_SERVICE_DB_NAME", "schedule_db"),
}


# =============================================================================
# 테스트 실행 순서 강제
# =============================================================================

def pytest_collection_modifyitems(items):
    """
    전체 실행 시 순서 강제: infra → component → integration → scenarios

    개별 폴더 실행 시에는 해당 폴더 내 테스트만 실행됩니다.
    전체 실행 (pytest system-tests/) 시에만 순서가 강제됩니다.
    """
    order = {
        "infra": 0,
        "component": 1,
        "integration": 2,
        "scenarios": 3
    }

    def get_sort_key(item):
        path = str(item.fspath)
        for folder, priority in order.items():
            if f"/{folder}/" in path or f"\\{folder}\\" in path:
                return priority
        return 100  # 기타 파일은 마지막

    items.sort(key=get_sort_key)


# =============================================================================
# 환경 설정 Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def localstack_endpoint() -> str:
    """LocalStack 엔드포인트 (환경변수에서 로드)"""
    return os.environ['AWS_SQS_ENDPOINT']


@pytest.fixture(scope="session")
def service_urls() -> dict:
    """모든 서비스 URL"""
    return {
        "gateway": os.environ.get('API_GATEWAY_URL', 'http://localhost:8080'),
        "user": os.environ['USER_SERVICE_URL'],
        "course": os.environ['COURSE_SERVICE_URL'],
        "schedule": os.environ['SCHEDULE_SERVICE_URL'],
    }


# =============================================================================
# AWS 클라이언트 Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def sqs_client(localstack_endpoint: str):
    """SQS 클라이언트 fixture"""
    client = boto3.client(
        'sqs',
        endpoint_url=localstack_endpoint,
        region_name=os.environ['AWS_REGION'],
        aws_access_key_id=os.environ['AWS_ACCESS_KEY_ID'],
        aws_secret_access_key=os.environ['AWS_SECRET_ACCESS_KEY']
    )
    return client


@pytest.fixture(scope="session")
def lambda_client(localstack_endpoint: str):
    """Lambda 클라이언트 fixture"""
    client = boto3.client(
        'lambda',
        endpoint_url=os.environ['AWS_LAMBDA_ENDPOINT_URL'],
        region_name=os.environ['AWS_REGION'],
        aws_access_key_id=os.environ['AWS_ACCESS_KEY_ID'],
        aws_secret_access_key=os.environ['AWS_SECRET_ACCESS_KEY']
    )
    return client


# =============================================================================
# SQS 큐 URL Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def canvas_sync_queue_url(sqs_client) -> str:
    """lambda-to-courseservice-sync URL 조회/생성"""
    queue_name = os.environ['SQS_CANVAS_SYNC_QUEUE']
    try:
        response = sqs_client.get_queue_url(QueueName=queue_name)
        return response['QueueUrl']
    except sqs_client.exceptions.QueueDoesNotExist:
        response = sqs_client.create_queue(
            QueueName=queue_name,
            Attributes={'VisibilityTimeout': '5'}
        )
        return response['QueueUrl']


@pytest.fixture(scope="session")
def assignment_to_schedule_queue_url(sqs_client) -> str:
    """courseservice-to-scheduleservice-assignments URL 조회/생성"""
    queue_name = os.environ['SQS_ASSIGNMENT_TO_SCHEDULE_QUEUE']
    try:
        response = sqs_client.get_queue_url(QueueName=queue_name)
        return response['QueueUrl']
    except sqs_client.exceptions.QueueDoesNotExist:
        response = sqs_client.create_queue(
            QueueName=queue_name,
            Attributes={'VisibilityTimeout': '5'}
        )
        return response['QueueUrl']


# =============================================================================
# 서비스 URL Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def schedule_service_url() -> str:
    """Schedule-Service API URL"""
    return os.environ['SCHEDULE_SERVICE_URL']


@pytest.fixture(scope="session")
def course_service_url() -> str:
    """Course-Service API URL"""
    return os.environ['COURSE_SERVICE_URL']


@pytest.fixture(scope="session")
def user_service_url() -> str:
    """User-Service API URL"""
    return os.environ['USER_SERVICE_URL']


# =============================================================================
# 데이터베이스 Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def mysql_connection():
    """MySQL 연결 fixture (Course-Service DB)"""
    max_retries = 30
    host = TEST_DB_HOST
    port = TEST_DB_PORT
    user = TEST_DB_USER
    password = TEST_DB_PASSWORD
    database = TEST_DB_NAMES["course"]

    for i in range(max_retries):
        try:
            conn = mysql.connector.connect(
                host=host,
                port=port,
                user=user,
                password=password,
                database=database
            )
            yield conn
            conn.close()
            return
        except mysql.connector.Error as e:
            if i == max_retries - 1:
                raise
            time.sleep(1)


@pytest.fixture(scope="session")
def schedule_db_connection():
    """MySQL 연결 fixture (Schedule-Service DB)"""
    max_retries = 30
    host = TEST_DB_HOST
    port = TEST_DB_PORT
    user = TEST_DB_USER
    password = TEST_DB_PASSWORD
    database = TEST_DB_NAMES["schedule"]

    for i in range(max_retries):
        try:
            conn = mysql.connector.connect(
                host=host,
                port=port,
                user=user,
                password=password,
                database=database
            )
            yield conn
            conn.close()
            return
        except mysql.connector.Error as e:
            if i == max_retries - 1:
                raise
            time.sleep(1)


@pytest.fixture(scope="session")
def user_db_connection():
    """MySQL 연결 fixture (User-Service DB)"""
    max_retries = 30
    host = TEST_DB_HOST
    port = TEST_DB_PORT
    user = TEST_DB_USER
    password = TEST_DB_PASSWORD
    database = TEST_DB_NAMES["user"]

    for i in range(max_retries):
        try:
            conn = mysql.connector.connect(
                host=host,
                port=port,
                user=user,
                password=password,
                database=database
            )
            yield conn
            conn.close()
            return
        except mysql.connector.Error as e:
            if i == max_retries - 1:
                raise
            time.sleep(1)


# =============================================================================
# 데이터 정리 Fixtures
# =============================================================================

@pytest.fixture(scope="function")
def clean_database(mysql_connection):
    """각 테스트 전후 Course-Service DB 정리"""
    cursor = mysql_connection.cursor()

    # 테스트 전: 기존 데이터 삭제
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE assignments")
    cursor.execute("TRUNCATE TABLE enrollments")
    cursor.execute("TRUNCATE TABLE courses")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    mysql_connection.commit()

    yield

    # 테스트 후: 생성된 데이터 삭제
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE assignments")
    cursor.execute("TRUNCATE TABLE enrollments")
    cursor.execute("TRUNCATE TABLE courses")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    mysql_connection.commit()
    cursor.close()


@pytest.fixture(scope="function")
def clean_schedule_database(schedule_db_connection):
    """각 테스트 전후 Schedule-Service DB 정리"""
    cursor = schedule_db_connection.cursor()

    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE schedules")
    cursor.execute("TRUNCATE TABLE categories")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    schedule_db_connection.commit()

    yield

    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE schedules")
    cursor.execute("TRUNCATE TABLE categories")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    schedule_db_connection.commit()
    cursor.close()


@pytest.fixture(scope="function")
def clean_user_database(user_db_connection):
    """각 테스트 전후 User-Service DB 정리 (friend/group 데이터만)"""
    cursor = user_db_connection.cursor()

    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE group_members")
    cursor.execute("TRUNCATE TABLE `groups`")
    cursor.execute("TRUNCATE TABLE friendships")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    user_db_connection.commit()

    yield

    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE group_members")
    cursor.execute("TRUNCATE TABLE `groups`")
    cursor.execute("TRUNCATE TABLE friendships")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    user_db_connection.commit()
    cursor.close()


@pytest.fixture(scope="function")
def clean_sqs_queue(sqs_client, canvas_sync_queue_url):
    """각 테스트 전후 SQS 큐 비우기"""
    sqs_client.purge_queue(QueueUrl=canvas_sync_queue_url)
    time.sleep(1)

    yield

    sqs_client.purge_queue(QueueUrl=canvas_sync_queue_url)


# =============================================================================
# 테스트 데이터 Fixtures
# =============================================================================

@pytest.fixture(scope="function")
def test_course(mysql_connection, clean_database):
    """테스트용 Course 생성"""
    cursor = mysql_connection.cursor()

    cursor.execute("""
        INSERT INTO courses (canvas_course_id, name, course_code, description, start_at, end_at, created_at, updated_at)
        VALUES (789, 'Spring Boot 고급', 'CS301', '테스트 과목', '2025-09-01 00:00:00', '2025-12-31 23:59:59', NOW(), NOW())
    """)
    mysql_connection.commit()

    course_id = cursor.lastrowid
    cursor.close()

    return {
        'id': course_id,
        'canvas_course_id': 789,
        'name': 'Spring Boot 고급',
        'course_code': 'CS301'
    }


# =============================================================================
# E2E/Scenario Test Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def canvas_token():
    """실제 Canvas API 토큰 (환경변수에서 로드)

    Note: 토큰이 없으면 None 반환. 테스트에서 명시적으로 검증해야 함.
    """
    return os.environ.get("CANVAS_API_TOKEN")


@pytest.fixture(scope="function")
def test_user_credentials():
    """E2E 테스트용 사용자 자격 증명 - 각 테스트마다 고유"""
    import uuid
    unique_id = uuid.uuid4().hex[:8]
    return {
        "email": f"e2e-test-{unique_id}@unisync.com",
        "password": "TestPassword123!",
        "name": "E2E Test User"
    }


@pytest.fixture(scope="function")
def jwt_auth_tokens(service_urls):
    """
    회원가입 및 로그인을 통해 JWT 토큰 획득 (멀티 유저 지원)
    각 테스트마다 새로운 사용자들을 생성하여 독립성 보장

    Returns:
        dict with keys: user1, user1_sub, user2, user2_sub
    """
    import uuid
    gateway_url = service_urls.get("gateway", "http://localhost:8080")

    def create_user(user_name: str) -> dict:
        unique_id = uuid.uuid4().hex[:8]
        credentials = {
            "email": f"e2e-{user_name}-{unique_id}@unisync.com",
            "password": "TestPassword123!",
            "name": f"E2E {user_name.title()} User"
        }

        signup_response = requests.post(
            f"{gateway_url}/api/v1/auth/signup",
            json=credentials,
            timeout=10
        )

        if signup_response.status_code != 201:
            pytest.fail(f"회원가입 실패 ({user_name}): {signup_response.status_code} - {signup_response.text}")

        signup_data = signup_response.json()
        print(f"[Setup] {user_name} 회원가입 완료: cognitoSub={signup_data.get('cognitoSub')}")

        return {
            "id_token": signup_data.get("idToken"),
            "access_token": signup_data.get("accessToken"),
            "refresh_token": signup_data.get("refreshToken"),
            "cognito_sub": signup_data.get("cognitoSub"),
            "email": signup_data.get("email")
        }

    print(f"\n[Setup] JWT 인증 - 멀티 유저 회원가입 중...")

    user1_data = create_user("user1")
    user2_data = create_user("user2")

    return {
        "user1": user1_data["id_token"],
        "user1_sub": user1_data["cognito_sub"],
        "user2": user2_data["id_token"],
        "user2_sub": user2_data["cognito_sub"],
        # 하위 호환성 유지
        "id_token": user1_data["id_token"],
        "access_token": user1_data["access_token"],
        "refresh_token": user1_data["refresh_token"],
        "cognito_sub": user1_data["cognito_sub"],
        "email": user1_data["email"]
    }


def wait_for_sync(timeout_seconds=60, poll_interval=2):
    """동기화가 완료될 때까지 대기하는 헬퍼 함수"""
    def _wait(check_fn, error_msg="Sync timeout"):
        start_time = time.time()
        while time.time() - start_time < timeout_seconds:
            if check_fn():
                return True
            time.sleep(poll_interval)
        raise TimeoutError(error_msg)
    return _wait
