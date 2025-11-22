"""
User-Service Group API 테스트

그룹 관리 API 컴포넌트 테스트
cognitoSub 기반 사용자 식별
"""

import pytest
import requests
import uuid


def create_test_user(gateway_url, name_prefix):
    """Helper function to create a test user and return user data"""
    email = f"{name_prefix.lower()}-{uuid.uuid4().hex[:8]}@unisync.com"
    signup_response = requests.post(
        f"{gateway_url}/api/v1/auth/signup",
        json={
            "email": email,
            "password": "TestPassword123!",
            "name": f"{name_prefix} User"
        },
        timeout=10
    )
    assert signup_response.status_code == 201
    signup_data = signup_response.json()

    return {
        "cognitoSub": signup_data.get("cognitoSub"),
        "email": email,
        "tokens": signup_data,
        "headers": {
            "Authorization": f"Bearer {signup_data['idToken']}",
            "Content-Type": "application/json"
        }
    }


class TestGroupCreateApi:
    """그룹 생성 API 테스트"""

    def test_create_group_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 생성 성공

        Given: 인증된 사용자
        When: POST /v1/groups (그룹 생성)
        Then: 201 Created, 생성자는 자동으로 OWNER
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 그룹 생성")

        group_data = {
            "name": "테스트 그룹",
            "description": "시스템 테스트용 그룹"
        }

        response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            json=group_data,
            timeout=5
        )

        assert response.status_code == 201, \
            f"그룹 생성 실패: {response.status_code} - {response.text}"

        group = response.json()
        assert group["name"] == "테스트 그룹"
        assert group["description"] == "시스템 테스트용 그룹"
        assert group["myRole"] == "OWNER"
        assert group["memberCount"] == 1
        print(f"  - 그룹 생성 성공: ID={group['groupId']}, Role={group['myRole']}")


class TestGroupListApi:
    """그룹 목록 조회 API 테스트"""

    def test_get_my_groups_empty(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        내 그룹 목록 조회 - 비어있음

        Given: 그룹이 없는 사용자
        When: GET /v1/groups
        Then: 200 OK, 빈 배열 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 내 그룹 목록 조회 (비어있음)")

        response = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        groups = response.json()
        assert isinstance(groups, list)
        assert len(groups) == 0
        print(f"  - 그룹 목록 조회 성공: 0개")

    def test_get_my_groups_with_data(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        내 그룹 목록 조회 - 데이터 있음

        Given: 사용자가 속한 그룹이 있음
        When: GET /v1/groups
        Then: 200 OK, 그룹 목록 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 내 그룹 목록 조회 (데이터 있음)")

        # 그룹 생성
        requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            json={"name": "그룹1", "description": "테스트"},
            timeout=5
        )
        requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            json={"name": "그룹2", "description": "테스트"},
            timeout=5
        )

        # 그룹 목록 조회
        response = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        groups = response.json()
        assert len(groups) == 2
        print(f"  - 그룹 목록 조회 성공: {len(groups)}개")


class TestGroupDetailApi:
    """그룹 상세 조회 API 테스트"""

    def test_get_group_details_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 상세 조회 성공

        Given: 내가 속한 그룹
        When: GET /v1/groups/{groupId}
        Then: 200 OK, 그룹 상세 정보 + 멤버 목록 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 그룹 상세 조회")

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            json={"name": "상세조회 테스트 그룹", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # 그룹 상세 조회
        response = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200, \
            f"그룹 상세 조회 실패: {response.status_code} - {response.text}"

        group_detail = response.json()
        assert group_detail["groupId"] == group_id
        assert group_detail["name"] == "상세조회 테스트 그룹"
        assert "members" in group_detail
        assert len(group_detail["members"]) == 1  # OWNER만
        assert group_detail["members"][0]["role"] == "OWNER"
        print(f"  - 그룹 상세 조회 성공: {len(group_detail['members'])} 멤버")

    def test_get_group_details_not_member(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 상세 조회 실패 - 멤버가 아님

        Given: 내가 속하지 않은 그룹
        When: GET /v1/groups/{groupId}
        Then: 404 Not Found, MEMBER_NOT_FOUND
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # user1이 그룹 생성
        user1_tokens = jwt_auth_tokens
        user1_headers = {
            "Authorization": f"Bearer {user1_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=user1_headers,
            json={"name": "Private Group", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # user2 생성
        user2 = create_test_user(gateway_url, "NotMember")
        user2_headers = user2["headers"]

        print(f"\n[TEST] 멤버가 아닌 사용자의 그룹 상세 조회")

        # user2가 user1의 그룹 조회 시도
        response = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=user2_headers,
            timeout=5
        )

        assert response.status_code == 404
        error = response.json()
        assert error.get("errorCode") == "MEMBER_NOT_FOUND"
        print(f"  - 404 Not Found 반환: 멤버가 아닌 사용자는 조회 불가")


class TestGroupUpdateApi:
    """그룹 수정 API 테스트"""

    def test_update_group_by_owner_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 수정 성공 (OWNER)

        Given: 내가 OWNER인 그룹
        When: PUT /v1/groups/{groupId} (이름, 설명 수정)
        Then: 200 OK, 수정된 그룹 정보 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 그룹 수정 (OWNER)")

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            json={"name": "원본 이름", "description": "원본 설명"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # 그룹 수정
        update_data = {
            "name": "수정된 이름",
            "description": "수정된 설명"
        }
        response = requests.put(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=headers,
            json=update_data,
            timeout=5
        )

        assert response.status_code == 200, \
            f"그룹 수정 실패: {response.status_code} - {response.text}"

        updated_group = response.json()
        assert updated_group["name"] == "수정된 이름"
        assert updated_group["description"] == "수정된 설명"
        print(f"  - 그룹 수정 성공: {updated_group['name']}")

    def test_update_group_by_non_owner_fails(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 수정 실패 (OWNER가 아님)

        Given: 내가 ADMIN/MEMBER인 그룹
        When: PUT /v1/groups/{groupId}
        Then: 403 Forbidden, INSUFFICIENT_PERMISSION
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # owner (user1) 그룹 생성
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "Owner Group", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # admin (user2) 생성 및 초대
        admin = create_test_user(gateway_url, "Admin")
        admin_cognito_sub = admin["cognitoSub"]
        admin_headers = admin["headers"]

        # ADMIN으로 초대
        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": admin_cognito_sub, "role": "ADMIN"},
            timeout=5
        )

        print(f"\n[TEST] 그룹 수정 실패 (ADMIN은 수정 불가)")

        # ADMIN이 그룹 수정 시도
        response = requests.put(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=admin_headers,
            json={"name": "ADMIN이 수정", "description": "수정 시도"},
            timeout=5
        )

        assert response.status_code == 403
        error = response.json()
        assert error.get("errorCode") == "INSUFFICIENT_PERMISSION"
        print(f"  - 403 Forbidden 반환: ADMIN은 그룹 수정 불가")


class TestGroupDeleteApi:
    """그룹 삭제 API 테스트"""

    def test_delete_group_by_owner_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 삭제 성공 (OWNER)

        Given: 내가 OWNER인 그룹
        When: DELETE /v1/groups/{groupId}
        Then: 200 OK, 그룹 삭제 (멤버도 CASCADE 삭제)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 그룹 삭제 (OWNER)")

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            json={"name": "삭제 테스트 그룹", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # 그룹 삭제
        response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        delete_data = response.json()
        assert "삭제" in delete_data.get("message", "")
        print(f"  - 그룹 삭제 성공")

        # 삭제 후 목록에 없는지 확인
        list_response = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=headers,
            timeout=5
        )
        groups = list_response.json()
        assert isinstance(groups, list), f"Expected list, got {type(groups)}: {groups}"
        assert not any(g["groupId"] == group_id for g in groups)
        print(f"  - 삭제된 그룹이 목록에 없음")


class TestMemberInviteApi:
    """멤버 초대 API 테스트"""

    def test_invite_member_by_owner_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        멤버 초대 성공 (OWNER)

        Given: 내가 OWNER인 그룹
        When: POST /v1/groups/{groupId}/members (새 멤버 초대)
        Then: 201 Created, 멤버 추가됨
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "초대 테스트 그룹", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # 초대할 사용자 생성
        member = create_test_user(gateway_url, "Member")
        member_cognito_sub = member["cognitoSub"]

        print(f"\n[TEST] 멤버 초대 (OWNER가 MEMBER 초대)")

        # 멤버 초대
        response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )

        assert response.status_code == 201, \
            f"멤버 초대 실패: {response.status_code} - {response.text}"

        member_data = response.json()
        assert member_data["user"]["cognitoSub"] == member_cognito_sub
        assert member_data["role"] == "MEMBER"
        print(f"  - 멤버 초대 성공: cognitoSub={member_cognito_sub}, role=MEMBER")

        # 그룹 멤버 수 확인
        group_detail = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}",
            headers=owner_headers,
            timeout=5
        ).json()
        assert len(group_detail["members"]) == 2  # OWNER + MEMBER
        print(f"  - 그룹 멤버 수: {len(group_detail['members'])}명")

    def test_invite_member_by_admin_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        멤버 초대 성공 (ADMIN)

        Given: 내가 ADMIN인 그룹
        When: POST /v1/groups/{groupId}/members
        Then: 201 Created, ADMIN도 멤버 초대 가능
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "ADMIN 초대 테스트", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # ADMIN 사용자 생성 및 초대
        admin = create_test_user(gateway_url, "AdminInvite")
        admin_cognito_sub = admin["cognitoSub"]
        admin_headers = admin["headers"]

        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": admin_cognito_sub, "role": "ADMIN"},
            timeout=5
        )

        # 새 멤버 생성
        new_member = create_test_user(gateway_url, "NewMember")
        new_member_cognito_sub = new_member["cognitoSub"]

        print(f"\n[TEST] 멤버 초대 (ADMIN이 초대)")

        # ADMIN이 새 멤버 초대
        response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=admin_headers,
            json={"userCognitoSub": new_member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )

        assert response.status_code == 201
        print(f"  - ADMIN이 멤버 초대 성공")

    def test_invite_member_by_member_fails(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        멤버 초대 실패 (MEMBER는 초대 불가)

        Given: 내가 MEMBER인 그룹
        When: POST /v1/groups/{groupId}/members
        Then: 403 Forbidden, INSUFFICIENT_PERMISSION
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "Permission Test", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # MEMBER 사용자 생성 및 초대
        member = create_test_user(gateway_url, "MemberNoPerm")
        member_cognito_sub = member["cognitoSub"]
        member_headers = member["headers"]

        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )

        # 새 사용자 생성
        new_user = create_test_user(gateway_url, "NewUser")
        new_user_cognito_sub = new_user["cognitoSub"]

        print(f"\n[TEST] 멤버 초대 실패 (MEMBER는 초대 불가)")

        # MEMBER가 초대 시도
        response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=member_headers,
            json={"userCognitoSub": new_user_cognito_sub, "role": "MEMBER"},
            timeout=5
        )

        assert response.status_code == 403
        error = response.json()
        assert error.get("errorCode") == "INSUFFICIENT_PERMISSION"
        print(f"  - 403 Forbidden 반환: MEMBER는 초대 불가")


class TestMemberRoleApi:
    """멤버 역할 변경 API 테스트"""

    def test_update_member_role_by_owner_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        멤버 역할 변경 성공 (OWNER)

        Given: 내가 OWNER인 그룹, MEMBER가 존재
        When: PATCH /v1/groups/{groupId}/members/{memberId}/role (MEMBER -> ADMIN)
        Then: 200 OK, 역할 변경됨
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "역할 변경 테스트", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # MEMBER 추가
        member = create_test_user(gateway_url, "RoleChange")
        member_cognito_sub = member["cognitoSub"]

        invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )
        membership_id = invite_response.json()["memberId"]

        print(f"\n[TEST] 멤버 역할 변경 (MEMBER -> ADMIN)")

        # 역할 변경
        response = requests.patch(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{membership_id}/role",
            headers=owner_headers,
            json={"role": "ADMIN"},
            timeout=5
        )

        assert response.status_code == 200, \
            f"역할 변경 실패: {response.status_code} - {response.text}"

        updated_member = response.json()
        assert updated_member["role"] == "ADMIN"
        print(f"  - 역할 변경 성공: MEMBER -> ADMIN")

    def test_update_member_role_to_owner_fails(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        OWNER로 역할 변경 불가

        Given: 멤버 역할 변경 시도
        When: role=OWNER로 변경 시도
        Then: 400 Bad Request
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 + 멤버 준비
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "OWNER 변경 불가 테스트", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        member = create_test_user(gateway_url, "NoOwner")
        member_cognito_sub = member["cognitoSub"]

        invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )
        membership_id = invite_response.json()["memberId"]

        print(f"\n[TEST] OWNER로 역할 변경 불가")

        # OWNER로 변경 시도
        response = requests.patch(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{membership_id}/role",
            headers=owner_headers,
            json={"role": "OWNER"},
            timeout=5
        )

        assert response.status_code == 400
        print(f"  - 400 Bad Request 반환: OWNER로 변경 불가")


class TestMemberRemoveApi:
    """멤버 제거 API 테스트"""

    def test_remove_member_by_owner_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        멤버 제거 성공 (OWNER)

        Given: 내가 OWNER인 그룹, MEMBER/ADMIN 존재
        When: DELETE /v1/groups/{groupId}/members/{memberId}
        Then: 200 OK, OWNER는 모든 멤버 제거 가능
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "멤버 제거 테스트", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # MEMBER 추가
        member = create_test_user(gateway_url, "Remove")
        member_cognito_sub = member["cognitoSub"]

        invite_response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )
        membership_id = invite_response.json()["memberId"]

        print(f"\n[TEST] 멤버 제거 (OWNER가 MEMBER 제거)")

        # 멤버 제거
        response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{membership_id}",
            headers=owner_headers,
            timeout=5
        )

        assert response.status_code == 200
        remove_data = response.json()
        assert "제거" in remove_data.get("message", "")
        print(f"  - 멤버 제거 성공")

        # 멤버 목록에 없는지 확인
        members = requests.get(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            timeout=5
        ).json()
        assert len(members) == 1  # OWNER만
        print(f"  - 멤버 목록에서 제거됨")

    def test_remove_member_by_admin_restricted(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        ADMIN은 MEMBER만 제거 가능

        Given: 내가 ADMIN인 그룹
        When: ADMIN이 다른 ADMIN 제거 시도
        Then: 403 Forbidden
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "ADMIN 권한 테스트", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # ADMIN1, ADMIN2 생성
        admin1 = create_test_user(gateway_url, "Admin1")
        admin1_cognito_sub = admin1["cognitoSub"]
        admin1_headers = admin1["headers"]

        admin2 = create_test_user(gateway_url, "Admin2")
        admin2_cognito_sub = admin2["cognitoSub"]

        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": admin1_cognito_sub, "role": "ADMIN"},
            timeout=5
        )
        admin2_invite = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": admin2_cognito_sub, "role": "ADMIN"},
            timeout=5
        )
        admin2_membership_id = admin2_invite.json()["memberId"]

        print(f"\n[TEST] ADMIN이 다른 ADMIN 제거 시도 (권한 부족)")

        # ADMIN1이 ADMIN2 제거 시도
        response = requests.delete(
            f"{gateway_url}/api/v1/groups/{group_id}/members/{admin2_membership_id}",
            headers=admin1_headers,
            timeout=5
        )

        assert response.status_code == 403
        error = response.json()
        assert error.get("errorCode") == "INSUFFICIENT_PERMISSION"
        print(f"  - 403 Forbidden 반환: ADMIN은 MEMBER만 제거 가능")


class TestGroupLeaveApi:
    """그룹 탈퇴 API 테스트"""

    def test_leave_group_as_member_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        그룹 탈퇴 성공 (MEMBER)

        Given: 내가 MEMBER인 그룹
        When: POST /v1/groups/{groupId}/leave
        Then: 200 OK, 탈퇴 성공
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "탈퇴 테스트 그룹", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # MEMBER 추가
        member = create_test_user(gateway_url, "Leave")
        member_cognito_sub = member["cognitoSub"]
        member_headers = member["headers"]

        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )

        print(f"\n[TEST] 그룹 탈퇴 (MEMBER)")

        # MEMBER 탈퇴
        response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/leave",
            headers=member_headers,
            timeout=5
        )

        assert response.status_code == 200
        leave_data = response.json()
        assert "탈퇴" in leave_data.get("message", "")
        print(f"  - 그룹 탈퇴 성공")

        # 탈퇴 후 내 그룹 목록에 없는지 확인
        my_groups = requests.get(
            f"{gateway_url}/api/v1/groups",
            headers=member_headers,
            timeout=5
        ).json()
        assert not any(g["groupId"] == group_id for g in my_groups)
        print(f"  - 탈퇴한 그룹이 목록에 없음")

    def test_leave_group_as_owner_fails(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        OWNER 탈퇴 실패 (소유권 이전 필요)

        Given: 내가 OWNER인 그룹, 다른 멤버 존재
        When: POST /v1/groups/{groupId}/leave
        Then: 400 Bad Request (소유권 이전 필요)
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        owner_headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 그룹 생성
        create_response = requests.post(
            f"{gateway_url}/api/v1/groups",
            headers=owner_headers,
            json={"name": "OWNER 탈퇴 테스트", "description": "테스트"},
            timeout=5
        )
        group_id = create_response.json()["groupId"]

        # MEMBER 추가
        member = create_test_user(gateway_url, "OwnerLeave")
        member_cognito_sub = member["cognitoSub"]

        requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/members",
            headers=owner_headers,
            json={"userCognitoSub": member_cognito_sub, "role": "MEMBER"},
            timeout=5
        )

        print(f"\n[TEST] OWNER 탈퇴 실패 (소유권 이전 필요)")

        # OWNER가 탈퇴 시도
        response = requests.post(
            f"{gateway_url}/api/v1/groups/{group_id}/leave",
            headers=owner_headers,
            timeout=5
        )

        assert response.status_code == 400
        error = response.json()
        assert "ownership" in error.get("message", "").lower()
        print(f"  - 400 Bad Request 반환: OWNER는 소유권 이전 후 탈퇴 가능")
