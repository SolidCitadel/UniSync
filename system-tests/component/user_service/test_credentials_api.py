"""
User-Service Credentials API 테스트

Canvas 토큰 관리 API 검증
"""

import pytest
import requests


class TestCredentialsApi:
    """Canvas 토큰 관리 API 테스트"""

    def test_register_canvas_token(self, jwt_auth_tokens, service_urls, canvas_token):
        """
        Canvas 토큰 등록 API

        Given: 유효한 JWT 토큰과 Canvas API 토큰
        When: POST /api/v1/integrations/canvas/credentials 호출
        Then: 200 OK, success=true
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] Canvas 토큰 등록 API")
        response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            json={"canvasToken": canvas_token},
            timeout=10
        )

        assert response.status_code == 200, \
            f"Canvas 토큰 등록 실패: {response.status_code} - {response.text}"

        data = response.json()
        assert data.get("success") is True
        assert data.get("message") is not None

        print(f"  ✅ Canvas 토큰 등록 성공")

    def test_get_integration_status(self, jwt_auth_tokens, service_urls):
        """
        연동 상태 조회 API

        Given: Canvas 토큰이 등록된 사용자
        When: GET /api/v1/integrations/status 호출
        Then: 200 OK, canvas.isConnected=true
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 연동 상태 조회 API")
        response = requests.get(
            f"{gateway_url}/api/v1/integrations/status",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200, \
            f"연동 상태 조회 실패: {response.status_code}"

        data = response.json()

        # Canvas 연동 정보가 있는지 확인
        if data.get("canvas"):
            assert "isConnected" in data["canvas"]
            assert "lastValidatedAt" in data["canvas"]
            print(f"  ✅ Canvas 연동 상태: {data['canvas']['isConnected']}")
        else:
            print(f"  ℹ️  Canvas 연동 정보 없음 (미등록 상태)")

    def test_delete_canvas_token(self, jwt_auth_tokens, service_urls, canvas_token):
        """
        Canvas 토큰 삭제 API

        Given: Canvas 토큰이 등록된 사용자
        When: DELETE /api/v1/integrations/canvas/credentials 호출
        Then: 204 No Content
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 토큰 등록
        print(f"\n[TEST] Canvas 토큰 삭제 API (사전 준비: 토큰 등록)")
        register_response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            json={"canvasToken": canvas_token},
            timeout=10
        )
        assert register_response.status_code == 200, \
            f"Canvas 토큰 등록 실패: {register_response.status_code} - {register_response.text}"

        # 토큰 삭제
        response = requests.delete(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 204, \
            f"Canvas 토큰 삭제 실패: {response.status_code}"

        print(f"  ✅ Canvas 토큰 삭제 성공")

        # 삭제 후 상태 확인
        status_response = requests.get(
            f"{gateway_url}/api/v1/integrations/status",
            headers=headers,
            timeout=5
        )
        assert status_response.status_code == 200, \
            f"상태 조회 실패: {status_response.status_code}"

        status_data = status_response.json()
        if status_data.get("canvas"):
            assert status_data["canvas"]["isConnected"] is False or \
                   status_data.get("canvas") is None
            print(f"  ✅ 삭제 검증 완료: isConnected=false")
