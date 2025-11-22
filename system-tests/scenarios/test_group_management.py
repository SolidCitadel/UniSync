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
