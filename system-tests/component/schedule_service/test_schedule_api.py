"""
Schedule-Service Schedule API 테스트

일정 CRUD API 검증
"""

import pytest
import requests
from datetime import datetime, timedelta


class TestScheduleApi:
    """Schedule API 테스트"""

    def test_get_schedules_empty(self, jwt_auth_tokens, service_urls):
        """
        일정 목록 조회 (빈 목록)

        Given: 일정이 없는 사용자
        When: GET /api/v1/schedules 호출
        Then: 200 OK, 빈 배열
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 일정 목록 조회 (빈 목록)")
        response = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

        print(f"  ✅ 일정 목록 조회 성공 ({len(data)}개)")

    def test_create_schedule_without_category(self, jwt_auth_tokens, service_urls):
        """
        카테고리 없이 일정 생성 (실패)

        Given: categoryId가 없는 일정 데이터
        When: POST /api/v1/schedules 호출
        Then: 400 Bad Request
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        schedule_data = {
            "title": "테스트 일정",
            "description": "카테고리 없는 일정",
            "startTime": (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT10:00:00"),
            "endTime": (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT11:00:00"),
            "isAllDay": False
            # categoryId 누락
        }

        print(f"\n[TEST] 카테고리 없이 일정 생성 (실패 예상)")
        response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert response.status_code == 400
        print(f"  ✅ 예상대로 400 Bad Request")

    def test_get_schedule_by_id_not_found(self, jwt_auth_tokens, service_urls):
        """
        존재하지 않는 일정 조회

        Given: 존재하지 않는 scheduleId
        When: GET /api/v1/schedules/{scheduleId} 호출
        Then: 404 Not Found
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 존재하지 않는 일정 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/schedules/99999",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 404
        print(f"  ✅ 예상대로 404 Not Found")

    def test_delete_schedule_not_found(self, jwt_auth_tokens, service_urls):
        """
        존재하지 않는 일정 삭제

        Given: 존재하지 않는 scheduleId
        When: DELETE /api/v1/schedules/{scheduleId} 호출
        Then: 404 Not Found
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 존재하지 않는 일정 삭제")
        response = requests.delete(
            f"{gateway_url}/api/v1/schedules/99999",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 404
        print(f"  ✅ 예상대로 404 Not Found")


class TestCategoryApi:
    """Category API 테스트"""

    def test_get_categories_empty(self, jwt_auth_tokens, service_urls):
        """
        카테고리 목록 조회 (빈 목록)

        Given: 카테고리가 없는 사용자
        When: GET /api/v1/categories 호출
        Then: 200 OK, 빈 배열
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 카테고리 목록 조회 (빈 목록)")
        response = requests.get(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

        print(f"  ✅ 카테고리 목록 조회 성공 ({len(data)}개)")
