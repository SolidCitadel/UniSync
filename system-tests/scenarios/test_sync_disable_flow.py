"""
Scenario: 비활성화된 과목은 동기화 시 스케줄/카테고리가 생성되지 않는다.
"""

import time
import pytest
import requests


@pytest.mark.usefixtures("clean_database", "clean_schedule_database")
class TestSyncDisableFlow:
    def test_disabled_enrollment_blocks_schedule_creation(
        self,
        jwt_auth_tokens,
        service_urls,
        mysql_connection,
        schedule_db_connection
    ):
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        course_service_url = service_urls["course"]
        schedule_service_url = service_urls["schedule"]

        id_token = jwt_auth_tokens["id_token"]
        cognito_sub = jwt_auth_tokens["user1_sub"]

        # 1) 비활성 enrollment를 직접 삽입 (Course DB)
        cursor = mysql_connection.cursor()
        cursor.execute(
            """
            INSERT INTO courses (canvas_course_id, name, course_code, created_at, updated_at)
            VALUES (%s, %s, %s, NOW(), NOW())
            """,
            (999001, "Disable Sync Course", "DISABLE101"),
        )
        course_id = cursor.lastrowid

        cursor.execute(
            """
            INSERT INTO enrollments (cognito_sub, course_id, is_sync_leader, is_sync_enabled, enrolled_at)
            VALUES (%s, %s, %s, %s, NOW())
            """,
            (cognito_sub, course_id, True, False),
        )
        mysql_connection.commit()
        cursor.close()

        # 2) Canvas 토큰 등록 (동기화 API 호출 통과용)
        requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers={
                "Authorization": f"Bearer {id_token}",
                "Content-Type": "application/json",
            },
            json={"canvasToken": "dummy-token-for-disabled-sync"},
            timeout=10,
        )

        # 3) 동기화 API 호출 (비활성 수강 → 0건 리턴 기대)
        sync_resp = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/sync",
            headers={
                "Authorization": f"Bearer {id_token}",
                "Content-Type": "application/json",
            },
            timeout=60,
        )

        assert sync_resp.status_code == 200, f"동기화 실패: {sync_resp.status_code} - {sync_resp.text}"
        body = sync_resp.json()
        assert body.get("coursesCount", 0) == 0
        assert body.get("assignmentsCount", 0) == 0

        # 4) 스케줄/카테고리 생성 여부 확인 (없어야 함)
        for _ in range(5):
            schedules_resp = requests.get(
                f"{schedule_service_url}/v1/schedules",
                headers={"X-Cognito-Sub": cognito_sub},
                timeout=5,
            )
            if schedules_resp.status_code == 200 and len(schedules_resp.json()) == 0:
                break
            time.sleep(1)

        schedules_resp = requests.get(
            f"{schedule_service_url}/v1/schedules",
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5,
        )
        assert schedules_resp.status_code == 200
        assert len(schedules_resp.json()) == 0, "비활성 과목인데 Schedule이 생성됨"

        categories_resp = requests.get(
            f"{schedule_service_url}/v1/categories",
            headers={"X-Cognito-Sub": cognito_sub},
            timeout=5,
        )
        assert categories_resp.status_code == 200
        categories = categories_resp.json()
        assert all(c.get("name") != "Canvas" for c in categories), "비활성 과목인데 Canvas 카테고리가 생성됨"

        # 추가 확인: Schedule DB에도 CANVAS 소스가 없어야 함
        cur = schedule_db_connection.cursor(dictionary=True)
        cur.execute("SELECT COUNT(*) AS cnt FROM schedules WHERE source = 'CANVAS'")
        cnt = cur.fetchone()["cnt"]
        cur.close()
        assert cnt == 0, "Schedule DB에 CANVAS 소스 데이터가 생성됨"

        print("✅ 비활성 과목 동기화 시 Schedule/Category 생성이 차단됨 확인")
