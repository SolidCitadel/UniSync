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


@pytest.fixture(scope="session")
def test_user_credentials():
    """E2E 테스트용 사용자 자격 증명"""
    timestamp = int(time.time())
    return {
        "email": f"e2e-test-{timestamp}@unisync.com",
        "password": "TestPassword123!",
        "name": "E2E Test User"
    }


@pytest.fixture(scope="function")
def jwt_auth_tokens(test_user_credentials, service_urls):
    """
    회원가입 및 로그인을 통해 JWT 토큰 획득
    각 테스트마다 새로운 사용자를 생성하여 독립성 보장
    """
    gateway_url = service_urls["gateway"]

    print(f"\n[Setup] JWT 인증 - 회원가입 중...")

    # 1. 회원가입 (API Gateway 경유)
    signup_response = requests.post(
        f"{gateway_url}/api/v1/auth/signup",
        json=test_user_credentials,
        timeout=10
    )

    if signup_response.status_code != 201:
        pytest.fail(f"회원가입 실패: {signup_response.status_code} - {signup_response.text}")

    signup_data = signup_response.json()
    print(f"[Setup] 회원가입 완료: cognitoSub={signup_data.get('cognitoSub')}, email={signup_data.get('email')}")

    # 회원가입 시 자동 로그인되어 토큰이 반환됨
    return {
        "id_token": signup_data.get("idToken"),
        "access_token": signup_data.get("accessToken"),
        "refresh_token": signup_data.get("refreshToken"),
        "cognito_sub": signup_data.get("cognitoSub"),
        "email": signup_data.get("email")
    }


def extract_cognito_sub_from_token(id_token):
    """JWT ID Token에서 Cognito Sub 추출"""
    import base64
    import json

    if not id_token:
        return None

    try:
        # JWT를 . 으로 분리
        parts = id_token.split(".")
        if len(parts) != 3:
            return None

        # Payload(두 번째 부분) 디코딩
        payload = parts[1]
        # URL-safe base64 디코딩 (패딩 추가)
        padding = len(payload) % 4
        if padding:
            payload += '=' * (4 - padding)

        decoded = base64.urlsafe_b64decode(payload)
        claims = json.loads(decoded)

        return claims.get("sub")
    except Exception as e:
        print(f"[WARN] JWT 파싱 실패: {e}")
        return None


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