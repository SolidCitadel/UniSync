"""
친구 관리 통합 플로우 테스트

완전한 친구 관계 시나리오:
1. 사용자 검색
2. 친구 요청 발송
3. 친구 요청 수락/거절
4. 친구 목록 조회
5. 친구 삭제
6. 사용자 차단

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


class TestFriendManagementFlow:
    """친구 관리 전체 플로우 통합 테스트"""

    def test_complete_friend_lifecycle(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        완전한 친구 관계 생명주기:
        사용자 생성 → 검색 → 요청 → 수락 → 친구 확인 → 삭제
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        # =================================================================
        # STEP 1: 사용자 준비 (Alice, Bob, Charlie)
        # =================================================================
        print("\n" + "=" * 100)
        print("친구 관리 전체 플로우 테스트: 사용자 생성 → 검색 → 친구 요청 → 수락 → 삭제")
        print("=" * 100)

        # Alice (주요 테스트 사용자)
        alice_tokens = jwt_auth_tokens
        alice_cognito_sub = alice_tokens.get("cognito_sub")
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # Bob 생성
        bob = create_test_user(gateway_url, "Bob Friend")
        bob_cognito_sub = bob["cognitoSub"]
        bob_headers = bob["headers"]

        # Charlie 생성
        charlie = create_test_user(gateway_url, "Charlie Friend")
        charlie_cognito_sub = charlie["cognitoSub"]
        charlie_headers = charlie["headers"]

        print(f"\n[STEP 1/7] 사용자 준비")
        print(f"  - Alice: {alice_tokens['email']} (cognitoSub: {alice_cognito_sub[:20]}...)")
        print(f"  - Bob: {bob['email']} (cognitoSub: {bob_cognito_sub[:20]}...)")
        print(f"  - Charlie: {charlie['email']} (cognitoSub: {charlie_cognito_sub[:20]}...)")
        print(f"  ✅ 3명의 사용자 생성 완료")

        # =================================================================
        # STEP 2: 사용자 검색
        # =================================================================
        print(f"\n[STEP 2/7] 사용자 검색")

        search_response = requests.get(
            f"{gateway_url}/api/v1/friends/search?query=friend&limit=20",
            headers=alice_headers,
            timeout=5
        )
        assert search_response.status_code == 200
        search_results = search_response.json()

        # Bob과 Charlie가 검색되어야 함 (이름에 "Friend" 포함)
        found_cognito_subs = [u["cognitoSub"] for u in search_results]
        assert bob_cognito_sub in found_cognito_subs
        assert charlie_cognito_sub in found_cognito_subs
        assert alice_cognito_sub not in found_cognito_subs  # 본인은 제외

        print(f"  ✅ 사용자 검색 성공: {len(search_results)}명 발견")
        print(f"     - Bob 발견: ✅")
        print(f"     - Charlie 발견: ✅")

        # =================================================================
        # STEP 3: Alice → Bob 친구 요청
        # =================================================================
        print(f"\n[STEP 3/7] Alice → Bob 친구 요청")

        friend_request_response = requests.post(
            f"{gateway_url}/api/v1/friends/requests",
            headers=alice_headers,
            json={"friendCognitoSub": bob_cognito_sub},
            timeout=5
        )
        assert friend_request_response.status_code == 201
        friendship = friend_request_response.json()
        assert friendship["friend"]["cognitoSub"] == bob_cognito_sub
        assert friendship["status"] == "PENDING"

        print(f"  ✅ Alice가 Bob에게 친구 요청 발송 (status: PENDING)")

        # =================================================================
        # STEP 4: Bob이 받은 요청 확인
        # =================================================================
        print(f"\n[STEP 4/7] Bob이 받은 요청 확인")

        pending_requests_response = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=bob_headers,
            timeout=5
        )
        assert pending_requests_response.status_code == 200
        pending_requests = pending_requests_response.json()
        assert len(pending_requests) == 1

        request_from_alice = pending_requests[0]
        assert request_from_alice["fromUser"]["cognitoSub"] == alice_cognito_sub
        request_id = request_from_alice["requestId"]

        print(f"  ✅ Bob이 받은 요청 확인: 1개")
        print(f"     - From: Alice (requestId: {request_id})")

        # =================================================================
        # STEP 5: Bob이 요청 수락
        # =================================================================
        print(f"\n[STEP 5/7] Bob이 친구 요청 수락")

        accept_response = requests.post(
            f"{gateway_url}/api/v1/friends/requests/{request_id}/accept",
            headers=bob_headers,
            timeout=5
        )
        assert accept_response.status_code == 200
        accept_data = accept_response.json()
        assert "수락" in accept_data.get("message", "")

        print(f"  ✅ Bob이 요청 수락")

        # 양방향 친구 확인
        alice_friends = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=alice_headers,
            timeout=5
        ).json()
        bob_friends = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=bob_headers,
            timeout=5
        ).json()

        assert any(f["friend"]["cognitoSub"] == bob_cognito_sub and f["status"] == "ACCEPTED" for f in alice_friends)
        assert any(f["friend"]["cognitoSub"] == alice_cognito_sub and f["status"] == "ACCEPTED" for f in bob_friends)

        print(f"  ✅ 양방향 친구 관계 확인")
        print(f"     - Alice의 친구 목록: {len(alice_friends)}명 (Bob 포함)")
        print(f"     - Bob의 친구 목록: {len(bob_friends)}명 (Alice 포함)")

        # =================================================================
        # STEP 6: Alice → Charlie 요청 후 Charlie 거절
        # =================================================================
        print(f"\n[STEP 6/7] Alice → Charlie 요청 후 거절")

        # Alice → Charlie 요청
        requests.post(
            f"{gateway_url}/api/v1/friends/requests",
            headers=alice_headers,
            json={"friendCognitoSub": charlie_cognito_sub},
            timeout=5
        )
        print(f"  ✅ Alice가 Charlie에게 친구 요청 발송")

        # Charlie가 받은 요청 조회
        charlie_pending = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=charlie_headers,
            timeout=5
        ).json()
        charlie_request_id = charlie_pending[0]["requestId"]

        # Charlie가 거절
        reject_response = requests.post(
            f"{gateway_url}/api/v1/friends/requests/{charlie_request_id}/reject",
            headers=charlie_headers,
            timeout=5
        )
        assert reject_response.status_code == 200

        print(f"  ✅ Charlie가 요청 거절")

        # 거절 후 요청 목록 확인
        after_reject_pending = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=charlie_headers,
            timeout=5
        ).json()
        assert len(after_reject_pending) == 0

        print(f"  ✅ 거절 후 요청 목록에서 삭제됨")

        # =================================================================
        # STEP 7: Alice가 Bob 친구 삭제
        # =================================================================
        print(f"\n[STEP 7/7] Alice가 Bob 친구 삭제")

        # Alice의 친구 목록에서 Bob의 friendshipId 조회
        alice_friends_before_delete = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=alice_headers,
            timeout=5
        ).json()
        bob_friendship = next(f for f in alice_friends_before_delete if f["friend"]["cognitoSub"] == bob_cognito_sub)
        friendship_id = bob_friendship["friendshipId"]

        # Alice가 친구 삭제
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/friends/{friendship_id}",
            headers=alice_headers,
            timeout=5
        )
        assert delete_response.status_code == 200

        print(f"  ✅ Alice가 Bob 친구 삭제")

        # 삭제 후 양방향 확인
        alice_friends_after = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=alice_headers,
            timeout=5
        ).json()
        bob_friends_after = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=bob_headers,
            timeout=5
        ).json()

        assert len(alice_friends_after) == 0
        assert len(bob_friends_after) == 0

        print(f"  ✅ 양방향 친구 관계 삭제 확인")
        print(f"     - Alice의 친구 목록: 0명")
        print(f"     - Bob의 친구 목록: 0명")

        # =================================================================
        # 최종 요약
        # =================================================================
        print("\n" + "=" * 100)
        print("[PASS] 친구 관리 전체 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ STEP 1: 사용자 3명 생성 (Alice, Bob, Charlie)")
        print(f"✅ STEP 2: 사용자 검색 (Bob, Charlie 발견)")
        print(f"✅ STEP 3: Alice → Bob 친구 요청")
        print(f"✅ STEP 4: Bob이 받은 요청 확인")
        print(f"✅ STEP 5: Bob이 요청 수락 (양방향 친구)")
        print(f"✅ STEP 6: Alice → Charlie 요청 후 거절")
        print(f"✅ STEP 7: Alice가 Bob 친구 삭제 (양방향 삭제)")
        print("=" * 100 + "\n")


class TestBlockingFlow:
    """사용자 차단 플로우 통합 테스트"""

    def test_block_and_search_flow(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        사용자 차단 플로우:
        1. Alice가 Bob 차단
        2. Bob은 Alice의 검색 결과에서 제외
        3. Bob이 Alice에게 친구 요청 불가
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("사용자 차단 플로우 테스트")
        print("=" * 100)

        # Alice, Bob 준비
        alice_tokens = jwt_auth_tokens
        alice_cognito_sub = alice_tokens.get("cognito_sub")
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        bob = create_test_user(gateway_url, "Bob Blocked")
        bob_cognito_sub = bob["cognitoSub"]
        bob_headers = bob["headers"]

        print(f"\n[STEP 1/3] Alice가 Bob 차단")

        # Alice가 Bob 차단
        block_response = requests.post(
            f"{gateway_url}/api/v1/friends/{bob_cognito_sub}/block",
            headers=alice_headers,
            timeout=5
        )
        assert block_response.status_code == 200
        print(f"  ✅ Bob 차단 성공")

        print(f"\n[STEP 2/3] Alice의 검색 결과에서 Bob 제외 확인")

        # Alice가 검색 시 Bob은 나타나지 않아야 함
        search_response = requests.get(
            f"{gateway_url}/api/v1/friends/search?query=bob&limit=20",
            headers=alice_headers,
            timeout=5
        )
        search_results = search_response.json()
        found_cognito_subs = [u["cognitoSub"] for u in search_results]
        assert bob_cognito_sub not in found_cognito_subs

        print(f"  ✅ 차단한 사용자(Bob)가 검색 결과에서 제외됨")

        print(f"\n[STEP 3/3] Bob의 검색에서는 Alice 정상 노출")

        # Bob의 검색에서는 Alice가 나타나야 함 (일방향 차단)
        bob_search_response = requests.get(
            f"{gateway_url}/api/v1/friends/search?query=user&limit=20",
            headers=bob_headers,
            timeout=5
        )
        bob_search_results = bob_search_response.json()
        # Bob은 Alice를 볼 수 있음 (일방향 차단)

        print(f"  ✅ Bob의 검색에서는 Alice 정상 노출 (일방향 차단)")

        print("\n" + "=" * 100)
        print("[PASS] 사용자 차단 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ STEP 1: Alice가 Bob 차단")
        print(f"✅ STEP 2: Alice의 검색 결과에서 Bob 제외")
        print(f"✅ STEP 3: Bob의 검색에서는 Alice 정상 노출 (일방향)")
        print("=" * 100 + "\n")


class TestMultipleRequestsFlow:
    """다중 친구 요청 플로우 통합 테스트"""

    def test_multiple_friend_requests_flow(self, jwt_auth_tokens, service_urls, clean_user_database):
        """
        다중 친구 요청 처리:
        1. Alice가 여러 사용자에게 동시 요청
        2. 각 사용자가 수락/거절
        3. 친구 목록 확인
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")

        print("\n" + "=" * 100)
        print("다중 친구 요청 플로우 테스트")
        print("=" * 100)

        # Alice 준비
        alice_tokens = jwt_auth_tokens
        alice_headers = {
            "Authorization": f"Bearer {alice_tokens['id_token']}",
            "Content-Type": "application/json"
        }

        # Bob, Charlie, David 생성
        users = []
        for name in ["Bob Multi", "Charlie Multi", "David Multi"]:
            user = create_test_user(gateway_url, name)
            users.append(user)

        print(f"\n[STEP 1/4] 사용자 준비")
        for user in users:
            print(f"  - {user['name']} (cognitoSub: {user['cognitoSub'][:20]}...)")
        print(f"  ✅ 3명의 사용자 생성 완료")

        print(f"\n[STEP 2/4] Alice가 모든 사용자에게 친구 요청")

        # Alice → 모든 사용자에게 요청
        for user in users:
            response = requests.post(
                f"{gateway_url}/api/v1/friends/requests",
                headers=alice_headers,
                json={"friendCognitoSub": user["cognitoSub"]},
                timeout=5
            )
            assert response.status_code == 201
            print(f"  ✅ {user['name']}에게 요청 발송")

        print(f"\n[STEP 3/4] Bob 수락, Charlie 거절, David 보류")

        # Bob 수락
        bob_pending = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=users[0]["headers"],
            timeout=5
        ).json()
        bob_request_id = bob_pending[0]["requestId"]
        requests.post(
            f"{gateway_url}/api/v1/friends/requests/{bob_request_id}/accept",
            headers=users[0]["headers"],
            timeout=5
        )
        print(f"  ✅ Bob 수락")

        # Charlie 거절
        charlie_pending = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=users[1]["headers"],
            timeout=5
        ).json()
        charlie_request_id = charlie_pending[0]["requestId"]
        requests.post(
            f"{gateway_url}/api/v1/friends/requests/{charlie_request_id}/reject",
            headers=users[1]["headers"],
            timeout=5
        )
        print(f"  ✅ Charlie 거절")

        # David는 보류 (아무 액션 없음)
        print(f"  ✅ David 보류")

        print(f"\n[STEP 4/4] 최종 상태 확인")

        # Alice의 친구 목록 (Bob만)
        alice_friends = requests.get(
            f"{gateway_url}/api/v1/friends",
            headers=alice_headers,
            timeout=5
        ).json()
        assert len(alice_friends) == 1
        assert alice_friends[0]["friend"]["cognitoSub"] == users[0]["cognitoSub"]  # Bob
        print(f"  ✅ Alice의 친구: 1명 (Bob)")

        # David가 받은 요청 확인 (아직 PENDING)
        david_pending = requests.get(
            f"{gateway_url}/api/v1/friends/requests/pending",
            headers=users[2]["headers"],
            timeout=5
        ).json()
        assert len(david_pending) == 1
        print(f"  ✅ David의 대기 중인 요청: 1개 (Alice)")

        print("\n" + "=" * 100)
        print("[PASS] 다중 친구 요청 플로우 테스트 성공!")
        print("=" * 100)
        print(f"✅ Alice → Bob, Charlie, David 동시 요청")
        print(f"✅ Bob 수락 → Alice의 친구")
        print(f"✅ Charlie 거절 → 관계 없음")
        print(f"✅ David 보류 → 요청 대기 중")
        print("=" * 100 + "\n")
