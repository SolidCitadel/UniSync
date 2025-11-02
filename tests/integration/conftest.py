"""
Integration Test Fixtures
E2E 테스트를 위한 pytest fixtures
"""

import pytest
import boto3
import time
import mysql.connector
from typing import Generator


@pytest.fixture(scope="session")
def localstack_endpoint() -> str:
    """LocalStack 엔드포인트"""
    return "http://localhost:4566"


@pytest.fixture(scope="session")
def sqs_client(localstack_endpoint: str):
    """SQS 클라이언트 fixture"""
    client = boto3.client(
        'sqs',
        endpoint_url=localstack_endpoint,
        region_name='us-east-1',
        aws_access_key_id='test',
        aws_secret_access_key='test'
    )
    return client


@pytest.fixture(scope="session")
def lambda_client(localstack_endpoint: str):
    """Lambda 클라이언트 fixture"""
    client = boto3.client(
        'lambda',
        endpoint_url=localstack_endpoint,
        region_name='us-east-1',
        aws_access_key_id='test',
        aws_secret_access_key='test'
    )
    return client


@pytest.fixture(scope="session")
def assignment_queue_url(sqs_client) -> str:
    """assignment-events-queue URL 조회/생성"""
    try:
        # 큐가 이미 있는지 확인
        response = sqs_client.get_queue_url(QueueName='assignment-events-queue')
        return response['QueueUrl']
    except sqs_client.exceptions.QueueDoesNotExist:
        # 큐가 없으면 생성 (테스트용 짧은 visibility timeout)
        response = sqs_client.create_queue(
            QueueName='assignment-events-queue',
            Attributes={
                'VisibilityTimeout': '5'  # 5초로 설정 (빠른 재시도)
            }
        )
        return response['QueueUrl']


@pytest.fixture(scope="session")
def mysql_connection():
    """MySQL 연결 fixture"""
    # 서비스가 준비될 때까지 대기
    max_retries = 30
    for i in range(max_retries):
        try:
            conn = mysql.connector.connect(
                host='localhost',
                port=3307,
                user='unisync',
                password='unisync_password',
                database='course_db'
            )
            yield conn
            conn.close()
            return
        except mysql.connector.Error as e:
            if i == max_retries - 1:
                raise
            time.sleep(1)


@pytest.fixture(scope="function")
def clean_database(mysql_connection):
    """각 테스트 전후 DB 정리"""
    cursor = mysql_connection.cursor()

    # 테스트 전: 기존 데이터 삭제
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE assignments")
    cursor.execute("TRUNCATE TABLE courses")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    mysql_connection.commit()

    yield

    # 테스트 후: 생성된 데이터 삭제
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE assignments")
    cursor.execute("TRUNCATE TABLE courses")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    mysql_connection.commit()
    cursor.close()


@pytest.fixture(scope="function")
def clean_sqs_queue(sqs_client, assignment_queue_url):
    """각 테스트 전후 SQS 큐 비우기"""
    # 테스트 전: 기존 메시지 제거
    sqs_client.purge_queue(QueueUrl=assignment_queue_url)
    time.sleep(1)  # purge 완료 대기

    yield

    # 테스트 후: 남은 메시지 제거
    sqs_client.purge_queue(QueueUrl=assignment_queue_url)


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


@pytest.fixture(scope="session")
def wait_for_services(lambda_client, sqs_client, assignment_queue_url):
    """모든 서비스가 준비될 때까지 대기 및 검증"""
    import requests

    print("\n[INFO] Infrastructure Validation Starting...")

    # 1. LocalStack Health Check
    print("[1/5] Checking LocalStack...")
    max_retries = 60
    for i in range(max_retries):
        try:
            response = requests.get("http://localhost:4566/_localstack/health", timeout=2)
            if response.status_code == 200:
                print("   [OK] LocalStack is healthy")
                break
        except requests.exceptions.RequestException:
            pass
        if i == max_retries - 1:
            raise TimeoutError("[ERROR] LocalStack failed to start")
        time.sleep(1)

    # 2. SQS Queue Validation
    print("[2/5] Checking SQS queues...")
    try:
        queues = sqs_client.list_queues()
        queue_urls = queues.get('QueueUrls', [])
        required_queue = 'assignment-events-queue'

        if any(required_queue in url for url in queue_urls):
            print(f"   [OK] SQS queue '{required_queue}' exists")
        else:
            raise RuntimeError(f"[ERROR] Required SQS queue '{required_queue}' not found")
    except Exception as e:
        raise RuntimeError(f"[ERROR] SQS validation failed: {e}")

    # 3. Lambda Function Validation
    print("[3/5] Checking Lambda functions...")
    try:
        response = lambda_client.list_functions()
        functions = [f['FunctionName'] for f in response.get('Functions', [])]

        required_lambda = 'canvas-sync-lambda'
        if required_lambda in functions:
            print(f"   [OK] Lambda '{required_lambda}' is deployed")
        else:
            print(f"   [WARN] Lambda '{required_lambda}' not found")
            print(f"   Available functions: {functions}")
            raise RuntimeError(f"[ERROR] Required Lambda '{required_lambda}' not deployed")
    except Exception as e:
        raise RuntimeError(f"[ERROR] Lambda validation failed: {e}")

    # 4. MySQL Validation
    print("[4/5] Checking MySQL...")
    max_retries = 30
    for i in range(max_retries):
        try:
            import mysql.connector
            conn = mysql.connector.connect(
                host='localhost',
                port=3307,
                user='unisync',
                password='unisync_password',
                database='course_db'
            )
            conn.close()
            print("   [OK] MySQL is ready")
            break
        except mysql.connector.Error:
            if i == max_retries - 1:
                raise TimeoutError("[ERROR] MySQL failed to start")
            time.sleep(1)

    # 5. Course Service Validation
    print("[5/5] Checking course-service...")
    max_retries = 60
    for i in range(max_retries):
        try:
            response = requests.get("http://localhost:8082/actuator/health", timeout=2)
            if response.status_code == 200:
                print("   [OK] course-service is healthy")
                break
        except requests.exceptions.RequestException:
            pass
        if i == max_retries - 1:
            raise TimeoutError("[ERROR] course-service failed to start")
        time.sleep(1)

    # 추가 대기 (SQS 리스너 시작 대기)
    time.sleep(5)

    print("[SUCCESS] All infrastructure validated successfully!\n")