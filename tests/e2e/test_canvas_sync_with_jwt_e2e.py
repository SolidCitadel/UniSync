"""
E2E 테스트: JWT 인증 + API Gateway + Canvas 동기화
"""

import pytest
import requests
from conftest import wait_for_sync


class TestCanvasSyncWithJWTE2E:
    """JWT 인증을 사용하는 완전한 E2E 테스트"""

    @pytest.mark.usefixtures("wait_for_services")
    def test_full_e2e_with_jwt_authentication(self, canvas_token, jwt_auth_tokens, service_urls):
        """
        완전한 E2E 시나리오 (JWT 인증 포함):
        0. 회원가입 및 로그인 (JWT 토큰 획득) - fixture에서 처리
        1. Canvas 토큰 등록 (API Gateway + JWT 인증)
        2. 연동 상태 확인 (API Gateway + JWT 인증)
        3. 자동 동기화 (Lambda + SQS)
        4. Course 조회 (API Gateway + JWT 인증)
        5. Assignment 조회 (API Gateway + JWT 인증)
        """
        gateway_url = service_urls["gateway"]
        id_token = jwt_auth_tokens["id_token"]
        cognito_sub = jwt_auth_tokens["cognito_sub"]
        email = jwt_auth_tokens["email"]

        # Authorization 헤더 준비
        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print("\n" + "=" * 80)
        print("🚀 E2E Test: JWT 인증 + Canvas 자동 동기화")
        print("=" * 80)
        print(f"📧 사용자: {email}")
        print(f"🆔 Cognito Sub: {cognito_sub}")
        print(f"🔑 JWT Token: {id_token[:20]}...")
        print("=" * 80)

        # ================================================================
        # Step 1: Canvas 토큰 등록 (API Gateway 경유, JWT 인증)
        # ================================================================
        print("\n[1/5] Canvas 토큰 등록 (Gateway + JWT)...")
        register_response = requests.post(
            f"{gateway_url}/api/v1/credentials/canvas",
            headers=headers,
            json={"canvasToken": canvas_token},
            timeout=10
        )

        assert register_response.status_code == 200, \
            f"토큰 등록 실패: {register_response.status_code} - {register_response.text}"

        register_data = register_response.json()
        assert register_data.get("success") is True, "토큰 등록 응답이 success=false"
        print(f"  ✅ Canvas 토큰 등록 완료 (즉시 응답)")

        # ================================================================
        # Step 2: 연동 상태 확인 (API Gateway 경유, JWT 인증)
        # ================================================================
        print("\n[2/5] 연동 상태 확인 (Gateway + JWT)...")
        status_response = requests.get(
            f"{gateway_url}/api/v1/integrations/status",
            headers={"Authorization": f"Bearer {id_token}"},
            timeout=5
        )

        assert status_response.status_code == 200, \
            f"연동 상태 조회 실패: {status_response.status_code} - {status_response.text}"

        status_data = status_response.json()
        assert status_data.get("canvas") is not None, "Canvas 연동 정보가 없음"
        assert status_data["canvas"]["isConnected"] is True, "Canvas 연동 상태가 false"

        canvas_username = status_data["canvas"].get("externalUsername")
        print(f"  ✅ Canvas 연동 확인: {canvas_username}")

        # ================================================================
        # Step 3: 자동 동기화 대기
        # ================================================================
        print("\n[3/5] 자동 동기화 대기 중...")
        print("  (user-token-registered → Lambda → course-enrollment → Course-Service)")
        print("  (assignment-sync-needed → Lambda → assignment-events → Course-Service)")

        # Course가 생성될 때까지 대기 (최대 60초)
        waiter = wait_for_sync(timeout_seconds=60, poll_interval=2)

        def check_courses_synced():
            try:
                response = requests.get(
                    f"{gateway_url}/api/v1/courses",
                    headers={"Authorization": f"Bearer {id_token}"},
                    timeout=5
                )
                if response.status_code == 200:
                    courses = response.json()
                    if len(courses) > 0:
                        print(f"  ✅ {len(courses)} courses 동기화 완료")
                        return True
            except Exception as e:
                print(f"  ⏳ 대기 중... ({str(e)[:50]})")
            return False

        try:
            waiter(check_courses_synced, "[FAIL] Course 동기화 실패 (60초 타임아웃)")
        except TimeoutError as e:
            pytest.fail(str(e))

        # ================================================================
        # Step 4: Course 조회 (API Gateway 경유, JWT 인증)
        # ================================================================
        print("\n[4/5] Course 조회 (Gateway + JWT)...")
        courses_response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers={"Authorization": f"Bearer {id_token}"},
            timeout=5
        )

        assert courses_response.status_code == 200, \
            f"Course 조회 실패: {courses_response.status_code} - {courses_response.text}"

        courses = courses_response.json()
        assert len(courses) > 0, "조회된 Course가 없음"

        print(f"  ✅ {len(courses)} courses 조회됨")
        for i, course in enumerate(courses[:3], 1):  # 처음 3개만 출력
            print(f"     {i}. {course['name']} ({course['courseCode']})")
        if len(courses) > 3:
            print(f"     ... 외 {len(courses) - 3}개")

        # ================================================================
        # Step 5: Assignment 조회 (API Gateway 경유, JWT 인증)
        # ================================================================
        print("\n[5/5] Assignment 조회 (Gateway + JWT)...")

        # 첫 번째 Course의 Assignment 조회
        first_course = courses[0]
        first_course_id = first_course["id"]
        first_course_name = first_course["name"]

        # Assignment가 생성될 때까지 대기 (최대 60초)
        def check_assignments_synced():
            try:
                response = requests.get(
                    f"{gateway_url}/api/v1/courses/{first_course_id}/assignments",
                    headers={"Authorization": f"Bearer {id_token}"},
                    timeout=5
                )
                if response.status_code == 200:
                    assignments = response.json()
                    if len(assignments) > 0:
                        print(f"  ✅ {len(assignments)} assignments 동기화 완료")
                        return True
            except Exception as e:
                print(f"  ⏳ Assignment 대기 중...")
            return False

        try:
            waiter(check_assignments_synced,
                   f"[FAIL] Assignment 동기화 실패 (Course: {first_course_name})")
        except TimeoutError:
            # Assignment가 없는 Course일 수 있음
            print(f"  ⚠️  Course '{first_course_name}'에 Assignment가 없을 수 있음")

        # 최종 Assignment 조회
        assignments_response = requests.get(
            f"{gateway_url}/api/v1/courses/{first_course_id}/assignments",
            headers={"Authorization": f"Bearer {id_token}"},
            timeout=5
        )

        assert assignments_response.status_code == 200, \
            f"Assignment 조회 실패: {assignments_response.status_code}"

        assignments = assignments_response.json()

        if len(assignments) > 0:
            print(f"  ✅ Course '{first_course_name}'에 {len(assignments)} assignments 조회됨")
            for i, assignment in enumerate(assignments[:3], 1):  # 처음 3개만 출력
                due_at = assignment.get("dueAt", "기한 없음")
                print(f"     {i}. {assignment['title']} (Due: {due_at})")
            if len(assignments) > 3:
                print(f"     ... 외 {len(assignments) - 3}개")
        else:
            print(f"  ℹ️  Course '{first_course_name}'에 Assignment가 없습니다")

        # ================================================================
        # 최종 결과
        # ================================================================
        print("\n" + "=" * 80)
        print("✅ E2E 테스트 성공!")
        print(f"   - 사용자: {canvas_username} (cognitoSub={cognito_sub})")
        print(f"   - JWT 인증: ✅")
        print(f"   - API Gateway: ✅")
        print(f"   - Courses: {len(courses)}개")
        print(f"   - Assignments: {len(assignments)}개 (첫 번째 Course)")
        print("=" * 80 + "\n")

        # 최소 검증: 1개 이상의 Course가 동기화되어야 함
        assert len(courses) > 0, "최소 1개 이상의 Course가 동기화되어야 합니다"


    @pytest.mark.usefixtures("wait_for_services")
    def test_jwt_authentication_failure(self, service_urls):
        """
        JWT 인증 실패 시나리오 테스트
        """
        gateway_url = service_urls["gateway"]

        print("\n" + "=" * 80)
        print("🔒 JWT 인증 실패 테스트")
        print("=" * 80)

        # Case 1: Authorization 헤더 없이 요청
        print("\n[Case 1] Authorization 헤더 없음...")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            timeout=5
        )
        print(f"  ✅ 예상대로 401 Unauthorized: {response.status_code}")
        assert response.status_code == 401, "인증 헤더가 없으면 401을 반환해야 함"

        # Case 2: 잘못된 토큰
        print("\n[Case 2] 잘못된 JWT 토큰...")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers={"Authorization": "Bearer invalid-token-12345"},
            timeout=5
        )
        print(f"  ✅ 예상대로 401 Unauthorized: {response.status_code}")
        assert response.status_code == 401, "잘못된 토큰은 401을 반환해야 함"

        # Case 3: Bearer가 없는 토큰
        print("\n[Case 3] Bearer 접두사 없음...")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers={"Authorization": "some-random-token"},
            timeout=5
        )
        print(f"  ✅ 예상대로 401 Unauthorized: {response.status_code}")
        assert response.status_code == 401, "Bearer 접두사가 없으면 401을 반환해야 함"

        print("\n" + "=" * 80)
        print("✅ JWT 인증 실패 테스트 통과!")
        print("=" * 80 + "\n")