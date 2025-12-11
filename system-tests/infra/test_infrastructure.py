"""
Infrastructure Tests (1단계)

docker-compose.acceptance.yml 환경의 인프라가 정상적으로 올라왔는지 검증합니다.
다른 모든 테스트 전에 실행되어야 합니다.

검증 대상:
- LocalStack (SQS, Lambda)
- MySQL
- 각 서비스 Health Check (User, Course, Schedule)
- SQS 큐 존재 여부
- Lambda 함수 배포 상태
"""

import pytest
import requests
import time
import os


class TestLocalStackHealth:
    """LocalStack 인프라 검증"""

    def test_localstack_is_healthy(self, localstack_endpoint):
        """LocalStack이 정상 동작하는지 확인"""
        response = requests.get(f"{localstack_endpoint}/_localstack/health", timeout=5)
        assert response.status_code == 200

        health = response.json()
        assert 'services' in health
        print(f"[OK] LocalStack healthy - Services: {list(health['services'].keys())}")


class TestSqsQueues:
    """SQS 큐 검증"""

    def test_canvas_sync_queue_exists(self, sqs_client):
        """lambda-to-courseservice-sync 큐가 존재하는지 확인"""
        queue_name = os.environ['SQS_CANVAS_SYNC_QUEUE']
        queues = sqs_client.list_queues()
        queue_urls = queues.get('QueueUrls', [])

        assert any(queue_name in url for url in queue_urls), \
            f"Queue '{queue_name}' not found. Available: {queue_urls}"
        print(f"[OK] SQS queue '{queue_name}' exists")

    def test_assignment_to_schedule_queue_exists(self, sqs_client):
        """courseservice-to-scheduleservice-assignments 큐가 존재하는지 확인"""
        queue_name = os.environ['SQS_ASSIGNMENT_TO_SCHEDULE_QUEUE']
        queues = sqs_client.list_queues()
        queue_urls = queues.get('QueueUrls', [])

        assert any(queue_name in url for url in queue_urls), \
            f"Queue '{queue_name}' not found. Available: {queue_urls}"
        print(f"[OK] SQS queue '{queue_name}' exists")

    def test_course_to_schedule_queue_exists(self, sqs_client):
        """courseservice-to-scheduleservice-courses 큐가 존재하는지 확인"""
        queue_name = os.environ['SQS_COURSE_TO_SCHEDULE_QUEUE']
        queues = sqs_client.list_queues()
        queue_urls = queues.get('QueueUrls', [])

        assert any(queue_name in url for url in queue_urls), \
            f"Queue '{queue_name}' not found. Available: {queue_urls}"
        print(f"[OK] SQS queue '{queue_name}' exists")


class TestLambdaFunctions:
    """Lambda 함수 검증"""

    def test_canvas_sync_lambda_deployed(self, lambda_client):
        """canvas-sync-lambda가 배포되었는지 확인"""
        response = lambda_client.list_functions()
        functions = [f['FunctionName'] for f in response.get('Functions', [])]

        assert 'canvas-sync-lambda' in functions, \
            f"Lambda 'canvas-sync-lambda' not deployed. Available: {functions}"
        print(f"[OK] Lambda 'canvas-sync-lambda' is deployed")


class TestMySqlConnection:
    """MySQL 데이터베이스 검증"""

    def test_mysql_course_db_connection(self, mysql_connection):
        """Course-Service DB 연결 가능한지 확인"""
        cursor = mysql_connection.cursor()
        cursor.execute("SELECT 1")
        result = cursor.fetchone()
        cursor.close()

        assert result[0] == 1
        print(f"[OK] MySQL (course-service DB) is ready")

    def test_mysql_schedule_db_connection(self, schedule_db_connection):
        """Schedule-Service DB 연결 가능한지 확인"""
        cursor = schedule_db_connection.cursor()
        cursor.execute("SELECT 1")
        result = cursor.fetchone()
        cursor.close()

        assert result[0] == 1
        print(f"[OK] MySQL (schedule-service DB) is ready")


class TestServiceHealthChecks:
    """Spring 서비스 Health Check"""

    def test_user_service_health(self, user_service_url):
        """User-Service가 정상인지 확인"""
        max_retries = 60
        for i in range(max_retries):
            try:
                response = requests.get(f"{user_service_url}/actuator/health", timeout=2)
                if response.status_code == 200:
                    print(f"[OK] user-service is healthy")
                    return
            except requests.exceptions.RequestException:
                pass
            time.sleep(1)

        pytest.fail("user-service failed to start within 60 seconds")

    def test_course_service_health(self, course_service_url):
        """Course-Service가 정상인지 확인"""
        max_retries = 60
        for i in range(max_retries):
            try:
                response = requests.get(f"{course_service_url}/actuator/health", timeout=2)
                if response.status_code == 200:
                    print(f"[OK] course-service is healthy")
                    return
            except requests.exceptions.RequestException:
                pass
            time.sleep(1)

        pytest.fail("course-service failed to start within 60 seconds")

    def test_schedule_service_health(self, schedule_service_url):
        """Schedule-Service가 정상인지 확인"""
        max_retries = 60
        for i in range(max_retries):
            try:
                response = requests.get(f"{schedule_service_url}/actuator/health", timeout=2)
                if response.status_code == 200:
                    print(f"[OK] schedule-service is healthy")
                    return
            except requests.exceptions.RequestException:
                pass
            time.sleep(1)

        pytest.fail("schedule-service failed to start within 60 seconds")
