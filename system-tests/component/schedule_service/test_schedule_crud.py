"""
Schedule-Service Schedule CRUD 테스트

일정 CRUD 완전 검증
"""

import pytest
import requests
from datetime import datetime, timedelta


class TestScheduleCrud:
    """Schedule CRUD 전체 플로우 테스트"""

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
            "name": "Schedule 테스트 카테고리",
            "color": "#5733FF",
            "icon": "📅"
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

    def test_create_schedule_success(self, jwt_auth_tokens, service_urls, test_category):
        """
        일정 생성 성공

        Given: 유효한 일정 데이터 (카테고리 포함)
        When: POST /api/v1/schedules 호출
        Then: 201 Created, 생성된 일정 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        schedule_data = {
            "categoryId": test_category["categoryId"],
            "title": "테스트 일정",
            "description": "일정 CRUD 테스트",
            "startTime": (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT10:00:00"),
            "endTime": (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT11:00:00"),
            "isAllDay": False
        }

        print(f"\n[TEST] 일정 생성")
        response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert response.status_code == 201, \
            f"일정 생성 실패: {response.status_code} - {response.text}"

        created_schedule = response.json()
        assert created_schedule["title"] == "테스트 일정"
        assert created_schedule["categoryId"] == test_category["categoryId"]
        assert created_schedule["status"] == "TODO"

        print(f"  ✅ 일정 생성 성공: ID={created_schedule['scheduleId']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )

    def test_get_schedule_by_id_success(self, jwt_auth_tokens, service_urls, test_category):
        """
        일정 상세 조회 성공

        Given: 생성된 일정
        When: GET /api/v1/schedules/{scheduleId} 호출
        Then: 200 OK, 일정 상세 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 일정 생성
        schedule_data = {
            "categoryId": test_category["categoryId"],
            "title": "상세 조회 테스트",
            "description": "상세 조회 테스트 설명",
            "startTime": (datetime.now() + timedelta(days=2)).strftime("%Y-%m-%dT14:00:00"),
            "endTime": (datetime.now() + timedelta(days=2)).strftime("%Y-%m-%dT15:00:00"),
            "isAllDay": False
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_schedule = create_response.json()

        # 일정 상세 조회
        print(f"\n[TEST] 일정 상세 조회")
        get_response = requests.get(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )

        assert get_response.status_code == 200
        schedule = get_response.json()
        assert schedule["scheduleId"] == created_schedule["scheduleId"]
        assert schedule["title"] == "상세 조회 테스트"
        assert schedule["description"] == "상세 조회 테스트 설명"

        print(f"  ✅ 일정 상세 조회 성공: {schedule['title']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )

    def test_update_schedule_success(self, jwt_auth_tokens, service_urls, test_category):
        """
        일정 수정 성공

        Given: 기존 일정
        When: PUT /api/v1/schedules/{scheduleId} 호출
        Then: 200 OK, 수정된 일정 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 일정 생성
        schedule_data = {
            "categoryId": test_category["categoryId"],
            "title": "수정 전 일정",
            "description": "수정 전 설명",
            "startTime": (datetime.now() + timedelta(days=3)).strftime("%Y-%m-%dT09:00:00"),
            "endTime": (datetime.now() + timedelta(days=3)).strftime("%Y-%m-%dT10:00:00"),
            "isAllDay": False
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_schedule = create_response.json()

        # 일정 수정
        updated_data = {
            "categoryId": test_category["categoryId"],
            "title": "수정된 일정",
            "description": "수정된 설명",
            "startTime": (datetime.now() + timedelta(days=4)).strftime("%Y-%m-%dT11:00:00"),
            "endTime": (datetime.now() + timedelta(days=4)).strftime("%Y-%m-%dT12:00:00"),
            "isAllDay": False
        }

        print(f"\n[TEST] 일정 수정")
        update_response = requests.put(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            json=updated_data,
            timeout=5
        )

        assert update_response.status_code == 200
        updated_schedule = update_response.json()
        assert updated_schedule["title"] == "수정된 일정"
        assert updated_schedule["description"] == "수정된 설명"

        print(f"  ✅ 일정 수정 성공: {updated_schedule['title']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )

    def test_update_schedule_status(self, jwt_auth_tokens, service_urls, test_category):
        """
        일정 상태 변경

        Given: 기존 일정 (TODO 상태)
        When: PATCH /api/v1/schedules/{scheduleId}/status 호출
        Then: 200 OK, 상태가 변경됨
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 일정 생성
        schedule_data = {
            "categoryId": test_category["categoryId"],
            "title": "상태 변경 테스트",
            "startTime": (datetime.now() + timedelta(days=5)).strftime("%Y-%m-%dT13:00:00"),
            "endTime": (datetime.now() + timedelta(days=5)).strftime("%Y-%m-%dT14:00:00"),
            "isAllDay": False
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_schedule = create_response.json()
        assert created_schedule["status"] == "TODO"

        # 상태 변경: TODO → IN_PROGRESS
        print(f"\n[TEST] 일정 상태 변경 (TODO → IN_PROGRESS)")
        status_response = requests.patch(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}/status",
            headers=headers,
            json={"status": "IN_PROGRESS"},
            timeout=5
        )

        assert status_response.status_code == 200
        updated_schedule = status_response.json()
        assert updated_schedule["status"] == "IN_PROGRESS"
        print(f"  ✅ 상태 변경 성공: TODO → IN_PROGRESS")

        # 상태 변경: IN_PROGRESS → DONE
        status_response = requests.patch(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}/status",
            headers=headers,
            json={"status": "DONE"},
            timeout=5
        )

        assert status_response.status_code == 200
        updated_schedule = status_response.json()
        assert updated_schedule["status"] == "DONE"
        print(f"  ✅ 상태 변경 성공: IN_PROGRESS → DONE")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )

    def test_delete_schedule_success(self, jwt_auth_tokens, service_urls, test_category):
        """
        일정 삭제 성공

        Given: 기존 일정
        When: DELETE /api/v1/schedules/{scheduleId} 호출
        Then: 204 No Content
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 먼저 일정 생성
        schedule_data = {
            "categoryId": test_category["categoryId"],
            "title": "삭제할 일정",
            "startTime": (datetime.now() + timedelta(days=6)).strftime("%Y-%m-%dT15:00:00"),
            "endTime": (datetime.now() + timedelta(days=6)).strftime("%Y-%m-%dT16:00:00"),
            "isAllDay": False
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_schedule = create_response.json()

        # 일정 삭제
        print(f"\n[TEST] 일정 삭제")
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )

        assert delete_response.status_code == 204
        print(f"  ✅ 일정 삭제 성공")

        # 삭제 확인
        get_response = requests.get(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )
        assert get_response.status_code == 404
        print(f"  ✅ 삭제 확인: 404 Not Found")

    def test_get_schedules_list(self, jwt_auth_tokens, service_urls, test_category):
        """
        일정 목록 조회

        Given: 여러 일정 생성
        When: GET /api/v1/schedules 호출
        Then: 200 OK, 일정 배열 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 여러 일정 생성
        created_ids = []
        for i in range(3):
            schedule_data = {
                "categoryId": test_category["categoryId"],
                "title": f"목록 테스트 {i+1}",
                "startTime": (datetime.now() + timedelta(days=7+i)).strftime("%Y-%m-%dT10:00:00"),
                "endTime": (datetime.now() + timedelta(days=7+i)).strftime("%Y-%m-%dT11:00:00"),
                "isAllDay": False
            }

            response = requests.post(
                f"{gateway_url}/api/v1/schedules",
                headers=headers,
                json=schedule_data,
                timeout=5
            )
            if response.status_code == 201:
                created_ids.append(response.json()["scheduleId"])

        # 목록 조회
        print(f"\n[TEST] 일정 목록 조회")
        list_response = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            timeout=5
        )

        assert list_response.status_code == 200
        schedules = list_response.json()
        assert isinstance(schedules, list)

        # 생성한 일정이 목록에 있는지 확인
        returned_ids = {s["scheduleId"] for s in schedules}
        for created_id in created_ids:
            assert created_id in returned_ids

        print(f"  ✅ 일정 목록 조회 성공: {len(schedules)}개")

        # Cleanup
        for schedule_id in created_ids:
            requests.delete(
                f"{gateway_url}/api/v1/schedules/{schedule_id}",
                headers=headers,
                timeout=5
            )

    def test_get_schedules_by_date_range(self, jwt_auth_tokens, service_urls, test_category):
        """
        날짜 범위로 일정 조회

        Given: 특정 날짜 범위에 일정 생성
        When: GET /api/v1/schedules?startDate=...&endDate=... 호출
        Then: 200 OK, 해당 범위의 일정만 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        # 일정 생성 (10일 후)
        target_date = datetime.now() + timedelta(days=10)
        schedule_data = {
            "categoryId": test_category["categoryId"],
            "title": "날짜 범위 테스트",
            "startTime": target_date.strftime("%Y-%m-%dT10:00:00"),
            "endTime": target_date.strftime("%Y-%m-%dT11:00:00"),
            "isAllDay": False
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert create_response.status_code == 201
        created_schedule = create_response.json()

        # 날짜 범위로 조회
        start_date = (target_date - timedelta(days=1)).strftime("%Y-%m-%dT00:00:00")
        end_date = (target_date + timedelta(days=1)).strftime("%Y-%m-%dT23:59:59")

        print(f"\n[TEST] 날짜 범위로 일정 조회")
        list_response = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            params={"startDate": start_date, "endDate": end_date},
            timeout=5
        )

        assert list_response.status_code == 200
        schedules = list_response.json()

        # 생성한 일정이 결과에 포함되어야 함
        found = any(s["scheduleId"] == created_schedule["scheduleId"] for s in schedules)
        assert found, "날짜 범위 내 일정이 조회되지 않음"

        print(f"  ✅ 날짜 범위 조회 성공: {len(schedules)}개")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )

    def test_create_all_day_schedule(self, jwt_auth_tokens, service_urls, test_category):
        """
        종일 일정 생성

        Given: isAllDay=true인 일정 데이터
        When: POST /api/v1/schedules 호출
        Then: 201 Created, isAllDay=true인 일정 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        schedule_data = {
            "categoryId": test_category["categoryId"],
            "title": "종일 일정",
            "description": "종일 이벤트 테스트",
            "startTime": (datetime.now() + timedelta(days=11)).strftime("%Y-%m-%dT00:00:00"),
            "endTime": (datetime.now() + timedelta(days=11)).strftime("%Y-%m-%dT23:59:59"),
            "isAllDay": True
        }

        print(f"\n[TEST] 종일 일정 생성")
        response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=schedule_data,
            timeout=5
        )

        assert response.status_code == 201
        created_schedule = response.json()
        assert created_schedule["title"] == "종일 일정"
        assert created_schedule["isAllDay"] is True

        print(f"  ✅ 종일 일정 생성 성공: isAllDay={created_schedule['isAllDay']}")

        # Cleanup
        requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule['scheduleId']}",
            headers=headers,
            timeout=5
        )
