"""
User-Service Friend API 테스트

친구 관리 API 컴포넌트 테스트
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


class TestFriendRequestApi:
    """친구 요청 API 테스트"""

    def test_send_friend_request_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        친구 요청 발송 성공

        Given: 두 명의 사용자 (user1, user2)
        When: user1이 user2에게 친구 요청
        Then: 201 Created, PENDING 상태의 friendship 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # user1 (요청자)
        user1_tokens = jwt_auth_tokens
        user1_headers = {
            "Authorization": f"Bearer {user1_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # user2 (대상자) 생성
        user2 = create_test_user(gateway_url, "Friend")
        user2_cognito_sub = user2["cognitoSub"]

        print(f"\n[TEST] 친구 요청 발송: user1 -> user2 (cognitoSub: {user2_cognito_sub})")

        # 친구 요청 발송
        response = requests.post(
            f"{gateway_url}/api/v1/friends/requests",
            headers=user1_headers,
            json={"friendCognitoSub": user2_cognito_sub},
            timeout=5
        )

        assert response.status_code == 201, \
            f"친구 요청 실패: {response.status_code} - {response.text}"

        friendship = response.json()
        assert friendship["friend"]["cognitoSub"] == user2_cognito_sub
        assert friendship["status"] == "PENDING"
        print(f"  - 친구 요청 성공: status={friendship['status']}")

    def test_send_friend_request_to_self(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        자기 자신에게 친구 요청 시 400 에러

        Given: 사용자 본인
        When: 본인 cognitoSub로 친구 요청
        Then: 400 Bad Request, SELF_FRIENDSHIP 에러
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # 내 cognitoSub
        my_cognito_sub = jwt_auth_tokens.get("cognito_sub")

        print(f"\n[TEST] 자기 자신에게 친구 요청 (cognitoSub: {my_cognito_sub})")

        # 자기 자신에게 친구 요청
        response = requests.post(
            f"{gateway_url}/api/v1/friends/requests",
            headers=headers,
            json={"friendCognitoSub": my_cognito_sub},
            timeout=5
        )

        assert response.status_code == 400
        error = response.json()
        assert error.get("errorCode") == "SELF_FRIENDSHIP"
        print(f"  - 400 Bad Request 반환: {error.get('message')}")


class TestFriendAcceptRejectApi:
    """친구 요청 수락/거절 API 테스트"""

    def test_accept_friend_request_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        친구 요청 수락 성공

        Given: user1이 user2에게 보낸 PENDING 친구 요청
        When: user2가 요청 수락
        Then: 200 OK, 양방향 ACCEPTED 관계 생성
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # user1 (요청자)
        user1_tokens = jwt_auth_tokens
        user1_cognito_sub = jwt_auth_tokens.get("cognito_sub")
        user1_headers = {
            "Authorization": f"Bearer {user1_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # user2 (수신자) 생성
        user2 = create_test_user(gateway_url, "Accept")
        user2_cognito_sub = user2["cognitoSub"]
        user2_headers = user2["headers"]

        print(f"\n[TEST] 친구 요청 수락 플로우")

        # user1 -> user2 친구 요청
        request_response = requests.post(
            f"{gateway_url}/api/v1/friends/requests",
            headers=user1_headers,
            json={"friendCognitoSub": user2_cognito_sub},
            timeout=5
        )
        assert request_response.status_code == 201
        print(f"  - 1단계: user1이 친구 요청 발송")

        # user2가 받은 요청 조회
        pending_response = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=user2_headers,
            timeout=5
        )
        assert pending_response.status_code == 200
        pending_requests = pending_response.json()
        assert len(pending_requests) > 0

        request_id = pending_requests[0]["requestId"]
        print(f"  - 2단계: user2가 받은 요청 확인 (requestId: {request_id})")

        # user2가 요청 수락
        accept_response = requests.post(
            f"{gateway_url}/api/v1/friends/requests/{request_id}/accept",
            headers=user2_headers,
            timeout=5
        )
        assert accept_response.status_code == 200
        accept_data = accept_response.json()
        assert "수락" in accept_data.get("message", "")
        print(f"  - 3단계: user2가 요청 수락")

        # user1의 친구 목록 확인 (양방향 확인)
        user1_friends = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=user1_headers,
            timeout=5
        ).json()
        assert any(f["friend"]["cognitoSub"] == user2_cognito_sub for f in user1_friends)
        print(f"  - 4단계: user1의 친구 목록에 user2 확인")

        # user2의 친구 목록 확인
        user2_friends = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=user2_headers,
            timeout=5
        ).json()
        assert any(f["friend"]["cognitoSub"] == user1_cognito_sub for f in user2_friends)
        print(f"  - 5단계: user2의 친구 목록에 user1 확인 (양방향 완료)")

    def test_reject_friend_request_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        친구 요청 거절 성공

        Given: user1이 user2에게 보낸 PENDING 친구 요청
        When: user2가 요청 거절
        Then: 200 OK, 요청 삭제됨
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # user1, user2 준비
        user1_tokens = jwt_auth_tokens
        user1_headers = {
            "Authorization": f"Bearer {user1_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        user2 = create_test_user(gateway_url, "Reject")
        user2_cognito_sub = user2["cognitoSub"]
        user2_headers = user2["headers"]

        print(f"\n[TEST] 친구 요청 거절 플로우")

        # user1 -> user2 친구 요청
        requests.post(
            f"{gateway_url}/api/v1/friends/requests",
            headers=user1_headers,
            json={"friendCognitoSub": user2_cognito_sub},
            timeout=5
        )
        print(f"  - 1단계: user1이 친구 요청 발송")

        # user2가 받은 요청 조회
        pending_requests = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=user2_headers,
            timeout=5
        ).json()
        request_id = pending_requests[0]["requestId"]
        print(f"  - 2단계: user2가 받은 요청 확인")

        # user2가 요청 거절
        reject_response = requests.post(
            f"{gateway_url}/api/v1/friends/requests/{request_id}/reject",
            headers=user2_headers,
            timeout=5
        )
        assert reject_response.status_code == 200
        reject_data = reject_response.json()
        assert "거절" in reject_data.get("message", "")
        print(f"  - 3단계: user2가 요청 거절")

        # 거절 후 요청 목록에 없는지 확인
        after_pending = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=user2_headers,
            timeout=5
        ).json()
        assert len(after_pending) == 0
        print(f"  - 4단계: 요청이 목록에서 삭제됨")


class TestFriendListApi:
    """친구 목록 API 테스트"""

    def test_get_friends_list_empty(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        친구 목록 조회 - 비어있음

        Given: 친구가 없는 사용자
        When: GET /v1/friends
        Then: 200 OK, 빈 배열 반환
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        print(f"\n[TEST] 친구 목록 조회 (비어있음)")

        response = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        friends = response.json()
        assert isinstance(friends, list)
        assert len(friends) == 0
        print(f"  - 친구 목록 조회 성공: 0개")


class TestFriendDeleteApi:
    """친구 삭제 API 테스트"""

    def test_delete_friend_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        친구 삭제 성공 (양방향 삭제)

        Given: user1과 user2가 친구 관계 (ACCEPTED)
        When: user1이 친구 삭제
        Then: 200 OK, 양방향 관계 모두 삭제
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # user1, user2 준비 및 친구 관계 설정
        user1_tokens = jwt_auth_tokens
        user1_headers = {
            "Authorization": f"Bearer {user1_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        user2 = create_test_user(gateway_url, "Delete")
        user2_cognito_sub = user2["cognitoSub"]
        user2_headers = user2["headers"]

        print(f"\n[TEST] 친구 삭제 (양방향)")

        # 친구 관계 설정 (요청 -> 수락)
        requests.post(
            f"{gateway_url}/api/v1/friends/requests",
            headers=user1_headers,
            json={"friendCognitoSub": user2_cognito_sub},
            timeout=5
        )
        pending_requests = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=user2_headers,
            timeout=5
        ).json()
        request_id = pending_requests[0]["requestId"]
        requests.post(
            f"{gateway_url}/api/v1/friends/requests/{request_id}/accept",
            headers=user2_headers,
            timeout=5
        )
        print(f"  - 준비: user1과 user2 친구 관계 설정")

        # user1의 친구 목록에서 friendship_id 조회
        user1_friends = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=user1_headers,
            timeout=5
        ).json()
        friendship = next(f for f in user1_friends if f["friend"]["cognitoSub"] == user2_cognito_sub)
        friendship_id = friendship["friendshipId"]

        # user1이 친구 삭제
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/friends/{friendship_id}",
            headers=user1_headers,
            timeout=5
        )
        assert delete_response.status_code == 200
        delete_data = delete_response.json()
        assert "삭제" in delete_data.get("message", "")
        print(f"  - user1이 친구 삭제")

        # user1의 친구 목록에 없는지 확인
        user1_friends_after = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=user1_headers,
            timeout=5
        ).json()
        assert len(user1_friends_after) == 0
        print(f"  - user1의 친구 목록에서 삭제됨")

        # user2의 친구 목록에도 없는지 확인 (양방향 삭제)
        user2_friends_after = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=user2_headers,
            timeout=5
        ).json()
        assert len(user2_friends_after) == 0
        print(f"  - user2의 친구 목록에서도 삭제됨 (양방향 삭제 완료)")


class TestBlockUserApi:
    """사용자 차단 API 테스트"""

    def test_block_user_success(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        사용자 차단 성공

        Given: user1과 user2
        When: user1이 user2 차단
        Then: 200 OK, BLOCKED 관계 생성
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        user1_tokens = jwt_auth_tokens
        user1_headers = {
            "Authorization": f"Bearer {user1_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # user2 생성
        user2 = create_test_user(gateway_url, "Block")
        user2_cognito_sub = user2["cognitoSub"]

        print(f"\n[TEST] 사용자 차단")

        # user1이 user2 차단
        block_response = requests.post(
            f"{gateway_url}/api/v1/friends/{user2_cognito_sub}/block",
            headers=user1_headers,
            timeout=5
        )

        assert block_response.status_code == 200
        block_data = block_response.json()
        assert "차단" in block_data.get("message", "")
        print(f"  - user2 차단 성공")

    def test_search_users_excludes_blocked(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        사용자 검색 - 차단한 사용자 제외

        Given: user1이 user2를 차단
        When: user1이 사용자 검색
        Then: 200 OK, user2는 검색 결과에서 제외
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        user1_tokens = jwt_auth_tokens
        user1_headers = {
            "Authorization": f"Bearer {user1_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # user2, user3 생성
        user2 = create_test_user(gateway_url, "SearchBlock")
        user2_cognito_sub = user2["cognitoSub"]

        create_test_user(gateway_url, "SearchNormal")

        print(f"\n[TEST] 차단한 사용자는 검색 결과에서 제외")

        # user1이 user2 차단
        requests.post(
            f"{gateway_url}/api/v1/friends/{user2_cognito_sub}/block",
            headers=user1_headers,
            timeout=5
        )
        print(f"  - user2 차단")

        # 사용자 검색
        search_response = requests.get(
            f"{gateway_url}/api/v1/friends/search?query=user&limit=20",
            headers=user1_headers,
            timeout=5
        )
        assert search_response.status_code == 200
        search_results = search_response.json()

        # user2는 검색 결과에 없어야 함
        blocked_user_cognito_subs = [u["cognitoSub"] for u in search_results]
        assert user2_cognito_sub not in blocked_user_cognito_subs
        print(f"  - 차단한 사용자(user2)가 검색 결과에서 제외됨")
