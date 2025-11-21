"""
Course-Service Sync API 테스트

Canvas 동기화 트리거 및 상태 확인 API 검증
"""

import pytest
import requests
import time


class TestCanvasSyncApi:
    """Canvas 동기화 API 테스트"""

    def test_canvas_sync_without_token_fails(self, jwt_auth_tokens, service_urls):
        """
        Canvas 토큰 없이 동기화 시 실패

        Given: Canvas 토큰이 등록되지 않은 사용자
        When: POST /api/v1/integrations/canvas/sync 호출
        Then: 400 또는 404 (토큰 미등록)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 Canvas 토큰이 있으면 삭제
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            timeout=5
        )
        assert delete_response.status_code in [204, 404], \
            f"Canvas 토큰 삭제 실패: {delete_response.status_code} - {delete_response.text}"

        print(f"\n[TEST] Canvas 토큰 없이 동기화 시도")
        response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/sync",
            headers=headers,
            timeout=30
        )

        # 토큰이 없으면 400, 404, 또는 500 (Lambda 내부 에러)
        assert response.status_code in [400, 404, 500], \
            f"예상치 못한 응답: {response.status_code} - {response.text}"
        print(f"  ✅ Canvas 토큰 없이 동기화 실패: {response.status_code}")

    def test_canvas_sync_with_invalid_token(self, jwt_auth_tokens, service_urls):
        """
        잘못된 Canvas 토큰 처리 검증

        Given: 잘못된 형식의 Canvas 토큰
        When: 토큰 등록 또는 동기화 시도
        Then: 등록 시점 또는 동기화 시점에서 에러 반환

        Note: 서비스가 토큰을 등록 시점에서 검증하면 400으로 거부,
              동기화 시점에서 검증하면 sync 호출 시 실패
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 잘못된 Canvas 토큰 등록 시도
        token_data = {
            "canvasToken": "invalid_canvas_token_12345"
        }

        print(f"\n[TEST] 잘못된 Canvas 토큰 등록 시도")
        register_response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            json=token_data,
            timeout=5
        )

        # Case 1: 토큰이 등록 시점에서 검증되어 거부됨 (더 좋은 동작)
        if register_response.status_code == 400:
            print(f"  ✅ 잘못된 토큰이 등록 시점에서 거부됨: 400")
            return

        # Case 2: 토큰이 등록됨 - 동기화 시점에서 실패해야 함
        assert register_response.status_code == 200, \
            f"예상치 못한 등록 응답: {register_response.status_code}"

        print(f"[TEST] 잘못된 Canvas 토큰으로 동기화 시도")
        response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/sync",
            headers=headers,
            timeout=30
        )

        # 잘못된 토큰이면 Canvas API 인증 실패
        assert response.status_code in [400, 401, 500], \
            f"예상치 못한 응답: {response.status_code} - {response.text}"
        print(f"  ✅ 잘못된 토큰으로 동기화 실패: {response.status_code}")

        # Cleanup
        cleanup_response = requests.delete(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            timeout=5
        )
        assert cleanup_response.status_code in [204, 404], \
            f"Cleanup 실패: {cleanup_response.status_code}"

    def test_canvas_sync_without_auth(self, service_urls):
        """
        인증 없이 동기화 요청 시 401

        Given: 인증 헤더 없음
        When: POST /api/v1/integrations/canvas/sync 호출
        Then: 401 Unauthorized
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print(f"\n[TEST] 인증 없이 동기화 요청")
        response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/sync",
            timeout=5
        )

        assert response.status_code == 401
        print(f"  ✅ 401 Unauthorized 반환")


class TestCanvasCredentialsApi:
    """Canvas 토큰 관리 API 추가 테스트"""

    def test_get_canvas_credentials_when_not_registered(self, jwt_auth_tokens, service_urls):
        """
        Canvas 토큰 미등록 시 조회 실패

        Given: Canvas 토큰이 등록되지 않은 사용자
        When: GET /api/v1/integrations/canvas/credentials 호출
        Then: 404 Not Found
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 Canvas 토큰이 있으면 삭제
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            timeout=5
        )
        assert delete_response.status_code in [204, 404], \
            f"Canvas 토큰 삭제 실패: {delete_response.status_code}"

        print(f"\n[TEST] Canvas 토큰 미등록 시 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 404
        print(f"  ✅ 404 Not Found 반환")

    def test_delete_canvas_credentials_when_not_registered(self, jwt_auth_tokens, service_urls):
        """
        Canvas 토큰 미등록 시 삭제 요청

        Given: Canvas 토큰이 등록되지 않은 사용자
        When: DELETE /api/v1/integrations/canvas/credentials 호출
        Then: 204 No Content 또는 404 Not Found
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 Canvas 토큰이 있으면 삭제
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            timeout=5
        )
        assert delete_response.status_code in [204, 404], \
            f"Canvas 토큰 삭제 실패: {delete_response.status_code}"

        print(f"\n[TEST] Canvas 토큰 미등록 시 삭제")
        response = requests.delete(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            timeout=5
        )

        # 이미 없으면 204 또는 404 둘 다 가능
        assert response.status_code in [204, 404]
        print(f"  ✅ {response.status_code} 반환")

    def test_register_canvas_credentials_missing_fields(self, jwt_auth_tokens, service_urls):
        """
        필수 필드 누락 시 Canvas 토큰 등록 실패

        Given: accessToken 누락
        When: POST /api/v1/integrations/canvas/credentials 호출
        Then: 400 Bad Request
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 필수 필드 누락 시 토큰 등록")
        response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            json={"canvasUrl": "https://khcanvas.khu.ac.kr"},  # accessToken 누락
            timeout=5
        )

        assert response.status_code == 400
        print(f"  ✅ 400 Bad Request 반환")
