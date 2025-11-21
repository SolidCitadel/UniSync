"""
API Gateway 인증 테스트

JWT 인증 성공/실패 시나리오 검증
"""

import pytest
import requests


class TestJwtAuthentication:
    """JWT 인증 테스트"""

    def test_jwt_authentication_success(self, jwt_auth_tokens, service_urls):
        """
        JWT 인증 성공 시나리오

        Given: 유효한 JWT 토큰
        When: API Gateway를 통해 보호된 엔드포인트 호출
        Then: 정상 응답 (200 OK)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 유효한 JWT 토큰으로 API 호출")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200, \
            f"유효한 JWT로 인증 실패: {response.status_code}"

        print(f"  ✅ 인증 성공: {response.status_code}")

    def test_jwt_authentication_no_header(self, service_urls):
        """
        JWT 인증 실패: Authorization 헤더 없음

        Given: Authorization 헤더 없음
        When: API Gateway를 통해 보호된 엔드포인트 호출
        Then: 401 Unauthorized
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print(f"\n[TEST] Authorization 헤더 없이 API 호출")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            timeout=5
        )

        assert response.status_code == 401, \
            f"예상: 401, 실제: {response.status_code}"

        print(f"  ✅ 예상대로 401 Unauthorized")

    def test_jwt_authentication_invalid_token(self, service_urls):
        """
        JWT 인증 실패: 잘못된 토큰

        Given: 잘못된 JWT 토큰
        When: API Gateway를 통해 보호된 엔드포인트 호출
        Then: 401 Unauthorized
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        headers = {
            "Authorization": "Bearer invalid-token-12345"
        }

        print(f"\n[TEST] 잘못된 JWT 토큰으로 API 호출")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 401, \
            f"예상: 401, 실제: {response.status_code}"

        print(f"  ✅ 예상대로 401 Unauthorized")

    def test_jwt_authentication_no_bearer_prefix(self, service_urls):
        """
        JWT 인증 실패: Bearer 접두사 없음

        Given: Bearer 접두사가 없는 토큰
        When: API Gateway를 통해 보호된 엔드포인트 호출
        Then: 401 Unauthorized
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        headers = {
            "Authorization": "some-random-token-without-bearer"
        }

        print(f"\n[TEST] Bearer 접두사 없는 토큰으로 API 호출")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 401, \
            f"예상: 401, 실제: {response.status_code}"

        print(f"  ✅ 예상대로 401 Unauthorized")

    def test_internal_api_blocked(self, service_urls):
        """
        내부 API 접근 차단 테스트

        Given: 내부 API 엔드포인트 (/api/internal/*)
        When: API Gateway를 통해 접근 시도
        Then: 403 Forbidden
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print(f"\n[TEST] 내부 API 접근 시도")
        response = requests.get(
            f"{gateway_url}/api/internal/v1/credentials/canvas/by-cognito-sub/test",
            timeout=5
        )

        assert response.status_code == 403, \
            f"예상: 403 Forbidden, 실제: {response.status_code}"

        print(f"  ✅ 예상대로 403 Forbidden (내부 API 차단됨)")


class TestPublicEndpoints:
    """인증 불필요 엔드포인트 테스트"""

    def test_signup_without_auth(self, service_urls, test_user_credentials):
        """
        회원가입은 인증 없이 가능

        Given: Authorization 헤더 없음
        When: /api/v1/auth/signup 호출
        Then: 201 Created (정상 처리)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # 고유한 이메일 생성
        import time
        unique_email = f"test-{int(time.time())}@example.com"

        signup_data = {
            "email": unique_email,
            "password": "TestPassword123!",
            "name": "Test User"
        }

        print(f"\n[TEST] 인증 없이 회원가입 시도")
        response = requests.post(
            f"{gateway_url}/api/v1/auth/signup",
            json=signup_data,
            timeout=10
        )

        assert response.status_code == 201, \
            f"회원가입 실패: {response.status_code} - {response.text}"

        print(f"  ✅ 인증 없이 회원가입 성공: {response.status_code}")

    def test_signin_without_auth(self, service_urls):
        """
        로그인은 인증 없이 가능

        Given: Authorization 헤더 없음
        When: /api/v1/auth/signin 호출
        Then: 401 또는 200 (자격증명에 따라)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        signin_data = {
            "email": "nonexistent@example.com",
            "password": "WrongPassword123!"
        }

        print(f"\n[TEST] 인증 없이 로그인 시도 (잘못된 자격증명)")
        response = requests.post(
            f"{gateway_url}/api/v1/auth/signin",
            json=signin_data,
            timeout=10
        )

        # 로그인 실패는 401이 맞지만, 인증 헤더가 없어도 요청은 처리됨
        assert response.status_code in [400, 401], \
            f"예상: 400/401, 실제: {response.status_code}"

        print(f"  ✅ 인증 없이 로그인 요청 처리됨: {response.status_code}")
