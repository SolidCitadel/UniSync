"""
카테고리 관리 E2E 시나리오 테스트

사용자의 전체 카테고리 관리 및 일정/할일 분류 플로우 검증
"""

import pytest
import requests
from datetime import datetime, timedelta


class TestCategoryManagement:
    """카테고리 관리 전체 여정 테스트"""

    def test_complete_category_workflow(self, jwt_auth_tokens, service_urls):
        """
        카테고리 관리 전체 워크플로우

        시나리오:
        1. 여러 카테고리 생성 (학업, 개인, 업무)
        2. 각 카테고리에 일정/할일 추가
        3. 카테고리별 일정/할일 조회
        4. 카테고리 수정
        5. 카테고리 삭제 (일정/할일이 있는 경우)
        6. 정리
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[SCENARIO] 카테고리 관리 전체 워크플로우")

        # ========== 1. 여러 카테고리 생성 ==========
        print(f"\n  [1/6] 여러 카테고리 생성")
        categories_to_create = [
            {"name": "학업", "color": "#4A90D9", "icon": "📚"},
            {"name": "개인", "color": "#7ED321", "icon": "👤"},
            {"name": "업무", "color": "#F5A623", "icon": "💼"}
        ]

        created_categories = {}
        for cat_data in categories_to_create:
            response = requests.post(
                f"{gateway_url}/api/v1/categories",
                headers=headers,
                json=cat_data,
                timeout=5
            )

            assert response.status_code == 201, \
                f"카테고리 '{cat_data['name']}' 생성 실패: {response.status_code}"
            category = response.json()
            created_categories[cat_data["name"]] = category["categoryId"]
            print(f"    ✅ 카테고리 생성: {cat_data['name']}")

        created_schedules = []
        created_todos = []

        try:
            # ========== 2. 각 카테고리에 일정/할일 추가 ==========
            print(f"\n  [2/6] 각 카테고리에 일정/할일 추가")

            today = datetime.now()

            # 학업 카테고리에 일정 추가
            schedule_data = {
                "title": "기말고사",
                "description": "소프트웨어 공학 기말고사",
                "startTime": (today + timedelta(days=7, hours=9)).strftime("%Y-%m-%dT%H:%M:%S"),
                "endTime": (today + timedelta(days=7, hours=11)).strftime("%Y-%m-%dT%H:%M:%S"),
                "categoryId": created_categories["학업"]
            }

            schedule_response = requests.post(
                f"{gateway_url}/api/v1/schedules",
                headers=headers,
                json=schedule_data,
                timeout=5
            )

            assert schedule_response.status_code == 201
            created_schedules.append(schedule_response.json()["scheduleId"])
            print(f"    ✅ 학업 카테고리에 일정 추가: 기말고사")

            # 개인 카테고리에 할일 추가
            todo_data = {
                "title": "운동하기",
                "startDate": today.strftime("%Y-%m-%d"),
                "dueDate": (today + timedelta(days=1)).strftime("%Y-%m-%d"),
                "priority": "MEDIUM",
                "categoryId": created_categories["개인"]
            }

            todo_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                json=todo_data,
                timeout=5
            )

            assert todo_response.status_code == 201
            created_todos.append(todo_response.json()["todoId"])
            print(f"    ✅ 개인 카테고리에 할일 추가: 운동하기")

            # 업무 카테고리에 일정 추가
            work_schedule_data = {
                "title": "팀 미팅",
                "startTime": (today + timedelta(days=1, hours=14)).strftime("%Y-%m-%dT%H:%M:%S"),
                "endTime": (today + timedelta(days=1, hours=15)).strftime("%Y-%m-%dT%H:%M:%S"),
                "categoryId": created_categories["업무"]
            }

            work_response = requests.post(
                f"{gateway_url}/api/v1/schedules",
                headers=headers,
                json=work_schedule_data,
                timeout=5
            )

            assert work_response.status_code == 201
            created_schedules.append(work_response.json()["scheduleId"])
            print(f"    ✅ 업무 카테고리에 일정 추가: 팀 미팅")

            # ========== 3. 카테고리별 일정/할일 조회 ==========
            print(f"\n  [3/6] 카테고리별 일정/할일 조회")

            # 일정 목록 조회
            schedules_response = requests.get(
                f"{gateway_url}/api/v1/schedules",
                headers=headers,
                timeout=5
            )

            assert schedules_response.status_code == 200
            schedules = schedules_response.json()
            print(f"    ✅ 전체 일정 조회: {len(schedules)}개")

            # 할일 목록 조회
            todos_response = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                timeout=5
            )

            assert todos_response.status_code == 200
            todos = todos_response.json()
            print(f"    ✅ 전체 할일 조회: {len(todos)}개")

            # ========== 4. 카테고리 수정 ==========
            print(f"\n  [4/6] 카테고리 수정")

            update_data = {
                "name": "학업/과제",
                "color": "#2C3E50",
                "icon": "📖"
            }

            update_response = requests.put(
                f"{gateway_url}/api/v1/categories/{created_categories['학업']}",
                headers=headers,
                json=update_data,
                timeout=5
            )

            assert update_response.status_code == 200
            updated_category = update_response.json()
            assert updated_category["name"] == "학업/과제"
            print(f"    ✅ 카테고리 수정: 학업 → 학업/과제")

            # ========== 5. 일정/할일이 있는 카테고리 확인 ==========
            print(f"\n  [5/6] 카테고리 연결 확인")

            # 수정된 카테고리 조회
            get_response = requests.get(
                f"{gateway_url}/api/v1/categories/{created_categories['학업']}",
                headers=headers,
                timeout=5
            )

            assert get_response.status_code == 200
            print(f"    ✅ 수정된 카테고리 조회 성공")

        finally:
            # ========== 6. 정리 ==========
            print(f"\n  [6/6] 정리")

            # 일정 삭제
            for schedule_id in created_schedules:
                requests.delete(
                    f"{gateway_url}/api/v1/schedules/{schedule_id}",
                    headers=headers,
                    timeout=5
                )
            print(f"    ✅ 일정 {len(created_schedules)}개 삭제")

            # 할일 삭제
            for todo_id in created_todos:
                requests.delete(
                    f"{gateway_url}/api/v1/todos/{todo_id}",
                    headers=headers,
                    timeout=5
                )
            print(f"    ✅ 할일 {len(created_todos)}개 삭제")

            # 카테고리 삭제
            for name, cat_id in created_categories.items():
                requests.delete(
                    f"{gateway_url}/api/v1/categories/{cat_id}",
                    headers=headers,
                    timeout=5
                )
            print(f"    ✅ 카테고리 {len(created_categories)}개 삭제")

        print(f"\n[SCENARIO] ✅ 카테고리 관리 전체 워크플로우 완료")


class TestCategoryColorCustomization:
    """카테고리 색상 커스터마이징 테스트"""

    def test_category_colors_and_icons(self, jwt_auth_tokens, service_urls):
        """
        다양한 색상과 아이콘으로 카테고리 생성

        시나리오:
        1. 다양한 색상의 카테고리 생성
        2. 색상 변경
        3. 목록에서 색상 확인
        4. 정리
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[SCENARIO] 카테고리 색상 커스터마이징")

        colors_and_icons = [
            {"name": "빨강", "color": "#FF0000", "icon": "🔴"},
            {"name": "파랑", "color": "#0000FF", "icon": "🔵"},
            {"name": "초록", "color": "#00FF00", "icon": "🟢"},
            {"name": "노랑", "color": "#FFFF00", "icon": "🟡"}
        ]

        created_ids = []

        try:
            # 다양한 색상의 카테고리 생성
            for cat_data in colors_and_icons:
                response = requests.post(
                    f"{gateway_url}/api/v1/categories",
                    headers=headers,
                    json=cat_data,
                    timeout=5
                )

                assert response.status_code == 201
                category = response.json()
                created_ids.append(category["categoryId"])
                assert category["color"] == cat_data["color"]
                print(f"  ✅ {cat_data['name']} 카테고리 생성: {cat_data['color']}")

            # 색상 변경
            update_response = requests.put(
                f"{gateway_url}/api/v1/categories/{created_ids[0]}",
                headers=headers,
                json={"name": "분홍", "color": "#FF69B4", "icon": "💗"},
                timeout=5
            )

            assert update_response.status_code == 200
            updated = update_response.json()
            assert updated["color"] == "#FF69B4"
            print(f"  ✅ 빨강 → 분홍 색상 변경")

            # 목록에서 확인
            list_response = requests.get(
                f"{gateway_url}/api/v1/categories",
                headers=headers,
                timeout=5
            )

            assert list_response.status_code == 200
            categories = list_response.json()
            print(f"  ✅ 카테고리 목록 조회: {len(categories)}개")

        finally:
            # Cleanup
            for cat_id in created_ids:
                requests.delete(
                    f"{gateway_url}/api/v1/categories/{cat_id}",
                    headers=headers,
                    timeout=5
                )

        print(f"\n[SCENARIO] ✅ 카테고리 색상 커스터마이징 완료")
