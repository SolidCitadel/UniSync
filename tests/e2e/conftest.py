import pytest
import requests
import os
import time
from datetime import datetime
from dotenv import load_dotenv

# .env 파일 로드
load_dotenv()


@pytest.fixture(scope="session")
def canvas_token():
    """실제 Canvas API 토큰 (환경변수에서 로드)"""
    token = os.getenv("CANVAS_API_TOKEN")
    if not token:
        pytest.skip("CANVAS_API_TOKEN not set in environment")
    return token


@pytest.fixture(scope="session")
def test_user_id():
    """테스트용 사용자 ID"""
    return 999  # E2E 테스트 전용 userId


@pytest.fixture(scope="session")
def service_urls():
    """서비스 URL 정보"""
    return {
        "gateway": os.getenv("GATEWAY_URL", "http://localhost:8080"),
        "user_service": os.getenv("USER_SERVICE_URL", "http://localhost:8081"),
        "course_service": os.getenv("COURSE_SERVICE_URL", "http://localhost:8082"),
    }


@pytest.fixture(scope="function")
def wait_for_services(service_urls):
    """서비스가 준비될 때까지 대기"""
    max_retries = 30
    retry_interval = 2

    services = [
        (service_urls["gateway"], "API-Gateway"),
        (service_urls["user_service"], "User-Service"),
        (service_urls["course_service"], "Course-Service"),
    ]

    for url, name in services:
        health_url = f"{url}/actuator/health"
        for i in range(max_retries):
            try:
                response = requests.get(health_url, timeout=2)
                if response.status_code == 200:
                    print(f"[OK] {name} is ready")
                    break
            except requests.exceptions.RequestException:
                if i == max_retries - 1:
                    pytest.fail(f"[FAIL] {name} not ready after {max_retries * retry_interval}s")
                time.sleep(retry_interval)


@pytest.fixture(scope="function")
def clean_test_data(test_user_id, service_urls):
    """테스트 데이터 정리 (Before & After)"""
    # Before: 테스트 전 정리
    # User-Service의 Credentials 삭제는 API가 없으므로 DB 직접 정리 필요
    # 또는 테스트 전용 userId 사용하여 충돌 방지

    yield

    # After: 테스트 후 정리 (선택사항)
    # 실제 Canvas 데이터이므로 정리하지 않고 남겨둘 수도 있음


def wait_for_sync(timeout_seconds=60, poll_interval=2):
    """
    동기화가 완료될 때까지 대기하는 헬퍼 함수

    Args:
        timeout_seconds: 최대 대기 시간 (초)
        poll_interval: 폴링 간격 (초)

    Returns:
        함수를 반환 (조건 체크 함수를 인자로 받음)
    """
    def _wait(check_fn, error_msg="Sync timeout"):
        start_time = time.time()
        while time.time() - start_time < timeout_seconds:
            if check_fn():
                return True
            time.sleep(poll_interval)
        raise TimeoutError(error_msg)
    return _wait