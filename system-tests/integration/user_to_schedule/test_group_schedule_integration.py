"""
User-Service → Schedule-Service 통합 테스트

그룹 삭제 시 Schedule-Service의 그룹 데이터 삭제 cascade 검증
Internal API 호출 검증
"""

import pytest
import requests
import time
import uuid


class TestGroupScheduleIntegration:
    """User-Service → Schedule-Service 그룹 연동 테스트"""

    @pytest.fixture(autouse=True)
    def setup(self, user_service_url, schedule_service_url, jwt_auth_tokens):
        """테스트 설정"""
        self.user_service_url = user_service_url
        self.schedule_service_url = schedule_service_url
        self.tokens = jwt_auth_tokens

    # =========================================================================
    # Internal API 테스트
    # =========================================================================

    def test_internal_membership_check_returns_member_info(
        self, user_service_url, schedule_service_url, jwt_auth_tokens
    ):
        """Internal API: 멤버십 조회가 정상 동작하는지 검증"""
        # Arrange: 그룹 생성 (X-Cognito-Sub 헤더 수동 추가 - API Gateway 역할)
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['user1']}",
            "X-Cognito-Sub": jwt_auth_tokens['user1_sub']
        }
        group_data = {
            "name": f"Test Group {uuid.uuid4().hex[:8]}",
            "description": "Integration test group"
        }

        # Act: 그룹 생성
        create_resp = requests.post(
            f"{user_service_url}/v1/groups",
            json=group_data,
            headers=headers
        )
        assert create_resp.status_code == 201, f"그룹 생성 실패: {create_resp.text}"
        group_id = create_resp.json()["groupId"]

        try:
            # Act: Internal API로 멤버십 조회 (Schedule-Service가 호출하는 API)
            # Internal API는 인증 불필요 (서비스 간 내부 통신)
            membership_resp = requests.get(
                f"{user_service_url}/api/internal/groups/{group_id}/members/{jwt_auth_tokens['user1_sub']}"
            )

            # Assert
            assert membership_resp.status_code == 200
            membership = membership_resp.json()
            assert membership["groupId"] == group_id
            assert membership["member"] is True  # Java boolean isMember -> JSON "member"
            assert membership["role"] == "OWNER"

        finally:
            # Cleanup
            requests.delete(f"{user_service_url}/v1/groups/{group_id}", headers=headers)

    def test_internal_membership_check_returns_not_member(
        self, user_service_url, jwt_auth_tokens
    ):
        """Internal API: 멤버가 아닌 경우 isMember=false 반환"""
        # Arrange: 그룹 생성 (user1)
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['user1']}",
            "X-Cognito-Sub": jwt_auth_tokens['user1_sub']
        }
        group_data = {
            "name": f"Test Group {uuid.uuid4().hex[:8]}",
            "description": "Integration test group"
        }

        create_resp = requests.post(
            f"{user_service_url}/v1/groups",
            json=group_data,
            headers=headers
        )
        assert create_resp.status_code == 201
        group_id = create_resp.json()["groupId"]

        try:
            # Act: user2로 멤버십 조회 (멤버 아님) - Internal API는 인증 불필요
            membership_resp = requests.get(
                f"{user_service_url}/api/internal/groups/{group_id}/members/{jwt_auth_tokens['user2_sub']}"
            )

            # Assert
            assert membership_resp.status_code == 200
            membership = membership_resp.json()
            assert membership["member"] is False  # Java boolean isMember -> JSON "member"
            assert membership["role"] is None

        finally:
            # Cleanup
            requests.delete(f"{user_service_url}/v1/groups/{group_id}", headers=headers)

    # =========================================================================
    # 그룹 삭제 Cascade 테스트
    # =========================================================================

    def test_group_delete_cascades_to_schedule_data(
        self, user_service_url, schedule_service_url, jwt_auth_tokens, schedule_db_connection
    ):
        """그룹 삭제 시 Schedule-Service의 그룹 데이터도 삭제되는지 검증"""
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['user1']}",
            "X-Cognito-Sub": jwt_auth_tokens['user1_sub']
        }

        # Step 1: 그룹 생성
        group_data = {
            "name": f"Cascade Test Group {uuid.uuid4().hex[:8]}",
            "description": "Group for cascade delete test"
        }
        create_resp = requests.post(
            f"{user_service_url}/v1/groups",
            json=group_data,
            headers=headers
        )
        assert create_resp.status_code == 201
        group_id = create_resp.json()["groupId"]

        # Step 2: 그룹 카테고리 생성
        category_data = {
            "name": f"Group Category {uuid.uuid4().hex[:8]}",
            "color": "#FF5733",
            "groupId": group_id
        }
        cat_resp = requests.post(
            f"{schedule_service_url}/v1/categories",
            json=category_data,
            headers=headers
        )
        assert cat_resp.status_code == 201, f"카테고리 생성 실패: {cat_resp.text}"
        category_id = cat_resp.json()["categoryId"]

        # Step 3: 그룹 일정 생성
        schedule_data = {
            "title": f"Group Schedule {uuid.uuid4().hex[:8]}",
            "categoryId": category_id,
            "groupId": group_id,
            "startTime": "2025-12-01T09:00:00",
            "endTime": "2025-12-01T10:00:00"
        }
        schedule_resp = requests.post(
            f"{schedule_service_url}/v1/schedules",
            json=schedule_data,
            headers=headers
        )
        assert schedule_resp.status_code == 201, f"일정 생성 실패: {schedule_resp.text}"
        schedule_id = schedule_resp.json()["scheduleId"]

        # Step 4: 그룹 할일 생성
        todo_data = {
            "title": f"Group Todo {uuid.uuid4().hex[:8]}",
            "categoryId": category_id,
            "groupId": group_id,
            "startDate": "2025-12-01",
            "dueDate": "2025-12-07",
            "priority": "MEDIUM"
        }
        todo_resp = requests.post(
            f"{schedule_service_url}/v1/todos",
            json=todo_data,
            headers=headers
        )
        assert todo_resp.status_code == 201, f"할일 생성 실패: {todo_resp.text}"
        todo_id = todo_resp.json()["todoId"]

        # Step 5: DB에서 데이터 존재 확인
        cursor = schedule_db_connection.cursor()

        cursor.execute("SELECT COUNT(*) FROM schedules WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 1, "일정이 DB에 없음"

        cursor.execute("SELECT COUNT(*) FROM todos WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 1, "할일이 DB에 없음"

        cursor.execute("SELECT COUNT(*) FROM categories WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 1, "카테고리가 DB에 없음"

        # Step 6: 그룹 삭제 (Cascade 발생!)
        delete_resp = requests.delete(
            f"{user_service_url}/v1/groups/{group_id}",
            headers=headers
        )
        assert delete_resp.status_code == 200, f"그룹 삭제 실패: {delete_resp.text}"

        # Step 7: 삭제 후 약간의 대기 (비동기 처리 대비)
        time.sleep(0.5)

        # Step 8: DB에서 데이터 삭제 확인
        schedule_db_connection.commit()  # 트랜잭션 새로고침

        cursor.execute("SELECT COUNT(*) FROM schedules WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 0, "일정이 삭제되지 않음"

        cursor.execute("SELECT COUNT(*) FROM todos WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 0, "할일이 삭제되지 않음"

        cursor.execute("SELECT COUNT(*) FROM categories WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 0, "카테고리가 삭제되지 않음"

        cursor.close()

    def test_last_owner_leave_group_cascades_to_schedule_data(
        self, user_service_url, schedule_service_url, jwt_auth_tokens, schedule_db_connection
    ):
        """마지막 OWNER 탈퇴 시 그룹 삭제 + Schedule 데이터 cascade 삭제"""
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['user1']}",
            "X-Cognito-Sub": jwt_auth_tokens['user1_sub']
        }

        # Step 1: 그룹 생성
        group_data = {
            "name": f"Leave Test Group {uuid.uuid4().hex[:8]}",
            "description": "Group for leave cascade test"
        }
        create_resp = requests.post(
            f"{user_service_url}/v1/groups",
            json=group_data,
            headers=headers
        )
        assert create_resp.status_code == 201
        group_id = create_resp.json()["groupId"]

        # Step 2: 그룹 카테고리 생성
        category_data = {
            "name": f"Leave Category {uuid.uuid4().hex[:8]}",
            "color": "#33FF57",
            "groupId": group_id
        }
        cat_resp = requests.post(
            f"{schedule_service_url}/v1/categories",
            json=category_data,
            headers=headers
        )
        assert cat_resp.status_code == 201
        category_id = cat_resp.json()["categoryId"]

        # Step 3: 그룹 일정 생성
        schedule_data = {
            "title": f"Leave Schedule {uuid.uuid4().hex[:8]}",
            "categoryId": category_id,
            "groupId": group_id,
            "startTime": "2025-12-15T14:00:00",
            "endTime": "2025-12-15T16:00:00"
        }
        schedule_resp = requests.post(
            f"{schedule_service_url}/v1/schedules",
            json=schedule_data,
            headers=headers
        )
        assert schedule_resp.status_code == 201, f"일정 생성 실패: {schedule_resp.status_code} - {schedule_resp.text}"

        # Step 4: DB에서 데이터 존재 확인 (커밋 후 조회)
        schedule_db_connection.commit()
        cursor = schedule_db_connection.cursor()
        cursor.execute("SELECT COUNT(*) FROM schedules WHERE group_id = %s", (group_id,))
        count = cursor.fetchone()[0]
        assert count == 1, f"일정이 DB에 없음: count={count}, group_id={group_id}"

        # Step 5: 마지막 OWNER 탈퇴 (그룹 삭제됨)
        leave_resp = requests.post(
            f"{user_service_url}/v1/groups/{group_id}/leave",
            headers=headers
        )
        assert leave_resp.status_code == 200, f"그룹 탈퇴 실패: {leave_resp.text}"

        # Step 6: 삭제 후 대기
        time.sleep(0.5)

        # Step 7: DB에서 데이터 삭제 확인
        schedule_db_connection.commit()

        cursor.execute("SELECT COUNT(*) FROM schedules WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 0, "일정이 삭제되지 않음"

        cursor.execute("SELECT COUNT(*) FROM categories WHERE group_id = %s", (group_id,))
        assert cursor.fetchone()[0] == 0, "카테고리가 삭제되지 않음"

        cursor.close()


class TestScheduleInternalGroupApi:
    """Schedule-Service Internal API 테스트"""

    def test_internal_delete_group_data_success(
        self, schedule_service_url, schedule_db_connection, jwt_auth_tokens
    ):
        """Internal API: 그룹 데이터 삭제가 정상 동작하는지 검증"""
        headers = {"Authorization": f"Bearer {jwt_auth_tokens['user1']}"}

        # 테스트용 임시 groupId (실제 그룹 없이)
        test_group_id = 999999

        # Internal API로 그룹 데이터 삭제 요청
        # (데이터 없어도 성공 응답)
        delete_resp = requests.delete(
            f"{schedule_service_url}/api/internal/groups/{test_group_id}/data"
        )

        # Assert
        assert delete_resp.status_code == 200
        response = delete_resp.json()
        assert response["groupId"] == test_group_id
        assert response["success"] is True
        assert response["deletedSchedules"] == 0
        assert response["deletedTodos"] == 0
        assert response["deletedCategories"] == 0


class TestUserToScheduleCoordination:
    """User-Service → Schedule-Service 일정 조율 Internal API 테스트"""

    def test_internal_get_group_member_cognito_subs(
        self, user_service_url, jwt_auth_tokens
    ):
        """Internal API: 그룹 멤버 cognitoSub 목록 조회"""
        # Arrange: 그룹 생성
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['user1']}",
            "X-Cognito-Sub": jwt_auth_tokens['user1_sub']
        }

        group_data = {
            "name": f"Coordination API Test {uuid.uuid4().hex[:8]}",
            "description": "Test group member list API"
        }

        create_resp = requests.post(
            f"{user_service_url}/v1/groups",
            json=group_data,
            headers=headers
        )
        assert create_resp.status_code == 201
        group_id = create_resp.json()["groupId"]

        try:
            # user2 추가
            invite_resp = requests.post(
                f"{user_service_url}/v1/groups/{group_id}/members",
                json={"userCognitoSub": jwt_auth_tokens['user2_sub']},
                headers=headers
            )
            assert invite_resp.status_code == 201

            # Act: Internal API로 그룹 멤버 cognitoSub 목록 조회
            # (Schedule-Service가 공강 찾기 시 호출하는 API)
            members_resp = requests.get(
                f"{user_service_url}/api/internal/groups/{group_id}/members/cognito-subs"
            )

            # Assert
            assert members_resp.status_code == 200
            cognito_subs = members_resp.json()
            assert isinstance(cognito_subs, list)
            assert len(cognito_subs) == 2  # user1, user2
            assert jwt_auth_tokens['user1_sub'] in cognito_subs
            assert jwt_auth_tokens['user2_sub'] in cognito_subs

        finally:
            # Cleanup
            requests.delete(f"{user_service_url}/v1/groups/{group_id}", headers=headers)
