"""
그룹 관리 통합 플로우 테스트

완전한 그룹 관리 시나리오:
1. 그룹 생성
2. 멤버 초대
3. 역할 변경
4. 멤버 제거
5. 그룹 탈퇴
6. 그룹 삭제

cognitoSub 기반 사용자 식별
"""

import pytest
import requests
import uuid


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


class TestGroupManagementFlow:
    """그룹 관리 전체 플로우 통합 테스트"""

    def test_complete_group_lifecycle(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        완전한 그룹 생명주기:
        그룹 생성 → 멤버 초대 → 역할 변경 → 활동 → 멤버 제거 → 탈퇴 → 삭제
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("그룹 관리 전체 플로우 테스트: 생성 → 초대 → 역할 변경 → 제거 → 탈퇴 → 삭제")
        print("=" * 100)

        # =================================================================
        # STEP 1: 사용자 준비 (Alice=OWNER, Bob=ADMIN, Charlie=MEMBER)
        # =================================================================
        print(f"\n[STEP 1/9] 사용자 준비")

        # Alice (그룹 OWNER)
        alice_tokens = jwt_auth_tokens
        alice_cognito_sub = alice_tokens.get("cognito_sub")
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # Bob
        bob = create_test_user(gateway_url, "Bob Admin")
        bob_cognito_sub = bob["cognitoSub"]
        bob_headers = bob["headers"]

        # Charlie
        charlie = create_test_user(gateway_url, "Charlie Member")
        charlie_cognito_sub = charlie["cognitoSub"]
        charlie_headers = charlie["headers"]

        # David (나중에 추가)
        david = create_test_user(gateway_url, "David Member")
        david_cognito_sub = david["cognitoSub"]

        print(f"  - Alice: {alice_tokens['email']} (cognitoSub: {alice_cognito_sub[:20]}...) → OWNER")
        print(f"  - Bob: {bob['email']} (cognitoSub: {bob_cognito_sub[:20]}...) → ADMIN 예정")
        print(f"  - Charlie: {charlie['email']} (cognitoSub: {charlie_cognito_sub[:20]}...) → MEMBER 예정")
        print(f"  - David: {david['email']} (cognitoSub: {david_cognito_sub[:20]}...) → 나중에 추가")
        print(f"  ✅ 4명의 사용자 생성 완료")

        # =================================================================
        # STEP 2: Alice가 그룹 생성
        # =================================================================
        print(f"\n[STEP 2/9] Alice가 그룹 생성")

        group_create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            json={
                "name": "프로젝트 팀",
                "description": "시스템 통합 테스트 그룹"
            },
            timeout=5
        )
        assert group_create_response.status_code == 201
        group = group_create_response.json()
        group_id = group["groupId"]
        assert group["myRole"] == "OWNER"
        assert group["memberCount"] == 1

        print(f"  ✅ 그룹 생성 성공")
        print(f"     - 그룹 ID: {group_id}")
        print(f"     - 이름: {group['name']}")
        print(f"     - Alice 역할: OWNER")
        print(f"     - 멤버 수: 1명")

        # =================================================================
        # STEP 3: Bob을 ADMIN으로 초대
        # =================================================================
        print(f"\n[STEP 3/9] Bob을 ADMIN으로 초대")

        bob_invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            json={"userCognitoSub": bob_cognito_sub, "role": "ADMIN"},
            timeout=5
        )
        assert bob_invite_response.status_code == 201
        bob_member = bob_invite_response.json()
        assert bob_member["role"] == "ADMIN"

        print(f"  ✅ Bob 초대 성공 (ADMIN)")

        # Bob의 그룹 목록 확인
        bob_groups = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=bob_headers,
            timeout=5
        ).json()
        assert len(bob_groups) == 1
        assert bob_groups[0]["groupId"] == group_id
        assert bob_groups[0]["myRole"] == "ADMIN"

        print(f"  ✅ Bob의 그룹 목록에서 확인: 1개 (Role: ADMIN)")

        # =================================================================
        # STEP 4: Charlie를 MEMBER로 초대
        # =================================================================
        print(f"\n[STEP 4/9] Charlie를 MEMBER로 초대")

        charlie_invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            json={"userCognitoSub": charlie_cognito_sub, "role": "MEMBER"},
            timeout=5
        )
        assert charlie_invite_response.status_code == 201
        charlie_member = charlie_invite_response.json()
        charlie_member_id = charlie_member["memberId"]

        print(f"  ✅ Charlie 초대 성공 (MEMBER)")

        # 멤버 목록 확인
        members = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            timeout=5
        ).json()
        assert len(members) == 3  # Alice, Bob, Charlie

        print(f"  ✅ 그룹 멤버: 3명 (OWNER 1, ADMIN 1, MEMBER 1)")

        # =================================================================
        # STEP 5: Bob(ADMIN)이 David를 MEMBER로 초대
        # =================================================================
        print(f"\n[STEP 5/9] Bob(ADMIN)이 David를 MEMBER로 초대")

        david_invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=bob_headers,
            json={"userCognitoSub": david_cognito_sub, "role": "MEMBER"},
            timeout=5
        )
        assert david_invite_response.status_code == 201
        david_member = david_invite_response.json()
        david_member_id = david_member["memberId"]

        print(f"  ✅ Bob(ADMIN)이 David 초대 성공")

        # 멤버 수 확인
        group_detail = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=alice_headers,
            timeout=5
        ).json()
        assert len(group_detail["members"]) == 4

        print(f"  ✅ 그룹 멤버: 4명")

        # =================================================================
        # STEP 6: Alice가 Charlie의 역할을 MEMBER → ADMIN으로 변경
        # =================================================================
        print(f"\n[STEP 6/9] Charlie의 역할 변경 (MEMBER → ADMIN)")

        role_update_response = requests.patch(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{charlie_member_id}/role",
            headers=alice_headers,
            json={"role": "ADMIN"},
            timeout=5
        )
        assert role_update_response.status_code == 200
        updated_charlie = role_update_response.json()
        assert updated_charlie["role"] == "ADMIN"

        print(f"  ✅ Charlie 역할 변경 성공: MEMBER → ADMIN")

        # Charlie의 그룹 목록에서 역할 확인
        charlie_groups = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=charlie_headers,
            timeout=5
        ).json()
        assert charlie_groups[0]["myRole"] == "ADMIN"

        print(f"  ✅ Charlie의 그룹에서 역할 확인: ADMIN")

        # =================================================================
        # STEP 7: Bob(ADMIN)이 David(MEMBER) 제거
        # =================================================================
        print(f"\n[STEP 7/9] Bob(ADMIN)이 David(MEMBER) 제거")

        remove_response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{david_member_id}",
            headers=bob_headers,
            timeout=5
        )
        assert remove_response.status_code == 200

        print(f"  ✅ Bob(ADMIN)이 David 제거 성공")

        # 멤버 수 확인
        members_after_remove = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            timeout=5
        ).json()
        assert len(members_after_remove) == 3  # Alice, Bob, Charlie

        print(f"  ✅ 그룹 멤버: 3명 (David 제거됨)")

        # =================================================================
        # STEP 8: Charlie가 그룹 탈퇴
        # =================================================================
        print(f"\n[STEP 8/9] Charlie가 그룹 탈퇴")

        leave_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/leave",
            headers=charlie_headers,
            timeout=5
        )
        assert leave_response.status_code == 200

        print(f"  ✅ Charlie 탈퇴 성공")

        # Charlie의 그룹 목록 확인
        charlie_groups_after_leave = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=charlie_headers,
            timeout=5
        ).json()
        assert len(charlie_groups_after_leave) == 0

        print(f"  ✅ Charlie의 그룹 목록: 0개")

        # 그룹 멤버 수 확인
        members_after_leave = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            timeout=5
        ).json()
        assert len(members_after_leave) == 2  # Alice, Bob

        print(f"  ✅ 그룹 멤버: 2명 (Charlie 탈퇴)")

        # =================================================================
        # STEP 9: Alice가 그룹 삭제
        # =================================================================
        print(f"\n[STEP 9/9] Alice(OWNER)가 그룹 삭제")

        delete_response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=alice_headers,
            timeout=5
        )
        assert delete_response.status_code == 200

        print(f"  ✅ 그룹 삭제 성공")

        # 모든 사용자의 그룹 목록 확인
        alice_groups_after = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            timeout=5
        ).json()
        bob_groups_after = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=bob_headers,
            timeout=5
        ).json()

        assert len(alice_groups_after) == 0
        assert len(bob_groups_after) == 0

        print(f"  ✅ 모든 멤버의 그룹 목록에서 삭제됨")

        # =================================================================
        # 최종 요약
        # =================================================================
        print("\n" + "=" * 100)
        print("[PASS] 그룹 관리 전체 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ STEP 1: 사용자 4명 준비 (Alice, Bob, Charlie, David)")
        print(f"✅ STEP 2: Alice가 그룹 생성 (OWNER)")
        print(f"✅ STEP 3: Bob을 ADMIN으로 초대")
        print(f"✅ STEP 4: Charlie를 MEMBER로 초대")
        print(f"✅ STEP 5: Bob(ADMIN)이 David를 MEMBER로 초대")
        print(f"✅ STEP 6: Charlie의 역할 변경 (MEMBER → ADMIN)")
        print(f"✅ STEP 7: Bob(ADMIN)이 David(MEMBER) 제거")
        print(f"✅ STEP 8: Charlie가 그룹 탈퇴")
        print(f"✅ STEP 9: Alice(OWNER)가 그룹 삭제")
        print("=" * 100 + "\n")


class TestGroupPermissionFlow:
    """그룹 권한 검증 플로우 통합 테스트"""

    def test_permission_enforcement_flow(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 권한 검증:
        1. MEMBER는 초대/제거 불가
        2. ADMIN은 MEMBER만 제거 가능
        3. OWNER만 역할 변경 가능
        4. OWNER만 그룹 수정/삭제 가능
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("그룹 권한 검증 플로우 테스트")
        print("=" * 100)

        # 사용자 준비
        alice_tokens = jwt_auth_tokens  # OWNER
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # Bob (ADMIN), Charlie (MEMBER), David (새 사용자)
        bob = create_test_user(gateway_url, "Bob Perm Admin")
        charlie = create_test_user(gateway_url, "Charlie Perm Member")
        david = create_test_user(gateway_url, "David Perm New")

        print(f"\n[STEP 1/5] 그룹 및 멤버 준비")

        # 그룹 생성
        group_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            json={"name": "권한 테스트 그룹", "description": "권한 검증용"},
            timeout=5
        )
        group_id = group_response.json()["groupId"]

        # Bob(ADMIN), Charlie(MEMBER) 초대
        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            json={"userCognitoSub": bob["cognitoSub"], "role": "ADMIN"},
            timeout=5
        )
        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            json={"userCognitoSub": charlie["cognitoSub"], "role": "MEMBER"},
            timeout=5
        )

        print(f"  ✅ 그룹 생성 및 멤버 초대 완료")
        print(f"     - Alice: OWNER")
        print(f"     - Bob: ADMIN")
        print(f"     - Charlie: MEMBER")

        # =================================================================
        # 권한 테스트 1: MEMBER는 초대 불가
        # =================================================================
        print(f"\n[STEP 2/5] MEMBER는 멤버 초대 불가")

        charlie_invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=charlie["headers"],
            json={"userCognitoSub": david["cognitoSub"], "role": "MEMBER"},
            timeout=5
        )
        assert charlie_invite_response.status_code == 403
        assert charlie_invite_response.json().get("errorCode") == "INSUFFICIENT_PERMISSION"

        print(f"  ✅ MEMBER(Charlie)의 초대 시도 차단됨 (403)")

        # =================================================================
        # 권한 테스트 2: ADMIN은 초대 가능
        # =================================================================
        print(f"\n[STEP 3/5] ADMIN은 멤버 초대 가능")

        bob_invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=bob["headers"],
            json={"userCognitoSub": david["cognitoSub"], "role": "MEMBER"},
            timeout=5
        )
        assert bob_invite_response.status_code == 201
        david_member_id = bob_invite_response.json()["memberId"]

        print(f"  ✅ ADMIN(Bob)의 초대 성공")

        # =================================================================
        # 권한 테스트 3: ADMIN은 ADMIN 제거 불가
        # =================================================================
        print(f"\n[STEP 4/5] ADMIN은 다른 ADMIN 제거 불가")

        # 새 ADMIN 추가
        eve = create_test_user(gateway_url, "Eve Perm Admin")

        eve_invite = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            json={"userCognitoSub": eve["cognitoSub"], "role": "ADMIN"},
            timeout=5
        )
        eve_member_id = eve_invite.json()["memberId"]

        # Bob(ADMIN)이 Eve(ADMIN) 제거 시도
        bob_remove_admin_response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{eve_member_id}",
            headers=bob["headers"],
            timeout=5
        )
        assert bob_remove_admin_response.status_code == 403
        assert bob_remove_admin_response.json().get("errorCode") == "INSUFFICIENT_PERMISSION"

        print(f"  ✅ ADMIN(Bob)의 ADMIN(Eve) 제거 시도 차단됨 (403)")

        # =================================================================
        # 권한 테스트 4: ADMIN은 MEMBER 제거 가능
        # =================================================================
        print(f"\n[STEP 5/5] ADMIN은 MEMBER 제거 가능")

        bob_remove_member_response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{david_member_id}",
            headers=bob["headers"],
            timeout=5
        )
        assert bob_remove_member_response.status_code == 200

        print(f"  ✅ ADMIN(Bob)의 MEMBER(David) 제거 성공")

        print("\n" + "=" * 100)
        print("[PASS] 그룹 권한 검증 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ MEMBER는 초대 불가 (403 Forbidden)")
        print(f"✅ ADMIN은 초대 가능")
        print(f"✅ ADMIN은 다른 ADMIN 제거 불가 (403 Forbidden)")
        print(f"✅ ADMIN은 MEMBER 제거 가능")
        print("=" * 100 + "\n")


class TestGroupScheduleIncludeFlow:
    """그룹 일정 + 개인 일정 통합 조회 플로우"""

    def test_include_groups_schedule_query(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        includeGroups=true 시나리오:
        1. 그룹 생성 후 그룹 카테고리/일정 생성
        2. 개인 카테고리/일정 생성
        3. includeGroups=true로 개인+그룹 일정 모두 조회
        4. groupId 필터로 그룹 일정만 조회
        5. status 필터 적용 확인
        6. 그룹이 없는 사용자의 includeGroups 동작 확인
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("그룹 일정 + 개인 일정 통합 조회 (includeGroups=true) 시나리오")
        print("=" * 100)

        # 사용자 준비 (OWNER)
        owner_tokens = jwt_auth_tokens
        owner_headers = {
            "Authorization": f"Bearer {owner_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 1) 그룹 생성
        group_resp = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "통합 조회 그룹", "description": "includeGroups 테스트"},
            timeout=5
        )
        assert group_resp.status_code == 201, f"그룹 생성 실패: {group_resp.text}"
        group_id = group_resp.json()["groupId"]
        print(f"  ✅ 그룹 생성 (groupId={group_id})")

        # 2) 개인/그룹 카테고리 생성
        personal_cat_resp = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=owner_headers,
            json={"name": f"개인카테고리-{uuid.uuid4().hex[:6]}", "color": "#123456"},
            timeout=5
        )
        assert personal_cat_resp.status_code == 201, f"개인 카테고리 생성 실패: {personal_cat_resp.text}"
        personal_cat_id = personal_cat_resp.json()["categoryId"]

        group_cat_resp = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=owner_headers,
            json={
                "name": f"그룹카테고리-{uuid.uuid4().hex[:6]}",
                "color": "#FF8844",
                "groupId": group_id
            },
            timeout=5
        )
        assert group_cat_resp.status_code == 201, f"그룹 카테고리 생성 실패: {group_cat_resp.text}"
        group_cat_id = group_cat_resp.json()["categoryId"]
        print(f"  ✅ 카테고리 생성 (personal={personal_cat_id}, group={group_cat_id})")

        # 3) 개인 일정 생성
        personal_schedule_resp = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=owner_headers,
            json={
                "title": f"개인 일정 {uuid.uuid4().hex[:4]}",
                "categoryId": personal_cat_id,
                "startTime": "2025-12-10T09:00:00",
                "endTime": "2025-12-10T10:00:00"
            },
            timeout=5
        )
        assert personal_schedule_resp.status_code == 201, f"개인 일정 생성 실패: {personal_schedule_resp.text}"
        personal_schedule = personal_schedule_resp.json()
        print(f"  ✅ 개인 일정 생성 (scheduleId={personal_schedule['scheduleId']})")

        # 4) 그룹 일정 생성
        group_schedule_resp = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=owner_headers,
            json={
                "title": f"그룹 일정 {uuid.uuid4().hex[:4]}",
                "categoryId": group_cat_id,
                "groupId": group_id,
                "startTime": "2025-12-11T11:00:00",
                "endTime": "2025-12-11T12:00:00"
            },
            timeout=5
        )
        assert group_schedule_resp.status_code == 201, f"그룹 일정 생성 실패: {group_schedule_resp.text}"
        group_schedule = group_schedule_resp.json()
        print(f"  ✅ 그룹 일정 생성 (scheduleId={group_schedule['scheduleId']})")

        # 5) includeGroups=true 조회 (개인+그룹 일정 모두 포함)
        include_resp = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=owner_headers,
            params={
                "includeGroups": "true",
                "startDate": "2025-12-01T00:00:00",
                "endDate": "2025-12-31T23:59:59"
            },
            timeout=5
        )
        assert include_resp.status_code == 200, f"includeGroups 조회 실패: {include_resp.text}"
        schedules = include_resp.json()
        assert any(s["scheduleId"] == personal_schedule["scheduleId"] for s in schedules), "개인 일정이 포함되지 않음"
        assert any(s["scheduleId"] == group_schedule["scheduleId"] and s.get("groupId") == group_id for s in schedules), "그룹 일정이 포함되지 않음"
        print(f"  ✅ includeGroups=true 조회: 개인+그룹 일정 모두 포함 (total={len(schedules)})")

        # 6) groupId 필터로 그룹 일정만 조회
        group_only_resp = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=owner_headers,
            params={
                "groupId": str(group_id),
                "startDate": "2025-12-01T00:00:00",
                "endDate": "2025-12-31T23:59:59"
            },
            timeout=5
        )
        assert group_only_resp.status_code == 200, f"그룹 일정 조회 실패: {group_only_resp.text}"
        group_only = group_only_resp.json()
        assert all(s.get("groupId") == group_id for s in group_only), "groupId 필터에 다른 일정 포함"
        print(f"  ✅ groupId 필터 조회: 그룹 일정만 포함 (total={len(group_only)})")

        # 7) includeGroups + status 필터(DONE) 검증 (완료 일정만 있는 사용자로 설정)
        # 개인 일정 상태 변경
        status_update_resp = requests.patch(
            f"{gateway_url}/api/v1/schedules/{personal_schedule['scheduleId']}/status",
            headers=owner_headers,
            json={"status": "DONE"},
            timeout=5
        )
        assert status_update_resp.status_code == 200, f"개인 일정 상태 변경 실패: {status_update_resp.text}"

        status_filtered_resp = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=owner_headers,
            params={
                "includeGroups": "true",
                "status": "DONE",
                "startDate": "2025-12-01T00:00:00",
                "endDate": "2025-12-31T23:59:59"
            },
            timeout=5
        )
        assert status_filtered_resp.status_code == 200, f"status 필터 조회 실패: {status_filtered_resp.text}"
        status_schedules = status_filtered_resp.json()
        assert all(s.get("status") == "DONE" for s in status_schedules), "status=DONE 필터가 적용되지 않음"
        print(f"  ✅ includeGroups + status=DONE 필터 적용 확인 (total={len(status_schedules)})")

        # 8) includeGroups + groupId 동시 전달 시 groupId 우선 동작 확인
        group_and_include_resp = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=owner_headers,
            params={
                "groupId": str(group_id),
                "includeGroups": "true"
            },
            timeout=5
        )
        assert group_and_include_resp.status_code == 200, f"groupId+includeGroups 조회 실패: {group_and_include_resp.text}"
        group_only_again = group_and_include_resp.json()
        assert all(s.get("groupId") == group_id for s in group_only_again), "groupId 우선 동작이 적용되지 않음"
        print(f"  ✅ groupId 우선 동작 확인 (total={len(group_only_again)})")

        # 9) 그룹이 없는 사용자의 includeGroups=true 호출 → 개인 일정만 포함
        solo_user = create_test_user(gateway_url, "Solo User")
        solo_headers = solo_user["headers"]

        # 개인 카테고리/일정 생성
        solo_cat_resp = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=solo_headers,
            json={"name": f"솔로카테-{uuid.uuid4().hex[:6]}", "color": "#888888"},
            timeout=5
        )
        assert solo_cat_resp.status_code == 201, f"솔로 카테고리 생성 실패: {solo_cat_resp.text}"
        solo_cat_id = solo_cat_resp.json()["categoryId"]

        solo_schedule_resp = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=solo_headers,
            json={
                "title": "솔로 일정",
                "categoryId": solo_cat_id,
                "startTime": "2025-12-15T09:00:00",
                "endTime": "2025-12-15T10:00:00"
            },
            timeout=5
        )
        assert solo_schedule_resp.status_code == 201, f"솔로 일정 생성 실패: {solo_schedule_resp.text}"
        solo_schedule = solo_schedule_resp.json()

        solo_include_resp = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=solo_headers,
            params={
                "includeGroups": "true",
                "startDate": "2025-12-01T00:00:00",
                "endDate": "2025-12-31T23:59:59"
            },
            timeout=5
        )
        assert solo_include_resp.status_code == 200, f"솔로 includeGroups 조회 실패: {solo_include_resp.text}"
        solo_schedules = solo_include_resp.json()
        assert len(solo_schedules) == 1 and solo_schedules[0]["scheduleId"] == solo_schedule["scheduleId"], "그룹 없는 사용자의 개인 일정만 반환되지 않음"
        print(f"  ✅ 그룹 없는 사용자의 includeGroups=true: 개인 일정만 반환 (total={len(solo_schedules)})")

        # Cleanup: 그룹 삭제(그룹 일정/카테고리 cascade), 개인 일정/카테고리 삭제
        requests.delete(f"{gateway_url}/api/v1/groups/{group_id}", headers=owner_headers, timeout=5)
        requests.delete(f"{gateway_url}/api/v1/schedules/{personal_schedule['scheduleId']}", headers=owner_headers, timeout=5)
        requests.delete(f"{gateway_url}/api/v1/categories/{personal_cat_id}", headers=owner_headers, timeout=5)

        print("\n" + "=" * 100)
        print("[PASS] includeGroups=true 통합 조회 시나리오 성공")
        print("=" * 100 + "\n")

class TestGroupUpdateFlow:
    """그룹 수정 플로우 통합 테스트"""

    def test_group_update_and_details_flow(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 수정 플로우:
        1. 그룹 생성
        2. 그룹 정보 수정 (OWNER만)
        3. 그룹 상세 조회
        4. ADMIN/MEMBER는 수정 불가 확인
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("그룹 수정 플로우 테스트")
        print("=" * 100)

        # Alice (OWNER), Bob (ADMIN) 준비
        alice_tokens = jwt_auth_tokens
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        bob = create_test_user(gateway_url, "Bob Update Admin")

        print(f"\n[STEP 1/4] 그룹 생성 및 Bob(ADMIN) 초대")

        # 그룹 생성
        group_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            json={"name": "원본 이름", "description": "원본 설명"},
            timeout=5
        )
        group_id = group_response.json()["groupId"]

        # Bob ADMIN으로 초대
        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=alice_headers,
            json={"userCognitoSub": bob["cognitoSub"], "role": "ADMIN"},
            timeout=5
        )

        print(f"  ✅ 그룹 생성 및 Bob 초대 완료")

        # =================================================================
        # STEP 2: Alice(OWNER)가 그룹 수정
        # =================================================================
        print(f"\n[STEP 2/4] Alice(OWNER)가 그룹 수정")

        update_response = requests.put(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=alice_headers,
            json={"name": "수정된 이름", "description": "수정된 설명"},
            timeout=5
        )
        assert update_response.status_code == 200
        updated_group = update_response.json()
        assert updated_group["name"] == "수정된 이름"
        assert updated_group["description"] == "수정된 설명"

        print(f"  ✅ 그룹 수정 성공")
        print(f"     - 이름: 원본 이름 → 수정된 이름")
        print(f"     - 설명: 원본 설명 → 수정된 설명")

        # =================================================================
        # STEP 3: 그룹 상세 조회
        # =================================================================
        print(f"\n[STEP 3/4] 그룹 상세 조회")

        detail_response = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=alice_headers,
            timeout=5
        )
        assert detail_response.status_code == 200
        group_detail = detail_response.json()
        assert group_detail["name"] == "수정된 이름"
        assert len(group_detail["members"]) == 2  # Alice, Bob

        print(f"  ✅ 그룹 상세 조회 성공")
        print(f"     - 이름: {group_detail['name']}")
        print(f"     - 멤버 수: {len(group_detail['members'])}명")

        # =================================================================
        # STEP 4: Bob(ADMIN)은 수정 불가
        # =================================================================
        print(f"\n[STEP 4/4] Bob(ADMIN)은 그룹 수정 불가")

        bob_update_response = requests.put(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=bob["headers"],
            json={"name": "Bob이 수정", "description": "실패해야 함"},
            timeout=5
        )
        assert bob_update_response.status_code == 403
        assert bob_update_response.json().get("errorCode") == "INSUFFICIENT_PERMISSION"

        print(f"  ✅ ADMIN(Bob)의 수정 시도 차단됨 (403)")

        print("\n" + "=" * 100)
        print("[PASS] 그룹 수정 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ OWNER(Alice)는 그룹 수정 가능")
        print(f"✅ ADMIN(Bob)은 그룹 수정 불가 (403 Forbidden)")
        print(f"✅ 그룹 상세 조회 정상 작동")
        print("=" * 100 + "\n")


class TestOwnerLeaveFlow:
    """OWNER 탈퇴 플로우 통합 테스트"""

    def test_owner_leave_scenarios(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        OWNER 탈퇴 시나리오:
        1. 다른 멤버 있을 때 탈퇴 불가 (소유권 이전 필요)
        2. 마지막 멤버일 때 탈퇴 시 그룹 자동 삭제
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("OWNER 탈퇴 플로우 테스트")
        print("=" * 100)

        # Alice (OWNER), Bob (MEMBER) 준비
        alice_tokens = jwt_auth_tokens
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        bob = create_test_user(gateway_url, "Bob Leave Member")

        # =================================================================
        # 시나리오 1: 다른 멤버 있을 때 OWNER 탈퇴 불가
        # =================================================================
        print(f"\n[시나리오 1] 다른 멤버 있을 때 OWNER 탈퇴 불가")

        # 그룹 생성
        group1_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            json={"name": "탈퇴 테스트 그룹1", "description": "테스트"},
            timeout=5
        )
        group1_id = group1_response.json()["groupId"]

        # Bob 초대
        requests.post(
            f"{gateway_url}/api/v1/groups/{group1_id}/members",
            headers=alice_headers,
            json={"userCognitoSub": bob["cognitoSub"], "role": "MEMBER"},
            timeout=5
        )

        print(f"  ✅ 그룹 생성 및 Bob 초대 (멤버 2명)")

        # Alice 탈퇴 시도
        alice_leave_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group1_id}/leave",
            headers=alice_headers,
            timeout=5
        )
        assert alice_leave_response.status_code == 400
        error_msg = alice_leave_response.json().get("message", "")
        assert "ownership" in error_msg.lower()

        print(f"  ✅ OWNER 탈퇴 차단됨 (400 Bad Request)")
        print(f"     - 메시지: {error_msg}")

        # =================================================================
        # 시나리오 2: 마지막 멤버일 때 탈퇴 시 그룹 자동 삭제
        # =================================================================
        print(f"\n[시나리오 2] 마지막 멤버(OWNER)일 때 탈퇴 시 그룹 자동 삭제")

        # 새 그룹 생성 (멤버 1명: Alice만)
        group2_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            json={"name": "탈퇴 테스트 그룹2", "description": "테스트"},
            timeout=5
        )
        group2_id = group2_response.json()["groupId"]

        print(f"  ✅ 그룹 생성 (멤버 1명: OWNER만)")

        # Alice 탈퇴
        alice_leave_solo_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group2_id}/leave",
            headers=alice_headers,
            timeout=5
        )
        assert alice_leave_solo_response.status_code == 200

        print(f"  ✅ OWNER 탈퇴 성공 (마지막 멤버)")

        # 그룹 자동 삭제 확인
        alice_groups = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            timeout=5
        ).json()
        assert isinstance(alice_groups, list), f"Expected list, got {type(alice_groups)}: {alice_groups}"
        assert not any(g["groupId"] == group2_id for g in alice_groups)

        print(f"  ✅ 그룹 자동 삭제 확인")

        print("\n" + "=" * 100)
        print("[PASS] OWNER 탈퇴 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ 다른 멤버 있을 때: OWNER 탈퇴 불가 (소유권 이전 필요)")
        print(f"✅ 마지막 멤버일 때: OWNER 탈퇴 시 그룹 자동 삭제")
        print("=" * 100 + "\n")


class TestGroupScheduleCascadeFlow:
    """그룹 삭제 시 Schedule 데이터 cascade 삭제 테스트"""

    def test_group_delete_cascades_schedule_data(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 삭제 시 Schedule 데이터 cascade 삭제:
        1. 그룹 생성
        2. 그룹 카테고리 생성
        3. 그룹 일정/할일 생성
        4. 그룹 삭제
        5. Schedule 데이터 삭제 확인
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("그룹 삭제 → Schedule 데이터 Cascade 삭제 테스트")
        print("=" * 100)

        # Alice (OWNER) 준비
        alice_tokens = jwt_auth_tokens
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # =================================================================
        # STEP 1: 그룹 생성
        # =================================================================
        print(f"\n[STEP 1/6] 그룹 생성")

        group_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            json={"name": "Cascade 테스트 그룹", "description": "Schedule cascade 삭제 테스트"},
            timeout=5
        )
        assert group_response.status_code == 201
        group_id = group_response.json()["groupId"]

        print(f"  ✅ 그룹 생성 성공 (groupId: {group_id})")

        # =================================================================
        # STEP 2: 그룹 카테고리 생성
        # =================================================================
        print(f"\n[STEP 2/6] 그룹 카테고리 생성")

        category_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=alice_headers,
            json={
                "name": f"그룹 카테고리 {uuid.uuid4().hex[:8]}",
                "color": "#FF5733",
                "groupId": group_id
            },
            timeout=5
        )
        assert category_response.status_code == 201, f"카테고리 생성 실패: {category_response.text}"
        category_id = category_response.json()["categoryId"]

        print(f"  ✅ 그룹 카테고리 생성 성공 (categoryId: {category_id})")

        # =================================================================
        # STEP 3: 그룹 일정 생성
        # =================================================================
        print(f"\n[STEP 3/6] 그룹 일정 생성")

        schedule_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=alice_headers,
            json={
                "title": f"그룹 일정 {uuid.uuid4().hex[:8]}",
                "categoryId": category_id,
                "groupId": group_id,
                "startTime": "2025-12-01T09:00:00",
                "endTime": "2025-12-01T10:00:00"
            },
            timeout=5
        )
        assert schedule_response.status_code == 201, f"일정 생성 실패: {schedule_response.text}"
        schedule_id = schedule_response.json()["scheduleId"]

        print(f"  ✅ 그룹 일정 생성 성공 (scheduleId: {schedule_id})")

        # =================================================================
        # STEP 4: 그룹 할일 생성
        # =================================================================
        print(f"\n[STEP 4/6] 그룹 할일 생성")

        todo_response = requests.post(
            f"{gateway_url}/api/v1/todos",
            headers=alice_headers,
            json={
                "title": f"그룹 할일 {uuid.uuid4().hex[:8]}",
                "categoryId": category_id,
                "groupId": group_id,
                "startDate": "2025-12-01",
                "dueDate": "2025-12-07",
                "priority": "MEDIUM"
            },
            timeout=5
        )
        assert todo_response.status_code == 201, f"할일 생성 실패: {todo_response.text}"
        todo_id = todo_response.json()["todoId"]

        print(f"  ✅ 그룹 할일 생성 성공 (todoId: {todo_id})")

        # =================================================================
        # STEP 5: 그룹 삭제 (Cascade 발생!)
        # =================================================================
        print(f"\n[STEP 5/6] 그룹 삭제 (Cascade 발생)")

        delete_response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=alice_headers,
            timeout=5
        )
        assert delete_response.status_code == 200, f"그룹 삭제 실패: {delete_response.text}"

        print(f"  ✅ 그룹 삭제 성공")

        # =================================================================
        # STEP 6: Schedule 데이터 삭제 확인 (API로 조회 불가 확인)
        # =================================================================
        print(f"\n[STEP 6/6] Schedule 데이터 삭제 확인")

        # 일정 조회 시도 → 404 예상
        schedule_check = requests.get(
            f"{gateway_url}/api/v1/schedules/{schedule_id}",
            headers=alice_headers,
            timeout=5
        )
        assert schedule_check.status_code == 404, f"일정이 삭제되지 않음: {schedule_check.status_code}"
        print(f"  ✅ 일정 삭제 확인 (404 Not Found)")

        # 할일 조회 시도 → 404 예상
        todo_check = requests.get(
            f"{gateway_url}/api/v1/todos/{todo_id}",
            headers=alice_headers,
            timeout=5
        )
        assert todo_check.status_code == 404, f"할일이 삭제되지 않음: {todo_check.status_code}"
        print(f"  ✅ 할일 삭제 확인 (404 Not Found)")

        # 카테고리 조회 시도 → 404 예상
        category_check = requests.get(
            f"{gateway_url}/api/v1/categories/{category_id}",
            headers=alice_headers,
            timeout=5
        )
        assert category_check.status_code == 404, f"카테고리가 삭제되지 않음: {category_check.status_code}"
        print(f"  ✅ 카테고리 삭제 확인 (404 Not Found)")

        # =================================================================
        # 최종 요약
        # =================================================================
        print("\n" + "=" * 100)
        print("[PASS] 그룹 삭제 → Schedule 데이터 Cascade 삭제 테스트 성공!")
        print("=" * 100)
        print(f"✅ STEP 1: 그룹 생성")
        print(f"✅ STEP 2: 그룹 카테고리 생성")
        print(f"✅ STEP 3: 그룹 일정 생성")
        print(f"✅ STEP 4: 그룹 할일 생성")
        print(f"✅ STEP 5: 그룹 삭제 (User-Service → Schedule-Service Internal API 호출)")
        print(f"✅ STEP 6: 모든 그룹 데이터 cascade 삭제 확인")
        print("=" * 100 + "\n")

    def test_owner_leave_cascades_schedule_data(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        마지막 OWNER 탈퇴 시 Schedule 데이터 cascade 삭제:
        1. 그룹 생성 (멤버: OWNER 1명)
        2. 그룹 일정 생성
        3. 마지막 OWNER 탈퇴 → 그룹 자동 삭제
        4. Schedule 데이터 삭제 확인
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("마지막 OWNER 탈퇴 → Schedule 데이터 Cascade 삭제 테스트")
        print("=" * 100)

        # Alice (OWNER) 준비
        alice_tokens = jwt_auth_tokens
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # =================================================================
        # STEP 1: 그룹 생성 (멤버 1명: OWNER만)
        # =================================================================
        print(f"\n[STEP 1/4] 그룹 생성 (멤버: OWNER 1명)")

        group_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=alice_headers,
            json={"name": "Leave Cascade 테스트", "description": "탈퇴 cascade 테스트"},
            timeout=5
        )
        assert group_response.status_code == 201
        group_id = group_response.json()["groupId"]

        print(f"  ✅ 그룹 생성 성공 (groupId: {group_id}, 멤버: 1명)")

        # =================================================================
        # STEP 2: 그룹 카테고리 및 일정 생성
        # =================================================================
        print(f"\n[STEP 2/4] 그룹 카테고리 및 일정 생성")

        # 카테고리
        cat_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=alice_headers,
            json={
                "name": f"Leave Category {uuid.uuid4().hex[:8]}",
                "color": "#33FF57",
                "groupId": group_id
            },
            timeout=5
        )
        assert cat_response.status_code == 201
        category_id = cat_response.json()["categoryId"]

        # 일정
        schedule_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=alice_headers,
            json={
                "title": f"Leave Schedule {uuid.uuid4().hex[:8]}",
                "categoryId": category_id,
                "groupId": group_id,
                "startTime": "2025-12-15T14:00:00",
                "endTime": "2025-12-15T16:00:00"
            },
            timeout=5
        )
        assert schedule_response.status_code == 201
        schedule_id = schedule_response.json()["scheduleId"]

        print(f"  ✅ 카테고리 생성 성공 (categoryId: {category_id})")
        print(f"  ✅ 일정 생성 성공 (scheduleId: {schedule_id})")

        # =================================================================
        # STEP 3: 마지막 OWNER 탈퇴 → 그룹 자동 삭제
        # =================================================================
        print(f"\n[STEP 3/4] 마지막 OWNER 탈퇴 (그룹 자동 삭제)")

        leave_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/leave",
            headers=alice_headers,
            timeout=5
        )
        assert leave_response.status_code == 200, f"탈퇴 실패: {leave_response.text}"

        print(f"  ✅ OWNER 탈퇴 성공 → 그룹 자동 삭제")

        # =================================================================
        # STEP 4: Schedule 데이터 삭제 확인
        # =================================================================
        print(f"\n[STEP 4/4] Schedule 데이터 삭제 확인")

        # 일정 조회 시도 → 404 예상
        schedule_check = requests.get(
            f"{gateway_url}/api/v1/schedules/{schedule_id}",
            headers=alice_headers,
            timeout=5
        )
        assert schedule_check.status_code == 404, f"일정이 삭제되지 않음: {schedule_check.status_code}"
        print(f"  ✅ 일정 삭제 확인 (404 Not Found)")

        # 카테고리 조회 시도 → 404 예상
        category_check = requests.get(
            f"{gateway_url}/api/v1/categories/{category_id}",
            headers=alice_headers,
            timeout=5
        )
        assert category_check.status_code == 404, f"카테고리가 삭제되지 않음: {category_check.status_code}"
        print(f"  ✅ 카테고리 삭제 확인 (404 Not Found)")

        # =================================================================
        # 최종 요약
        # =================================================================
        print("\n" + "=" * 100)
        print("[PASS] 마지막 OWNER 탈퇴 → Schedule 데이터 Cascade 삭제 테스트 성공!")
        print("=" * 100)
        print(f"✅ STEP 1: 그룹 생성 (멤버: OWNER 1명)")
        print(f"✅ STEP 2: 그룹 카테고리 및 일정 생성")
        print(f"✅ STEP 3: 마지막 OWNER 탈퇴 → 그룹 자동 삭제")
        print(f"✅ STEP 4: Schedule 데이터 cascade 삭제 확인")
        print("=" * 100 + "\n")


class TestScheduleCoordinationFlow:
    """일정 조율 플로우 통합 테스트"""

    def test_team_meeting_coordination_flow(self, service_urls, clean_user_database):
        """
        팀 프로젝트 미팅 일정 조율 시나리오:
        1. 팀장이 프로젝트 그룹 생성
        2. 팀원 초대
        3. 각자 개인 일정 등록
        4. 공강 시간 조회 (API Gateway 통해)
        5. 발견된 공강 시간에 그룹 미팅 일정 생성
        6. 모든 멤버가 그룹 일정 조회 가능 확인
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("팀 프로젝트 미팅 일정 조율 E2E 플로우 테스트")
        print("=" * 100)

        # =================================================================
        # STEP 1: 사용자 준비 (Leader, Member)
        # =================================================================
        print(f"\n[STEP 1/6] 사용자 준비")

        leader = create_test_user(gateway_url, "Team Leader")
        member = create_test_user(gateway_url, "Team Member")

        print(f"  ✅ Team Leader 생성: {leader['email']}")
        print(f"  ✅ Team Member 생성: {member['email']}")

        # =================================================================
        # STEP 2: 그룹 생성 및 멤버 초대
        # =================================================================
        print(f"\n[STEP 2/6] 그룹 생성 및 멤버 초대")

        group_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=leader['headers'],
            json={"name": "SE Project Team", "description": "Software Engineering Final Project"},
            timeout=5
        )
        assert group_response.status_code == 201
        group_id = group_response.json()["groupId"]

        print(f"  ✅ 그룹 생성 성공 (groupId: {group_id})")

        # 멤버 초대
        invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=leader['headers'],
            json={"userCognitoSub": member['cognitoSub']},
            timeout=5
        )
        assert invite_response.status_code == 201

        print(f"  ✅ 멤버 초대 성공")

        # =================================================================
        # STEP 3: 각자 개인 일정 등록
        # =================================================================
        print(f"\n[STEP 3/6] 각자 개인 일정 등록")

        # Leader 카테고리
        leader_cat_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=leader['headers'],
            json={"name": "Personal", "color": "#FF5733"},
            timeout=5
        )
        assert leader_cat_response.status_code == 201
        leader_cat_id = leader_cat_response.json()["categoryId"]

        # Leader 일정 (09:00-12:00)
        leader_schedule_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=leader['headers'],
            json={
                "title": "Operating Systems Class",
                "categoryId": leader_cat_id,
                "startTime": "2025-12-08T09:00:00",
                "endTime": "2025-12-08T12:00:00"
            },
            timeout=5
        )
        assert leader_schedule_response.status_code == 201

        # Member 카테고리
        member_cat_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=member['headers'],
            json={"name": "Personal", "color": "#33FF57"},
            timeout=5
        )
        assert member_cat_response.status_code == 201
        member_cat_id = member_cat_response.json()["categoryId"]

        # Member 일정 (14:00-16:00)
        member_schedule_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=member['headers'],
            json={
                "title": "Database Class",
                "categoryId": member_cat_id,
                "startTime": "2025-12-08T14:00:00",
                "endTime": "2025-12-08T16:00:00"
            },
            timeout=5
        )
        assert member_schedule_response.status_code == 201

        print(f"  ✅ Leader 일정 등록 (09:00-12:00)")
        print(f"  ✅ Member 일정 등록 (14:00-16:00)")

        # =================================================================
        # STEP 4: 공강 시간 조회 (API Gateway 통해)
        # =================================================================
        print(f"\n[STEP 4/6] 공강 시간 조회")

        free_slots_response = requests.post(
            f"{gateway_url}/api/v1/schedules/find-free-slots",
            headers=leader['headers'],
            json={
                "groupId": group_id,
                "startDate": "2025-12-08",
                "endDate": "2025-12-08",
                "minDurationMinutes": 120,  # 최소 2시간
                "workingHoursStart": "09:00",
                "workingHoursEnd": "20:00"
            },
            timeout=5
        )

        assert free_slots_response.status_code == 200
        free_slots_data = free_slots_response.json()

        assert free_slots_data["groupId"] == group_id
        assert free_slots_data["memberCount"] == 2
        assert len(free_slots_data["freeSlots"]) > 0

        # 12:00-14:00 또는 16:00-20:00 공강 확인
        found_slot = None
        for slot in free_slots_data["freeSlots"]:
            if "16:00:00" in slot["startTime"] and slot["durationMinutes"] >= 120:
                found_slot = slot
                break

        assert found_slot is not None, "2시간 이상 공강이 발견되지 않음"

        print(f"  ✅ 공강 시간 발견: {found_slot['startTime']} ~ {found_slot['endTime']} ({found_slot['durationMinutes']}분)")

        # =================================================================
        # STEP 5: 특정 멤버만 선택해서 공강 조회
        # =================================================================
        print(f"\n[STEP 5/7] 특정 멤버만 선택해서 공강 조회 (Leader만)")

        leader_only_response = requests.post(
            f"{gateway_url}/api/v1/schedules/find-free-slots",
            headers=leader['headers'],
            json={
                "groupId": group_id,
                "userIds": [leader['cognitoSub']],  # Leader만 선택
                "startDate": "2025-12-08",
                "endDate": "2025-12-08",
                "minDurationMinutes": 120,
                "workingHoursStart": "09:00",
                "workingHoursEnd": "20:00"
            },
            timeout=5
        )

        assert leader_only_response.status_code == 200
        leader_only_data = leader_only_response.json()

        assert leader_only_data["groupId"] == group_id
        assert leader_only_data["memberCount"] == 1  # Leader만
        assert len(leader_only_data["freeSlots"]) > 0

        # Leader는 12:00-20:00이 공강이어야 함 (09:00-12:00만 수업)
        leader_found_slot = None
        for slot in leader_only_data["freeSlots"]:
            if "12:00:00" in slot["startTime"] and slot["durationMinutes"] >= 120:
                leader_found_slot = slot
                break

        assert leader_found_slot is not None, "Leader의 공강이 발견되지 않음"

        print(f"  ✅ Leader 공강 발견: {leader_found_slot['startTime']} ~ {leader_found_slot['endTime']} ({leader_found_slot['durationMinutes']}분)")
        print(f"  ✅ userIds 필드 테스트 성공: cognitoSub 배열로 특정 멤버만 선택 가능")

        # =================================================================
        # STEP 6: 그룹 미팅 일정 생성
        # =================================================================
        print(f"\n[STEP 6/7] 그룹 미팅 일정 생성")

        # 그룹 카테고리 생성
        group_cat_response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=leader['headers'],
            json={"name": "Team Project", "color": "#FF6B6B", "groupId": group_id},
            timeout=5
        )
        assert group_cat_response.status_code == 201
        group_cat_id = group_cat_response.json()["categoryId"]

        # 그룹 일정 생성 (발견된 공강 시간에)
        meeting_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=leader['headers'],
            json={
                "groupId": group_id,
                "categoryId": group_cat_id,
                "title": "Team Project Kickoff Meeting",
                "description": "Discuss project requirements",
                "location": "Engineering Building Room 301",
                "startTime": found_slot["startTime"],
                "endTime": "2025-12-08T18:00:00",  # 2시간 미팅
                "source": "USER"
            },
            timeout=5
        )

        assert meeting_response.status_code == 201
        meeting_schedule_id = meeting_response.json()["scheduleId"]

        print(f"  ✅ 그룹 미팅 일정 생성 성공 (scheduleId: {meeting_schedule_id})")

        # =================================================================
        # STEP 7: 모든 멤버가 그룹 일정 조회 가능 확인
        # =================================================================
        print(f"\n[STEP 7/7] 모든 멤버 그룹 일정 조회 확인")

        # Leader 조회
        leader_schedule_check = requests.get(
            f"{gateway_url}/api/v1/schedules/{meeting_schedule_id}",
            headers=leader['headers'],
            timeout=5
        )
        assert leader_schedule_check.status_code == 200
        assert leader_schedule_check.json()["groupId"] == group_id

        # Member 조회
        member_schedule_check = requests.get(
            f"{gateway_url}/api/v1/schedules/{meeting_schedule_id}",
            headers=member['headers'],
            timeout=5
        )
        assert member_schedule_check.status_code == 200
        assert member_schedule_check.json()["title"] == "Team Project Kickoff Meeting"

        print(f"  ✅ Leader 조회 성공")
        print(f"  ✅ Member 조회 성공")

        # Cleanup
        requests.delete(f"{gateway_url}/api/v1/groups/{group_id}", headers=leader['headers'], timeout=5)

        # =================================================================
        # 최종 요약
        # =================================================================
        print("\n" + "=" * 100)
        print("[PASS] 팀 프로젝트 미팅 일정 조율 E2E 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ STEP 1: 사용자 준비 (Leader, Member)")
        print(f"✅ STEP 2: 그룹 생성 및 멤버 초대")
        print(f"✅ STEP 3: 각자 개인 일정 등록")
        print(f"✅ STEP 4: 공강 시간 조회 (전체 멤버)")
        print(f"✅ STEP 5: 특정 멤버만 선택 공강 조회 (userIds 필드 테스트)")
        print(f"✅ STEP 6: 발견된 공강 시간에 그룹 미팅 일정 생성")
        print(f"✅ STEP 7: 모든 멤버 그룹 일정 조회 가능 확인")
        print("=" * 100 + "\n")
