"""
Schedule-Service Category CRUD 테스트

카테고리 CRUD 완전 검증
"""

import pytest
import requests


class TestCategoryCrud:
    """Category CRUD 전체 플로우 테스트"""

    def test_create_category_success(self, jwt_auth_tokens, service_urls):
        """
        카테고리 생성 성공

        Given: 유효한 카테고리 데이터
        When: POST /api/v1/categories 호출
        Then: 201 Created, 생성된 카테고리 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        category_data = {
            "name": "학업",
            "color": "#FF5733",
            "icon": "📚"
        }

        print(f"\n[TEST] 카테고리 생성")
        response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )

        assert response.status_code == 201, \
            f"카테고리 생성 실패: {response.status_code} - {response.text}"

        created_category = response.json()
        assert created_category["name"] == "학업"
        assert created_category["color"] == "#FF5733"
        assert created_category["categoryId"] is not None

        print(f"  ✅ 카테고리 생성 성공: ID={created_category['categoryId']}, name={created_category['name']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/categories/{created_category['categoryId']}",
            headers=headers,
            timeout=5
        )

    def test_get_category_by_id_success(self, jwt_auth_tokens, service_urls):
        """
        카테고리 상세 조회 성공

        Given: 생성된 카테고리
        When: GET /api/v1/categories/{categoryId} 호출
        Then: 200 OK, 카테고리 상세 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 카테고리 생성
        category_data = {
            "name": "조회 테스트",
            "color": "#00FF00",
            "icon": "🔍"
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_category = create_response.json()

        # 카테고리 상세 조회
        print(f"\n[TEST] 카테고리 상세 조회")
        get_response = requests.get(
            f"{gateway_url}/api/v1/categories/{created_category['categoryId']}",
            headers=headers,
            timeout=5
        )

        assert get_response.status_code == 200
        category = get_response.json()
        assert category["categoryId"] == created_category["categoryId"]
        assert category["name"] == "조회 테스트"
        assert category["color"] == "#00FF00"

        print(f"  ✅ 카테고리 상세 조회 성공: {category['name']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/categories/{created_category['categoryId']}",
            headers=headers,
            timeout=5
        )

    def test_update_category_success(self, jwt_auth_tokens, service_urls):
        """
        카테고리 수정 성공

        Given: 기존 카테고리
        When: PUT /api/v1/categories/{categoryId} 호출
        Then: 200 OK, 수정된 카테고리 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 카테고리 생성
        category_data = {
            "name": "수정 전",
            "color": "#0000FF",
            "icon": "📝"
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_category = create_response.json()

        # 카테고리 수정
        updated_data = {
            "name": "수정 후",
            "color": "#FF0000",
            "icon": "✏️"
        }

        print(f"\n[TEST] 카테고리 수정")
        update_response = requests.put(
            f"{gateway_url}/api/v1/categories/{created_category['categoryId']}",
            headers=headers,
            json=updated_data,
            timeout=5
        )

        assert update_response.status_code == 200
        updated_category = update_response.json()
        assert updated_category["name"] == "수정 후"
        assert updated_category["color"] == "#FF0000"
        assert updated_category["icon"] == "✏️"

        print(f"  ✅ 카테고리 수정 성공: {updated_category['name']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/categories/{created_category['categoryId']}",
            headers=headers,
            timeout=5
        )

    def test_delete_category_success(self, jwt_auth_tokens, service_urls):
        """
        카테고리 삭제 성공

        Given: 기존 카테고리
        When: DELETE /api/v1/categories/{categoryId} 호출
        Then: 204 No Content
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 카테고리 생성
        category_data = {
            "name": "삭제할 카테고리",
            "color": "#FFFF00",
            "icon": "🗑️"
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_category = create_response.json()

        # 카테고리 삭제
        print(f"\n[TEST] 카테고리 삭제")
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/categories/{created_category['categoryId']}",
            headers=headers,
            timeout=5
        )

        assert delete_response.status_code == 204
        print(f"  ✅ 카테고리 삭제 성공")

        # 삭제 확인
        get_response = requests.get(
            f"{gateway_url}/api/v1/categories/{created_category['categoryId']}",
            headers=headers,
            timeout=5
        )
        assert get_response.status_code == 404
        print(f"  ✅ 삭제 확인: 404 Not Found")

    def test_get_categories_list(self, jwt_auth_tokens, service_urls):
        """
        카테고리 목록 조회

        Given: 여러 카테고리 생성
        When: GET /api/v1/categories 호출
        Then: 200 OK, 카테고리 배열 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 여러 카테고리 생성
        categories_to_create = [
            {"name": "목록 테스트 1", "color": "#FF0000", "icon": "1️⃣"},
            {"name": "목록 테스트 2", "color": "#00FF00", "icon": "2️⃣"},
            {"name": "목록 테스트 3", "color": "#0000FF", "icon": "3️⃣"},
        ]

        created_ids = []
        for cat_data in categories_to_create:
            response = requests.post(
                f"{gateway_url}/api/v1/categories",
                headers=headers,
                json=cat_data,
                timeout=5
            )
            if response.status_code == 201:
                created_ids.append(response.json()["categoryId"])

        # 목록 조회
        print(f"\n[TEST] 카테고리 목록 조회")
        list_response = requests.get(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            timeout=5
        )

        assert list_response.status_code == 200
        categories = list_response.json()
        assert isinstance(categories, list)

        # 생성한 카테고리가 목록에 있는지 확인
        created_names = {cat["name"] for cat in categories_to_create}
        returned_names = {cat["name"] for cat in categories}
        assert created_names.issubset(returned_names)

        print(f"  ✅ 카테고리 목록 조회 성공: {len(categories)}개")

        # Cleanup
        for cat_id in created_ids:
            requests.delete(
                f"{gateway_url}/api/v1/categories/{cat_id}",
                headers=headers,
                timeout=5
            )

    def test_create_category_missing_required_fields(self, jwt_auth_tokens, service_urls):
        """
        필수 필드 누락 시 카테고리 생성 실패

        Given: name 또는 color가 없는 데이터
        When: POST /api/v1/categories 호출
        Then: 400 Bad Request
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # name 누락
        print(f"\n[TEST] 카테고리 생성 - 필수 필드 누락")
        response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json={"color": "#FF0000"},  # name 누락
            timeout=5
        )

        assert response.status_code == 400
        print(f"  ✅ name 누락 시 400 Bad Request")

        # color 누락
        response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json={"name": "테스트"},  # color 누락
            timeout=5
        )

        assert response.status_code == 400
        print(f"  ✅ color 누락 시 400 Bad Request")
