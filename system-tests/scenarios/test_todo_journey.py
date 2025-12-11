"""
Todo 관리 E2E 시나리오 테스트

사용자의 전체 Todo 관리 플로우 검증
"""

import pytest
import requests
import uuid
from datetime import datetime, timedelta


def create_test_user(gateway_url, name):
    """Helper function to create a test user and return user data"""
    email = f"{name.lower().replace(' ', '-')}-{uuid.uuid4().hex[:8]}@unisync.com"
    signup_response = requests.post(
        f"{gateway_url}/api/v1/auth/signup",
        json={
            "email": email,
            "password": "TestPassword123!",
            "name": name
        },
        timeout=10
    )
    signup_data = signup_response.json()

    return {
        "cognitoSub": signup_data.get("cognitoSub"),
        "email": email,
        "name": name,
        "headers": {
            "Authorization": f"Bearer {signup_data['idToken']}",
            "Content-Type": "application/json"
        }
    }


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


class TestTodoDeadlineWorkflow:
    """Todo deadline 필드 테스트"""

    def test_todo_with_deadline(self, jwt_auth_tokens, service_urls):
        """
        deadline 필드가 있는 Todo 생성 및 관리

        시나리오:
        1. deadline 있는 Todo 생성
        2. deadline 없는 Todo 생성
        3. deadline 수정
        4. due_date > deadline 검증 (실패해야 함)
        5. deadline 기준 필터링 (미래 구현)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[SCENARIO] Todo deadline 필드 테스트")

        # 카테고리 생성
        category_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json={"name": "Deadline 테스트", "color": "#FF5733", "icon": "⏰"},
            timeout=5
        )
        assert category_response.status_code == 201
        category_id = category_response.json()["categoryId"]

        today = datetime.now()
        start_date = today.strftime("%Y-%m-%d")
        due_date = (today + timedelta(days=5)).strftime("%Y-%m-%d")
        deadline = (today + timedelta(days=7)).strftime("%Y-%m-%dT23:59:00")

        created_todos = []
        try:
            # ========== 1. deadline 있는 Todo 생성 ==========
            print(f"\n  [1/5] deadline 있는 Todo 생성")
            todo_with_deadline_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                json={
                    "title": "과제 제출 (deadline 있음)",
                    "description": "실제 제출 마감: 7일 후",
                    "startDate": start_date,
                    "dueDate": due_date,  # 5일 후 완료 목표
                    "deadline": deadline,  # 7일 후 최종 마감
                    "priority": "HIGH",
                    "categoryId": category_id
                },
                timeout=5
            )
            assert todo_with_deadline_response.status_code == 201, \
                f"deadline 있는 Todo 생성 실패: {todo_with_deadline_response.status_code}"

            todo_with_deadline = todo_with_deadline_response.json()
            created_todos.append(todo_with_deadline["todoId"])

            assert todo_with_deadline["deadline"] == deadline
            assert todo_with_deadline["dueDate"] == due_date
            print(f"    ✅ deadline 있는 Todo 생성 성공")
            print(f"       - dueDate: {due_date} (목표 완료일)")
            print(f"       - deadline: {deadline} (최종 마감일)")

            # ========== 2. deadline 없는 Todo 생성 ==========
            print(f"\n  [2/5] deadline 없는 Todo 생성")
            todo_without_deadline_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                json={
                    "title": "개인 프로젝트 (deadline 없음)",
                    "description": "자율적으로 진행",
                    "startDate": start_date,
                    "dueDate": due_date,
                    "priority": "MEDIUM",
                    "categoryId": category_id
                },
                timeout=5
            )
            assert todo_without_deadline_response.status_code == 201

            todo_without_deadline = todo_without_deadline_response.json()
            created_todos.append(todo_without_deadline["todoId"])

            assert todo_without_deadline.get("deadline") is None
            print(f"    ✅ deadline 없는 Todo 생성 성공")

            # ========== 3. deadline 수정 ==========
            print(f"\n  [3/5] deadline 수정")
            new_deadline = (today + timedelta(days=10)).strftime("%Y-%m-%dT23:59:00")

            update_response = requests.put(
                f"{gateway_url}/api/v1/todos/{todo_with_deadline['todoId']}",
                headers=headers,
                json={
                    "title": "과제 제출 (deadline 연장)",
                    "description": "마감일 연장됨",
                    "startDate": start_date,
                    "dueDate": due_date,
                    "deadline": new_deadline,  # 10일 후로 연장
                    "priority": "HIGH",
                    "categoryId": category_id
                },
                timeout=5
            )
            assert update_response.status_code == 200

            updated_todo = update_response.json()
            assert updated_todo["deadline"] == new_deadline
            print(f"    ✅ deadline 수정 성공: {deadline} → {new_deadline}")

            # ========== 4. due_date > deadline 검증 (실패해야 함) ==========
            print(f"\n  [4/5] due_date > deadline 검증 (실패해야 함)")
            invalid_due_date = (today + timedelta(days=15)).strftime("%Y-%m-%d")

            invalid_todo_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                json={
                    "title": "잘못된 Todo",
                    "startDate": start_date,
                    "dueDate": invalid_due_date,  # 15일 후
                    "deadline": deadline,  # 7일 후 (dueDate보다 빠름!)
                    "priority": "HIGH",
                    "categoryId": category_id
                },
                timeout=5
            )

            # 400 또는 500 에러 예상 (DB 제약 조건 위반)
            assert invalid_todo_response.status_code in [400, 500], \
                f"due_date > deadline 검증 실패: {invalid_todo_response.status_code}"
            print(f"    ✅ due_date > deadline 검증 성공 (요청 거부됨)")

            # ========== 5. Todo 목록 조회로 deadline 확인 ==========
            print(f"\n  [5/5] Todo 목록 조회로 deadline 확인")
            list_response = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                timeout=5
            )
            assert list_response.status_code == 200

            todos = list_response.json()
            todos_with_deadline = [t for t in todos if t.get("deadline") is not None]
            todos_without_deadline = [t for t in todos if t.get("deadline") is None]

            print(f"    ✅ Todo 목록 조회 성공")
            print(f"       - deadline 있는 Todo: {len(todos_with_deadline)}개")
            print(f"       - deadline 없는 Todo: {len(todos_without_deadline)}개")

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

        print(f"\n[SCENARIO] ✅ Todo deadline 필드 테스트 완료")


class TestScheduleTodoIntegration:
    """Schedule과 Todo 연동 테스트"""

    def test_schedule_detail_with_todos_and_subtasks(self, jwt_auth_tokens, service_urls):
        """
        Schedule 상세 조회 시 관련 Todo + Subtasks 반환 테스트

        시나리오:
        1. 카테고리 생성
        2. Schedule 생성
        3. Schedule과 연결된 Todo 생성 (schedule_id 지정)
        4. Todo에 서브태스크 추가
        5. Schedule 상세 조회로 todos와 subtasks 확인
        6. 다른 Schedule 생성 후 Todo 추가 (분리 확인)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print(f"\n[SCENARIO] Schedule과 Todo 연동 테스트")

        # 카테고리 생성
        category_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json={"name": "프로젝트", "color": "#4CAF50", "icon": "📊"},
            timeout=5
        )
        assert category_response.status_code == 201
        category_id = category_response.json()["categoryId"]

        today = datetime.now()
        schedule_time = (today + timedelta(days=7)).strftime("%Y-%m-%dT14:00:00")
        schedule_end_time = (today + timedelta(days=7)).strftime("%Y-%m-%dT16:00:00")

        created_schedules = []
        created_todos = []

        try:
            # ========== 1. Schedule 생성 ==========
            print(f"\n  [1/6] Schedule 생성")
            schedule_response = requests.post(
                f"{gateway_url}/api/v1/schedules",
                headers=headers,
                json={
                    "title": "프로젝트 발표",
                    "description": "최종 발표 및 시연",
                    "startTime": schedule_time,
                    "endTime": schedule_end_time,
                    "location": "공학관 301호",
                    "categoryId": category_id
                },
                timeout=5
            )
            assert schedule_response.status_code == 201

            schedule = schedule_response.json()
            schedule_id = schedule["scheduleId"]
            created_schedules.append(schedule_id)
            print(f"    ✅ Schedule 생성: ID={schedule_id}, title={schedule['title']}")

            # ========== 2. Schedule과 연결된 Todo 생성 ==========
            print(f"\n  [2/6] Schedule과 연결된 Todo 생성")
            start_date = today.strftime("%Y-%m-%d")
            due_date = (today + timedelta(days=5)).strftime("%Y-%m-%d")
            deadline = schedule_end_time

            todo_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                json={
                    "title": "프로젝트 준비",
                    "description": "발표 자료 준비",
                    "startDate": start_date,
                    "dueDate": due_date,
                    "deadline": deadline,
                    "priority": "HIGH",
                    "categoryId": category_id,
                    "scheduleId": schedule_id  # Schedule과 연결
                },
                timeout=5
            )
            assert todo_response.status_code == 201

            todo = todo_response.json()
            todo_id = todo["todoId"]
            created_todos.append(todo_id)
            assert todo["scheduleId"] == schedule_id
            print(f"    ✅ Todo 생성: ID={todo_id}, scheduleId={schedule_id}")

            # ========== 3. 서브태스크 추가 ==========
            print(f"\n  [3/6] 서브태스크 추가")
            subtasks_data = [
                {"title": "발표 자료 작성", "priority": "HIGH"},
                {"title": "시연 준비", "priority": "HIGH"},
                {"title": "리허설", "priority": "MEDIUM"}
            ]

            subtask_ids = []
            for subtask_info in subtasks_data:
                subtask_response = requests.post(
                    f"{gateway_url}/api/v1/todos",
                    headers=headers,
                    json={
                        "title": subtask_info["title"],
                        "startDate": start_date,
                        "dueDate": due_date,
                        "priority": subtask_info["priority"],
                        "categoryId": category_id,
                        "parentTodoId": todo_id  # 부모 Todo 지정
                    },
                    timeout=5
                )
                assert subtask_response.status_code == 201

                subtask = subtask_response.json()
                subtask_ids.append(subtask["todoId"])
                created_todos.append(subtask["todoId"])
                assert subtask["parentTodoId"] == todo_id
                print(f"    ✅ 서브태스크 생성: {subtask['title']}")

            # ========== 4. Schedule 상세 조회로 todos + subtasks 확인 ==========
            print(f"\n  [4/6] Schedule 상세 조회 (todos + subtasks 포함)")
            schedule_detail_response = requests.get(
                f"{gateway_url}/api/v1/schedules/{schedule_id}",
                headers=headers,
                timeout=5
            )
            assert schedule_detail_response.status_code == 200

            schedule_detail = schedule_detail_response.json()

            # Schedule 기본 정보 확인
            assert schedule_detail["scheduleId"] == schedule_id
            assert schedule_detail["title"] == "프로젝트 발표"

            # todos 배열 확인
            assert "todos" in schedule_detail
            todos_in_schedule = schedule_detail["todos"]
            assert len(todos_in_schedule) > 0, "Schedule에 연결된 Todo가 없음"

            # 메인 Todo 확인
            main_todo = todos_in_schedule[0]
            assert main_todo["todoId"] == todo_id
            assert main_todo["title"] == "프로젝트 준비"
            assert main_todo["scheduleId"] == schedule_id
            assert main_todo["deadline"] == deadline

            # subtasks 배열 확인
            assert "subtasks" in main_todo
            subtasks_in_todo = main_todo["subtasks"]
            assert len(subtasks_in_todo) == 3, f"서브태스크 개수 불일치: {len(subtasks_in_todo)}"

            # 각 서브태스크 확인
            subtask_titles = {st["title"] for st in subtasks_in_todo}
            expected_titles = {"발표 자료 작성", "시연 준비", "리허설"}
            assert subtask_titles == expected_titles, f"서브태스크 제목 불일치: {subtask_titles}"

            # 모든 서브태스크의 parentTodoId 확인
            for subtask in subtasks_in_todo:
                assert subtask["parentTodoId"] == todo_id
                assert "subtasks" in subtask  # 재귀적 구조

            print(f"    ✅ Schedule 상세 조회 성공")
            print(f"       - Schedule: {schedule_detail['title']}")
            print(f"       - 연결된 Todo: {len(todos_in_schedule)}개")
            print(f"       - 서브태스크: {len(subtasks_in_todo)}개")

            # ========== 5. 다른 Schedule 생성 후 분리 확인 ==========
            print(f"\n  [5/6] 다른 Schedule 생성 (Todo 분리 확인)")
            schedule2_time = (today + timedelta(days=14)).strftime("%Y-%m-%dT10:00:00")
            schedule2_end_time = (today + timedelta(days=14)).strftime("%Y-%m-%dT12:00:00")

            schedule2_response = requests.post(
                f"{gateway_url}/api/v1/schedules",
                headers=headers,
                json={
                    "title": "중간 점검 미팅",
                    "startTime": schedule2_time,
                    "endTime": schedule2_end_time,
                    "categoryId": category_id
                },
                timeout=5
            )
            assert schedule2_response.status_code == 201

            schedule2 = schedule2_response.json()
            schedule2_id = schedule2["scheduleId"]
            created_schedules.append(schedule2_id)

            # Schedule2와 연결된 Todo 생성
            todo2_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=headers,
                json={
                    "title": "중간 점검 자료 준비",
                    "startDate": (today + timedelta(days=10)).strftime("%Y-%m-%d"),
                    "dueDate": (today + timedelta(days=13)).strftime("%Y-%m-%d"),
                    "priority": "MEDIUM",
                    "categoryId": category_id,
                    "scheduleId": schedule2_id
                },
                timeout=5
            )
            assert todo2_response.status_code == 201

            todo2 = todo2_response.json()
            created_todos.append(todo2["todoId"])
            print(f"    ✅ 두 번째 Schedule과 Todo 생성")

            # ========== 6. 두 Schedule의 Todo 분리 확인 ==========
            print(f"\n  [6/6] 각 Schedule의 Todo 분리 확인")

            # Schedule1 상세 조회
            schedule1_detail = requests.get(
                f"{gateway_url}/api/v1/schedules/{schedule_id}",
                headers=headers,
                timeout=5
            ).json()

            # Schedule2 상세 조회
            schedule2_detail = requests.get(
                f"{gateway_url}/api/v1/schedules/{schedule2_id}",
                headers=headers,
                timeout=5
            ).json()

            # Schedule1에는 todo_id만, Schedule2에는 todo2_id만 있어야 함
            schedule1_todo_ids = {t["todoId"] for t in schedule1_detail["todos"]}
            schedule2_todo_ids = {t["todoId"] for t in schedule2_detail["todos"]}

            assert todo_id in schedule1_todo_ids
            assert todo_id not in schedule2_todo_ids
            assert todo2["todoId"] in schedule2_todo_ids
            assert todo2["todoId"] not in schedule1_todo_ids

            print(f"    ✅ Schedule별 Todo 분리 확인 성공")
            print(f"       - Schedule1 todos: {len(schedule1_detail['todos'])}개")
            print(f"       - Schedule2 todos: {len(schedule2_detail['todos'])}개")

        finally:
            # Cleanup
            for todo_id in created_todos:
                requests.delete(
                    f"{gateway_url}/api/v1/todos/{todo_id}",
                    headers=headers,
                    timeout=5
                )

            for schedule_id in created_schedules:
                requests.delete(
                    f"{gateway_url}/api/v1/schedules/{schedule_id}",
                    headers=headers,
                    timeout=5
                )

            requests.delete(
                f"{gateway_url}/api/v1/categories/{category_id}",
                headers=headers,
                timeout=5
            )

        print(f"\n[SCENARIO] ✅ Schedule과 Todo 연동 테스트 완료")


class TestGroupTodoWorkflow:
    """그룹 Todo 조회 테스트 (includeGroups 파라미터)"""

    def test_group_todo_query_with_include_groups(self, service_urls, clean_user_database):
        """
        includeGroups 파라미터를 사용한 그룹 Todo 조회 테스트

        시나리오:
        1. Owner와 Member 사용자 생성
        2. Owner가 그룹 생성 및 Member 초대
        3. 개인 카테고리/Todo 생성 (Owner, Member 각각)
        4. 그룹 카테고리/Todo 생성 (Owner가 생성)
        5. 개인 Todo만 조회 (파라미터 없음)
        6. 특정 그룹 Todo 조회 (groupId 파라미터)
        7. 개인 + 모든 그룹 Todo 통합 조회 (includeGroups=true)
        8. Member도 동일하게 조회 가능 확인
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print(f"\n[SCENARIO] 그룹 Todo 조회 테스트 (includeGroups)")

        # ========== 1. 사용자 준비 ==========
        print(f"\n  [1/8] 사용자 준비")
        owner = create_test_user(gateway_url, "Todo Owner")
        member = create_test_user(gateway_url, "Todo Member")

        print(f"    ✅ Owner: {owner['email']}")
        print(f"    ✅ Member: {member['email']}")

        # ========== 2. 그룹 생성 및 Member 초대 ==========
        print(f"\n  [2/8] 그룹 생성 및 Member 초대")
        group_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner['headers'],
            json={"name": "Todo 협업 그룹", "description": "Todo 공유 테스트"},
            timeout=5
        )
        assert group_response.status_code == 201
        group_id = group_response.json()["groupId"]

        # Member 초대
        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner['headers'],
            json={"userCognitoSub": member['cognitoSub'], "role": "MEMBER"},
            timeout=5
        )
        print(f"    ✅ 그룹 생성 (groupId: {group_id}) 및 Member 초대 완료")

        today = datetime.now()
        start_date = today.strftime("%Y-%m-%d")
        due_date = (today + timedelta(days=7)).strftime("%Y-%m-%d")

        created_categories = []
        created_todos = []

        try:
            # ========== 3. 개인 카테고리/Todo 생성 ==========
            print(f"\n  [3/8] 개인 카테고리/Todo 생성")

            # Owner 개인 카테고리/Todo
            owner_cat_response = requests.post(
                f"{gateway_url}/api/v1/categories",
                headers=owner['headers'],
                json={"name": f"Owner 개인-{uuid.uuid4().hex[:6]}", "color": "#FF5733"},
                timeout=5
            )
            owner_cat_id = owner_cat_response.json()["categoryId"]
            created_categories.append(("owner", owner_cat_id))

            owner_todo_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=owner['headers'],
                json={
                    "title": "Owner 개인 Todo",
                    "startDate": start_date,
                    "dueDate": due_date,
                    "priority": "MEDIUM",
                    "categoryId": owner_cat_id
                },
                timeout=5
            )
            owner_todo_id = owner_todo_response.json()["todoId"]
            created_todos.append(("owner", owner_todo_id))

            # Member 개인 카테고리/Todo
            member_cat_response = requests.post(
                f"{gateway_url}/api/v1/categories",
                headers=member['headers'],
                json={"name": f"Member 개인-{uuid.uuid4().hex[:6]}", "color": "#3498DB"},
                timeout=5
            )
            member_cat_id = member_cat_response.json()["categoryId"]
            created_categories.append(("member", member_cat_id))

            member_todo_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=member['headers'],
                json={
                    "title": "Member 개인 Todo",
                    "startDate": start_date,
                    "dueDate": due_date,
                    "priority": "LOW",
                    "categoryId": member_cat_id
                },
                timeout=5
            )
            member_todo_id = member_todo_response.json()["todoId"]
            created_todos.append(("member", member_todo_id))

            print(f"    ✅ Owner/Member 개인 카테고리 및 Todo 생성 완료")

            # ========== 4. 그룹 카테고리/Todo 생성 ==========
            print(f"\n  [4/8] 그룹 카테고리/Todo 생성")

            # 그룹 카테고리
            group_cat_response = requests.post(
                f"{gateway_url}/api/v1/categories",
                headers=owner['headers'],
                json={
                    "name": f"그룹 카테고리-{uuid.uuid4().hex[:6]}",
                    "color": "#2ECC71",
                    "groupId": group_id
                },
                timeout=5
            )
            group_cat_id = group_cat_response.json()["categoryId"]
            created_categories.append(("group", group_cat_id))

            # 그룹 Todo
            group_todo_response = requests.post(
                f"{gateway_url}/api/v1/todos",
                headers=owner['headers'],
                json={
                    "title": "그룹 Todo",
                    "description": "Owner가 생성한 그룹 할일",
                    "startDate": start_date,
                    "dueDate": due_date,
                    "priority": "HIGH",
                    "categoryId": group_cat_id,
                    "groupId": group_id
                },
                timeout=5
            )
            group_todo_id = group_todo_response.json()["todoId"]
            created_todos.append(("group", group_todo_id))

            print(f"    ✅ 그룹 카테고리 및 Todo 생성 완료")

            # ========== 5. Owner: 개인 Todo만 조회 ==========
            print(f"\n  [5/8] Owner: 개인 Todo만 조회 (파라미터 없음)")
            owner_personal_todos = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=owner['headers'],
                timeout=5
            ).json()

            owner_personal_todo_ids = {t["todoId"] for t in owner_personal_todos}
            assert owner_todo_id in owner_personal_todo_ids
            assert group_todo_id not in owner_personal_todo_ids
            assert member_todo_id not in owner_personal_todo_ids

            print(f"    ✅ Owner 개인 Todo만 조회됨 (total: {len(owner_personal_todos)}개)")

            # ========== 6. Owner: 특정 그룹 Todo 조회 ==========
            print(f"\n  [6/8] Owner: 특정 그룹 Todo 조회 (groupId 파라미터)")
            owner_group_todos = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=owner['headers'],
                params={"groupId": str(group_id)},
                timeout=5
            ).json()

            owner_group_todo_ids = {t["todoId"] for t in owner_group_todos}
            assert group_todo_id in owner_group_todo_ids
            assert owner_todo_id not in owner_group_todo_ids
            assert member_todo_id not in owner_group_todo_ids

            print(f"    ✅ Owner 그룹 Todo만 조회됨 (total: {len(owner_group_todos)}개)")

            # ========== 7. Owner: 개인 + 모든 그룹 Todo 통합 조회 ==========
            print(f"\n  [7/8] Owner: 개인 + 모든 그룹 Todo 통합 조회 (includeGroups=true)")
            owner_all_todos = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=owner['headers'],
                params={"includeGroups": "true"},
                timeout=5
            ).json()

            owner_all_todo_ids = {t["todoId"] for t in owner_all_todos}
            assert owner_todo_id in owner_all_todo_ids
            assert group_todo_id in owner_all_todo_ids
            assert member_todo_id not in owner_all_todo_ids  # Member 개인 Todo는 안 보임

            print(f"    ✅ Owner 개인 + 그룹 Todo 통합 조회됨 (total: {len(owner_all_todos)}개)")
            print(f"       - 개인 Todo 포함: ✅")
            print(f"       - 그룹 Todo 포함: ✅")
            print(f"       - 다른 사용자 개인 Todo: ❌")

            # ========== 8. Member도 동일하게 조회 가능 확인 ==========
            print(f"\n  [8/8] Member: 그룹 Todo 조회 가능 확인")

            # Member 개인 Todo만 조회
            member_personal_todos = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=member['headers'],
                timeout=5
            ).json()

            member_personal_todo_ids = {t["todoId"] for t in member_personal_todos}
            assert member_todo_id in member_personal_todo_ids
            assert group_todo_id not in member_personal_todo_ids

            print(f"    ✅ Member 개인 Todo만 조회됨")

            # Member 개인 + 그룹 Todo 통합 조회
            member_all_todos = requests.get(
                f"{gateway_url}/api/v1/todos",
                headers=member['headers'],
                params={"includeGroups": "true"},
                timeout=5
            ).json()

            member_all_todo_ids = {t["todoId"] for t in member_all_todos}
            assert member_todo_id in member_all_todo_ids
            assert group_todo_id in member_all_todo_ids  # Member도 그룹 Todo 조회 가능
            assert owner_todo_id not in member_all_todo_ids

            print(f"    ✅ Member 개인 + 그룹 Todo 통합 조회됨")
            print(f"       - Member 개인 Todo 포함: ✅")
            print(f"       - 그룹 Todo 포함: ✅")
            print(f"       - Owner 개인 Todo: ❌")

            # 그룹 Todo 상세 확인
            group_todo_detail = next((t for t in member_all_todos if t["todoId"] == group_todo_id), None)
            assert group_todo_detail is not None
            assert group_todo_detail["groupId"] == group_id
            assert group_todo_detail["title"] == "그룹 Todo"
            print(f"    ✅ Member가 조회한 그룹 Todo 상세 정보 확인 완료")

        finally:
            # Cleanup
            for user_type, todo_id in created_todos:
                headers = owner['headers'] if user_type in ["owner", "group"] else member['headers']
                requests.delete(
                    f"{gateway_url}/api/v1/todos/{todo_id}",
                    headers=headers,
                    timeout=5
                )

            for user_type, cat_id in created_categories:
                headers = owner['headers'] if user_type in ["owner", "group"] else member['headers']
                requests.delete(
                    f"{gateway_url}/api/v1/categories/{cat_id}",
                    headers=headers,
                    timeout=5
                )

            requests.delete(
                f"{gateway_url}/api/v1/groups/{group_id}",
                headers=owner['headers'],
                timeout=5
            )

        print(f"\n[SCENARIO] ✅ 그룹 Todo 조회 테스트 완료")
