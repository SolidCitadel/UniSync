"""
Course-Service Course API 테스트

과목 조회 API 검증
"""

import pytest
import requests


class TestCourseApi:
    """Course API 테스트"""

    def test_get_courses_empty(self, jwt_auth_tokens, service_urls):
        """
        과목 목록 조회 (빈 목록)

        Given: 동기화되지 않은 사용자
        When: GET /api/v1/courses 호출
        Then: 200 OK, 빈 배열
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 과목 목록 조회 (빈 목록)")
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

        print(f"  ✅ 과목 목록 조회 성공 ({len(data)}개)")

    def test_get_course_by_id_not_found(self, jwt_auth_tokens, service_urls):
        """
        존재하지 않는 과목 조회

        Given: 존재하지 않는 courseId
        When: GET /api/v1/courses/{courseId} 호출
        Then: 404 Not Found
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 존재하지 않는 과목 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/courses/99999",
            headers=headers,
            timeout=5
        )

        assert response.status_code == 404
        print(f"  ✅ 예상대로 404 Not Found")


class TestAssignmentApi:
    """Assignment API 테스트"""

    def test_get_assignments_by_course_not_found(self, jwt_auth_tokens, service_urls):
        """
        존재하지 않는 과목의 과제 조회

        Given: 존재하지 않는 courseId
        When: GET /api/v1/courses/{courseId}/assignments 호출
        Then: 404 Not Found 또는 빈 배열
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]

        headers = {
            "Authorization": f"Bearer {id_token}"
        }

        print(f"\n[TEST] 존재하지 않는 과목의 과제 조회")
        response = requests.get(
            f"{gateway_url}/api/v1/courses/99999/assignments",
            headers=headers,
            timeout=5
        )

        # 404 또는 빈 배열 모두 허용
        assert response.status_code in [200, 404]

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, list)
            print(f"  ✅ 빈 배열 반환: {len(data)}개")
        else:
            print(f"  ✅ 404 Not Found")
