"""
User-Service Profile API 테스트

사용자 프로필 조회 API 검증
"""

import pytest
import requests


class TestProfileApi:
    """사용자 프로필 API 테스트"""

    def test_get_my_info_success(self, jwt_auth_tokens, service_urls):
        """
        내 정보 조회 성공

        Given: 유효한 JWT 토큰
        When: GET /api/v1/users/me 호출
        Then: 200 OK, 사용자 정보 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 내 정보 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/users/me",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200, \
            f"내 정보 조회 실패: {response.status_code} - {response.text}"

        user_info = response.json()
        assert "userId" in user_info or "id" in user_info
        assert "email" in user_info
        print(f"  ✅ 내 정보 조회 성공: {user_info.get('email', 'N/A')}")

    def test_get_my_info_without_token(self, service_urls):
        """
        토큰 없이 내 정보 조회 시 401

        Given: 인증 헤더 없음
        When: GET /api/v1/users/me 호출
        Then: 401 Unauthorized
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print(f"\n[TEST] 토큰 없이 내 정보 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/users/me",
            timeout=5
        )

        assert response.status_code == 401
        print(f"  ✅ 401 Unauthorized 반환")

    def test_get_my_info_with_invalid_token(self, service_urls):
        """
        잘못된 토큰으로 내 정보 조회 시 401

        Given: 잘못된 JWT 토큰
        When: GET /api/v1/users/me 호출
        Then: 401 Unauthorized
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        headers = {
            "Authorization": "Bearer invalid.jwt.token",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 잘못된 토큰으로 내 정보 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/users/me",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 401
        print(f"  ✅ 401 Unauthorized 반환")


class TestIntegrationStatusApi:
    """연동 상태 API 테스트"""

    def test_get_integration_status_success(self, jwt_auth_tokens, service_urls):
        """
        연동 상태 조회 성공

        Given: 유효한 JWT 토큰
        When: GET /api/v1/integrations/status 호출
        Then: 200 OK, 연동 상태 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 연동 상태 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/integrations/status",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200, \
            f"연동 상태 조회 실패: {response.status_code} - {response.text}"

        status = response.json()
        # Canvas 연동 상태가 포함되어야 함
        assert "canvas" in status or "canvasConnected" in status or "integrations" in status
        print(f"  ✅ 연동 상태 조회 성공")

    def test_get_integration_status_without_token(self, service_urls):
        """
        토큰 없이 연동 상태 조회 시 401

        Given: 인증 헤더 없음
        When: GET /api/v1/integrations/status 호출
        Then: 401 Unauthorized
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print(f"\n[TEST] 토큰 없이 연동 상태 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/integrations/status",
            timeout=5
        )

        assert response.status_code == 401
        print(f"  ✅ 401 Unauthorized 반환")
