"""
Schedule-Service Todo API 테스트

할일 CRUD API 검증
"""

import pytest
import requests
from datetime import date, timedelta


class TestTodoApi:
    """Todo API 테스트"""

    def test_get_todos_empty(self, jwt_auth_tokens, service_urls):
        """
        할일 목록 조회 (빈 목록)

        Given: 할일이 없는 사용자
        When: GET /api/v1/todos 호출
        Then: 200 OK, 빈 배열
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 할일 목록 조회 (빈 목록)")
        response = requests.get(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

        print(f"  ✅ 할일 목록 조회 성공 ({len(data)}개)")

    def test_get_todo_by_id_not_found(self, jwt_auth_tokens, service_urls):
        """
        존재하지 않는 할일 조회

        Given: 존재하지 않는 todoId
        When: GET /api/v1/todos/{todoId} 호출
        Then: 404 Not Found
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 존재하지 않는 할일 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/todos/99999",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 404
        print(f"  ✅ 예상대로 404 Not Found")

    def test_delete_todo_not_found(self, jwt_auth_tokens, service_urls):
        """
        존재하지 않는 할일 삭제

        Given: 존재하지 않는 todoId
        When: DELETE /api/v1/todos/{todoId} 호출
        Then: 404 Not Found
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 존재하지 않는 할일 삭제")
        response = requests.delete(
            f"{gateway_url}/api/v1/todos/99999",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 404
        print(f"  ✅ 예상대로 404 Not Found")


class TestTodoCrud:
    """Todo CRUD 전체 플로우 테스트"""

    @pytest.fixture
    def test_category(self, jwt_auth_tokens, service_urls):
        """테스트용 카테고리 생성"""
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        category_data = {
            "name": "Todo 테스트 카테고리",
            "color": "#FF5733",
            "icon": "📝"
        }

        response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )

        if response.status_code == 201:
            category = response.json()
            yield category

            # Cleanup
            requests.delete(
                f"{gateway_url}/api/v1/categories/{category['categoryId']}",
                headers=headers,
                timeout=5
            )
        else:
            pytest.fail(f"카테고리 생성 실패: {response.status_code}")

    def test_create_todo_success(self, jwt_auth_tokens, service_urls, test_category):
        """
        할일 생성 성공

        Given: 유효한 할일 데이터
        When: POST /api/v1/todos 호출
        Then: 201 Created, 생성된 할일 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        todo_data = {
            "categoryId": test_category["categoryId"],
            "title": "테스트 할일",
            "description": "할일 API 테스트",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=7)).isoformat(),
            "priority": "HIGH"
        }

        print(f"\n[TEST] 할일 생성")
        response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            json=todo_data,
            timeout=5
        )

        assert response.status_code == 201, \
            f"할일 생성 실패: {response.status_code} - {response.text}"

        created_todo = response.json()
        assert created_todo["title"] == "테스트 할일"
        assert created_todo["priority"] == "HIGH"
        assert created_todo["status"] == "TODO"

        print(f"  ✅ 할일 생성 성공: ID={created_todo['todoId']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}",
            headers=headers,
            timeout=5
        )

    def test_create_todo_without_category(self, jwt_auth_tokens, service_urls):
        """
        카테고리 없이 할일 생성 (실패)

        Given: categoryId가 없는 할일 데이터
        When: POST /api/v1/todos 호출
        Then: 400 Bad Request
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        todo_data = {
            "title": "카테고리 없는 할일",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=7)).isoformat()
            # categoryId 누락
        }

        print(f"\n[TEST] 카테고리 없이 할일 생성 (실패 예상)")
        response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            json=todo_data,
            timeout=5
        )

        assert response.status_code == 400
        print(f"  ✅ 예상대로 400 Bad Request")

    def test_update_todo_success(self, jwt_auth_tokens, service_urls, test_category):
        """
        할일 수정 성공

        Given: 기존 할일
        When: PUT /api/v1/todos/{todoId} 호출
        Then: 200 OK, 수정된 할일 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 할일 생성
        todo_data = {
            "categoryId": test_category["categoryId"],
            "title": "수정 전 할일",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=7)).isoformat(),
            "priority": "LOW"
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            json=todo_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_todo = create_response.json()

        # 할일 수정
        updated_data = {
            "categoryId": test_category["categoryId"],
            "title": "수정된 할일",
            "description": "수정된 설명",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=14)).isoformat(),
            "priority": "HIGH"
        }

        print(f"\n[TEST] 할일 수정")
        update_response = requests.put(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}",
            headers=headers,
            json=updated_data,
            timeout=5
        )

        assert update_response.status_code == 200
        updated_todo = update_response.json()
        assert updated_todo["title"] == "수정된 할일"
        assert updated_todo["priority"] == "HIGH"

        print(f"  ✅ 할일 수정 성공")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}",
            headers=headers,
            timeout=5
        )

    def test_update_todo_status(self, jwt_auth_tokens, service_urls, test_category):
        """
        할일 상태 변경

        Given: 기존 할일 (TODO 상태)
        When: PATCH /api/v1/todos/{todoId}/status 호출
        Then: 200 OK, 상태가 변경됨
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 할일 생성
        todo_data = {
            "categoryId": test_category["categoryId"],
            "title": "상태 변경 테스트",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=7)).isoformat()
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            json=todo_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_todo = create_response.json()
        assert created_todo["status"] == "TODO"

        # 상태 변경: TODO → IN_PROGRESS
        print(f"\n[TEST] 할일 상태 변경 (TODO → IN_PROGRESS)")
        status_response = requests.patch(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}/status",
            headers=headers,
            json={"status": "IN_PROGRESS"},
            timeout=5
        )

        assert status_response.status_code == 200
        updated_todo = status_response.json()
        assert updated_todo["status"] == "IN_PROGRESS"
        print(f"  ✅ 상태 변경 성공: TODO → IN_PROGRESS")

        # 상태 변경: IN_PROGRESS → DONE
        status_response = requests.patch(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}/status",
            headers=headers,
            json={"status": "DONE"},
            timeout=5
        )

        assert status_response.status_code == 200
        updated_todo = status_response.json()
        assert updated_todo["status"] == "DONE"
        print(f"  ✅ 상태 변경 성공: IN_PROGRESS → DONE")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}",
            headers=headers,
            timeout=5
        )

    def test_update_todo_progress(self, jwt_auth_tokens, service_urls, test_category):
        """
        할일 진행률 업데이트

        Given: 기존 할일
        When: PATCH /api/v1/todos/{todoId}/progress 호출
        Then: 200 OK, 진행률이 업데이트됨
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 할일 생성
        todo_data = {
            "categoryId": test_category["categoryId"],
            "title": "진행률 테스트",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=7)).isoformat()
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            json=todo_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_todo = create_response.json()

        # 진행률 업데이트
        print(f"\n[TEST] 할일 진행률 업데이트")
        progress_response = requests.patch(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}/progress",
            headers=headers,
            json={"progressPercentage": 50},
            timeout=5
        )

        assert progress_response.status_code == 200
        updated_todo = progress_response.json()
        assert updated_todo["progressPercentage"] == 50
        print(f"  ✅ 진행률 업데이트 성공: 50%")

        # 100% 진행률
        progress_response = requests.patch(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}/progress",
            headers=headers,
            json={"progressPercentage": 100},
            timeout=5
        )

        assert progress_response.status_code == 200
        updated_todo = progress_response.json()
        assert updated_todo["progressPercentage"] == 100
        print(f"  ✅ 진행률 업데이트 성공: 100%")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}",
            headers=headers,
            timeout=5
        )

    def test_delete_todo_success(self, jwt_auth_tokens, service_urls, test_category):
        """
        할일 삭제 성공

        Given: 기존 할일
        When: DELETE /api/v1/todos/{todoId} 호출
        Then: 204 No Content
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 할일 생성
        todo_data = {
            "categoryId": test_category["categoryId"],
            "title": "삭제할 할일",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=7)).isoformat()
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            json=todo_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_todo = create_response.json()

        # 할일 삭제
        print(f"\n[TEST] 할일 삭제")
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}",
            headers=headers,
            timeout=5
        )

        assert delete_response.status_code == 204
        print(f"  ✅ 할일 삭제 성공")

        # 삭제 확인
        get_response = requests.get(
            f"{gateway_url}/api/v1/todos/{created_todo['todoId']}",
            headers=headers,
            timeout=5
        )
        assert get_response.status_code == 404
        print(f"  ✅ 삭제 확인: 404 Not Found")


class TestSubtaskApi:
    """서브태스크 API 테스트"""

    @pytest.fixture
    def test_category(self, jwt_auth_tokens, service_urls):
        """테스트용 카테고리 생성"""
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        category_data = {
            "name": "서브태스크 테스트 카테고리",
            "color": "#33FF57",
            "icon": "📋"
        }

        response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )

        if response.status_code == 201:
            category = response.json()
            yield category

            # Cleanup
            requests.delete(
                f"{gateway_url}/api/v1/categories/{category['categoryId']}",
                headers=headers,
                timeout=5
            )
        else:
            pytest.fail(f"카테고리 생성 실패: {response.status_code}")

    @pytest.fixture
    def parent_todo(self, jwt_auth_tokens, service_urls, test_category):
        """테스트용 부모 할일 생성"""
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        todo_data = {
            "categoryId": test_category["categoryId"],
            "title": "부모 할일",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=7)).isoformat()
        }

        response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=headers,
            json=todo_data,
            timeout=5
        )

        if response.status_code == 201:
            todo = response.json()
            yield todo

            # Cleanup
            requests.delete(
                f"{gateway_url}/api/v1/todos/{todo['todoId']}",
                headers=headers,
                timeout=5
            )
        else:
            pytest.fail(f"부모 할일 생성 실패: {response.status_code}")

    def test_get_subtasks_empty(self, jwt_auth_tokens, service_urls, parent_todo):
        """
        서브태스크 목록 조회 (빈 목록)

        Given: 서브태스크가 없는 부모 할일
        When: GET /api/v1/todos/{todoId}/subtasks 호출
        Then: 200 OK, 빈 배열
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 서브태스크 목록 조회 (빈 목록)")
        response = requests.get(
            f"{gateway_url}/api/v1/todos/{parent_todo['todoId']}/subtasks",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) == 0

        print(f"  ✅ 서브태스크 목록 조회 성공 (0개)")

    def test_create_subtask_success(self, jwt_auth_tokens, service_urls, test_category, parent_todo):
        """
        서브태스크 생성 성공

        Given: 부모 할일
        When: POST /api/v1/todos/{todoId}/subtasks 호출
        Then: 201 Created, parentTodoId가 설정된 할일 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        subtask_data = {
            "categoryId": test_category["categoryId"],
            "title": "서브태스크 1",
            "startDate": date.today().isoformat(),
            "dueDate": (date.today() + timedelta(days=3)).isoformat()
        }

        print(f"\n[TEST] 서브태스크 생성")
        response = requests.post(
            f"{gateway_url}/api/v1/todos/{parent_todo['todoId']}/subtasks",
            headers=headers,
            json=subtask_data,
            timeout=5
        )

        assert response.status_code == 201, \
            f"서브태스크 생성 실패: {response.status_code} - {response.text}"

        created_subtask = response.json()
        assert created_subtask["title"] == "서브태스크 1"
        assert created_subtask["parentTodoId"] == parent_todo["todoId"]

        print(f"  ✅ 서브태스크 생성 성공: ID={created_subtask['todoId']}, parentId={created_subtask['parentTodoId']}")

        # 서브태스크 목록 확인
        list_response = requests.get(
            f"{gateway_url}/api/v1/todos/{parent_todo['todoId']}/subtasks",
            headers=headers,
            timeout=5
        )

        assert list_response.status_code == 200
        subtasks = list_response.json()
        assert len(subtasks) == 1
        assert subtasks[0]["todoId"] == created_subtask["todoId"]

        print(f"  ✅ 서브태스크 목록 확인: {len(subtasks)}개")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/todos/{created_subtask['todoId']}",
            headers=headers,
            timeout=5
        )
