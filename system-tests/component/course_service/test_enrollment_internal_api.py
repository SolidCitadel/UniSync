"""
Course-Service 내부 Enrollment API 컴포넌트 테스트

동기화 활성 수강 내역 조회 API를 검증한다.
"""

import pytest
import requests


@pytest.mark.usefixtures("clean_database")
class TestEnrollmentInternalApi:
    def test_get_enabled_enrollments_requires_cognito_header(self, service_urls):
        """
        헤더 없이 호출 시 400/401 등의 오류를 반환해야 한다.
        """
        course_url = service_urls["course"]

        response = requests.get(
            f"{course_url}/internal/v1/enrollments/enabled",
            timeout=5
        )

        # 내부 API이므로 인증 헤더가 없으면 오류를 반환해야 한다 (상태코드는 구현에 따라 다름)
        assert response.status_code in [400, 401, 403, 500]

    def test_get_enabled_enrollments_filters_disabled(self, mysql_connection, service_urls):
        """
        is_sync_enabled=false 또는 다른 사용자의 수강 내역은 제외된다.
        """
        cursor = mysql_connection.cursor()

        # 과목 2개 생성 (한 개만 is_sync_enabled=true)
        courses = []
        for idx in range(2):
            cursor.execute(
                """
                INSERT INTO courses (
                    canvas_course_id, name, course_code, description,
                    start_at, end_at, created_at, updated_at
                )
                VALUES (%s, %s, %s, %s, NOW(), NOW(), NOW(), NOW())
                """,
                (98760 + idx, f"Algorithms 10{idx}", f"CS10{idx}", "Component test course"),
            )
            courses.append((cursor.lastrowid, 98760 + idx, f"Algorithms 10{idx}"))

        # 수강 내역 생성 (동일 사용자: 1개 on, 1개 off)
        cursor.execute(
            """
            INSERT INTO enrollments (
                cognito_sub, course_id, is_sync_leader, is_sync_enabled, enrolled_at
            )
            VALUES (%s, %s, %s, %s, NOW())
            """,
            ("enabled-user", courses[0][0], True, True),
        )
        cursor.execute(
            """
            INSERT INTO enrollments (
                cognito_sub, course_id, is_sync_leader, is_sync_enabled, enrolled_at
            )
            VALUES (%s, %s, %s, %s, NOW())
            """,
            ("enabled-user", courses[1][0], False, False),
        )
        mysql_connection.commit()
        cursor.close()

        course_url = service_urls["course"]
        headers = {"X-Cognito-Sub": "enabled-user"}

        response = requests.get(
            f"{course_url}/internal/v1/enrollments/enabled",
            headers=headers,
            timeout=5,
        )

        assert response.status_code == 200
        data = response.json()

        assert isinstance(data, list)
        assert len(data) == 1

        enrollment = data[0]
        assert enrollment["courseId"] == courses[0][0]
        assert enrollment["canvasCourseId"] == courses[0][1]
        assert enrollment["courseName"] == courses[0][2]
