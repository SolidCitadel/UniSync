"""
Todo 관리 E2E 시나리오 테스트

사용자의 전체 Todo 관리 플로우 검증
"""

import pytest
import requests
from datetime import datetime, timedelta


class TestTodoJourney:
    """Todo 관리 전체 여정 테스트"""

    def test_complete_todo_workflow(self, jwt_auth_tokens, service_urls):
        """
        Todo 전체 워크플로우 테스트

        시나리오:
        1. 카테고리 생성 (학업)
        2. Todo 생성 (기말 프로젝트)
        3. 서브태스크 추가 (기획서 작성, 코드 구현, 테스트)
        4. Todo 진행률 업데이트
        5. 서브태스크 완료 처리
        6. Todo 상태 완료로 변경
        7. Todo 삭제
        8. 카테고리 삭제
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[SCENARIO] Todo 전체 워크플로우")

        # ========== 1. 카테고리 생성 ==========
        print(f"\n  [1/8] 카테고리 생성")
        category_data = {
            "name": "학업",
            "color": "#4A90D9",
            "icon": "📚"
        }

        category_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )

        assert category_response.status_code == 201, \
            f"카테고리 생성 실패: {category_response.status_code}"
        category = category_response.json()
        category_id = category["categoryId"]
        print(f"    ✅ 카테고리 생성: ID={category_id}")

        try:
            # ========== 2. Todo 생성 ==========
            print(f"\n  [2/8] Todo 생성")
            today = datetime.now()
            due_date = (today + timedelta(days=14)).strftime("%Y-%m-%d")

            todo_data = {
                "title": "기말 프로젝트",
                "description": "소프트웨어 공학 기말 프로젝트",
                "startDate": today.strftime("%Y-%m-%d"),
                "dueDate": due_date,
                "priority": "HIGH",
                "categoryId": category_id
            }

            todo_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                json=todo_data,
                timeout=5
            )

            assert todo_response.status_code == 201, \
                f"Todo 생성 실패: {todo_response.status_code}"
            todo = todo_response.json()
            todo_id = todo["todoId"]
            print(f"    ✅ Todo 생성: ID={todo_id}, title={todo['title']}")

            # ========== 3. 서브태스크 추가 ==========
            print(f"\n  [3/8] 서브태스크 추가")
            subtasks = [
                {"title": "기획서 작성", "priority": "HIGH"},
                {"title": "코드 구현", "priority": "HIGH"},
                {"title": "테스트 작성", "priority": "MEDIUM"}
            ]

            subtask_ids = []
            for subtask_data in subtasks:
                subtask_data["parentTodoId"] = todo_id
                subtask_data["startDate"] = today.strftime("%Y-%m-%d")
                subtask_data["dueDate"] = due_date
                subtask_data["categoryId"] = category_id

                subtask_response = requests.post(
                    f"{gateway_url}/api/v1/todos",
                    headers=headers,
                    json=subtask_data,
                    timeout=5
                )

                assert subtask_response.status_code == 201, \
                    f"서브태스크 생성 실패: {subtask_response.status_code}"
                subtask = subtask_response.json()
                subtask_ids.append(subtask["todoId"])
                print(f"    ✅ 서브태스크 생성: {subtask['title']}")

            # ========== 4. Todo 진행률 업데이트 ==========
            print(f"\n  [4/8] Todo 진행률 업데이트")
            progress_response = requests.patch(
                f"{gateway_url}/api/v1/todos/{todo_id}/progress",
                headers=headers,
                json={"progressPercentage": 30},
                timeout=5
            )

            assert progress_response.status_code == 200, \
                f"진행률 업데이트 실패: {progress_response.status_code}"
            updated_todo = progress_response.json()
            assert updated_todo["progressPercentage"] == 30
            print(f"    ✅ 진행률 업데이트: 30%")

            # ========== 5. 서브태스크 완료 처리 ==========
            print(f"\n  [5/8] 서브태스크 완료 처리")
            for subtask_id in subtask_ids[:2]:  # 첫 두 개 완료
                status_response = requests.patch(
                    f"{gateway_url}/api/v1/todos/{subtask_id}/status",
                    headers=headers,
                    json={"status": "DONE"},
                    timeout=5
                )

                assert status_response.status_code == 200, \
                    f"서브태스크 상태 변경 실패: {status_response.status_code}"
                print(f"    ✅ 서브태스크 완료: ID={subtask_id}")

            # ========== 6. Todo 상태 완료로 변경 ==========
            print(f"\n  [6/8] Todo 상태 완료로 변경")

            # 진행률 100%로 업데이트
            requests.patch(
                f"{gateway_url}/api/v1/todos/{todo_id}/progress",
                headers=headers,
                json={"progressPercentage": 100},
                timeout=5
            )

            # 상태를 DONE으로 변경
            done_response = requests.patch(
                f"{gateway_url}/api/v1/todos/{todo_id}/status",
                headers=headers,
                json={"status": "DONE"},
                timeout=5
            )

            assert done_response.status_code == 200, \
                f"Todo 상태 변경 실패: {done_response.status_code}"
            done_todo = done_response.json()
            assert done_todo["status"] == "DONE"
            print(f"    ✅ Todo 완료 상태로 변경")

            # ========== 7. Todo 삭제 ==========
            print(f"\n  [7/8] Todo 삭제")

            # 서브태스크 먼저 삭제
            for subtask_id in subtask_ids:
                requests.delete(
                    f"{gateway_url}/api/v1/todos/{subtask_id}",
                    headers=headers,
                    timeout=5
                )

            # 메인 Todo 삭제
            delete_response = requests.delete(
                f"{gateway_url}/api/v1/todos/{todo_id}",
                headers=headers,
                timeout=5
            )

            assert delete_response.status_code == 204, \
                f"Todo 삭제 실패: {delete_response.status_code}"
            print(f"    ✅ Todo 삭제 완료")

        finally:
            # ========== 8. 카테고리 삭제 (Cleanup) ==========
            print(f"\n  [8/8] 카테고리 삭제")
            cleanup_response = requests.delete(
                f"{gateway_url}/api/v1/categories/{category_id}",
                headers=headers,
                timeout=5
            )
            print(f"    ✅ 카테고리 삭제 완료")

        print(f"\n[SCENARIO] ✅ Todo 전체 워크플로우 완료")


class TestTodoPriorityWorkflow:
    """Todo 우선순위 관리 테스트"""

    def test_todo_priority_filtering(self, jwt_auth_tokens, service_urls):
        """
        우선순위별 Todo 필터링

        시나리오:
        1. 다양한 우선순위의 Todo 생성
        2. 우선순위별 필터링 확인
        3. 정리
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[SCENARIO] Todo 우선순위 필터링")

        # 카테고리 생성
        category_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json={"name": "테스트", "color": "#FF0000", "icon": "🔥"},
            timeout=5
        )

        assert category_response.status_code == 201
        category_id = category_response.json()["categoryId"]

        today = datetime.now().strftime("%Y-%m-%d")
        due_date = (datetime.now() + timedelta(days=7)).strftime("%Y-%m-%d")

        created_todos = []
        try:
            # 다양한 우선순위의 Todo 생성
            priorities = ["HIGH", "MEDIUM", "LOW"]
            for priority in priorities:
                todo_response = requests.post(
                    f"{gateway_url}/api/v1/todos",
                    headers=headers,
                    json={
                        "title": f"{priority} 우선순위 Todo",
                        "startDate": today,
                        "dueDate": due_date,
                        "priority": priority,
                        "categoryId": category_id
                    },
                    timeout=5
                )

                assert todo_response.status_code == 201
                created_todos.append(todo_response.json()["todoId"])
                print(f"  ✅ {priority} 우선순위 Todo 생성")

            # 전체 목록 조회
            list_response = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                timeout=5
            )

            assert list_response.status_code == 200
            todos = list_response.json()
            print(f"  ✅ 전체 Todo 조회: {len(todos)}개")

        finally:
            # Cleanup
            for todo_id in created_todos:
                requests.delete(
                    f"{gateway_url}/api/v1/todos/{todo_id}",
                    headers=headers,
                    timeout=5
                )

            requests.delete(
                f"{gateway_url}/api/v1/categories/{category_id}",
                headers=headers,
                timeout=5
            )

        print(f"\n[SCENARIO] ✅ Todo 우선순위 필터링 완료")
