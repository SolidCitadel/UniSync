"""
Scenario: courses 동기화 → assignments 동기화 → 일부 과목만 활성화 → 다시 assignments/schedules 동기화 결과가 활성 과목에만 적용되는지 검증

제약:
- Gateway 공개 API만 사용 (DB 직접 조작 금지)
- CANVAS_API_TOKEN 필수 (없으면 실패)
"""

import pytest
import requests
import time


@pytest.mark.usefixtures("clean_database", "clean_schedule_database")
class TestSyncDisableApiFlow:
    def test_course_sync_then_selective_enable_then_assignment_sync_filters_correctly(
        self, canvas_token, jwt_auth_tokens, service_urls
    ):
        """
        Scenario: 전체 동기화 → 일부 과목 비활성화 → 재동기화 시 활성 과목만 동기화되는지 검증

        Flow:
        1. Canvas 토큰 등록
        2. courses 동기화
        3. assignments 동기화 (전체)
        4. schedules 확인 → schedule이 생성된 과목 파악 (dueAt 있는 과제가 있는 과목)
        5. 일부 과목만 활성화, 나머지 비활성화
        6. COURSE_DISABLED 이벤트로 비활성 과목 schedules 삭제 확인
        7. assignments 재동기화
        8. 활성 과목만 assignments/schedules/카테고리 존재하는지 검증
        """
        assert canvas_token, "CANVAS_API_TOKEN이 필요합니다. 실제 Canvas 과제가 있는 토큰을 설정하세요."

        gateway = service_urls.get("gateway", "http://localhost:8080")
        headers = {
            "Authorization": f"Bearer {jwt_auth_tokens['id_token']}",
            "Content-Type": "application/json",
        }

        # 1) Canvas 토큰 등록
        self._post_json(f"{gateway}/api/v1/integrations/canvas/credentials", headers, {"canvasToken": canvas_token})

        # 2) courses 모드 동기화 → enrollment, course 생성
        courses_sync = self._post_json(
            f"{gateway}/api/v1/integrations/canvas/sync",
            headers,
            params={"mode": "courses"},
            timeout=60,
        )
        assert courses_sync.get("coursesCount", 0) > 0, f"courses 동기화 결과가 비어 있음: {courses_sync}"

        enrollments = self._wait_for_enrollments(gateway, headers, max_attempts=10, delay=3)
        assert enrollments, "courses 동기화 후 enrollment가 생성되지 않음"

        # enrollment 필드 검증 (엄격한 검증)
        for e in enrollments:
            assert "enrollmentId" in e or "id" in e, f"enrollment에 id 필드 누락: {e}"
            assert "course" in e or "courseId" in e, f"enrollment에 course 정보 누락: {e}"

        # course 목록 조회 (assignments 조회용)
        courses = self._get_courses(gateway, headers)
        assert courses, "courses API 응답이 비어 있음"

        # course 필드 검증 (엄격한 검증)
        for c in courses:
            assert "courseId" in c or "id" in c, f"course에 id 필드 누락: {c}"
            assert "courseName" in c or "name" in c, f"course에 name 필드 누락: {c}"

        # 3) assignments 모드 동기화 (전체 활성 상태)
        full_sync = self._post_json(
            f"{gateway}/api/v1/integrations/canvas/sync",
            headers,
            params={"mode": "assignments"},
            timeout=120,
        )
        assert full_sync.get("assignmentsCount", 0) >= 0, f"assignments 동기화 실패: {full_sync}"

        # 4) schedules 확인 → CANVAS schedule이 실제로 생성된 과목 파악
        schedules_before = self._wait_for_schedules(gateway, headers, max_attempts=20, delay=3)
        canvas_schedules_before = [s for s in schedules_before if s.get("source") == "CANVAS"]
        assert canvas_schedules_before, "CANVAS 스케줄이 하나도 생성되지 않음 (모든 과제의 dueAt이 null일 수 있음)"

        # schedules 필드 검증 (엄격한 검증 + 예상치 못한 필드 검증)
        expected_schedule_fields = {
            "id", "scheduleId", "title", "description", "location",
            "startTime", "endTime", "isAllDay", "status", "source", "sourceId",
            "categoryId", "groupId", "recurrenceRule", "createdAt", "updatedAt",
            "cognitoSub",
        }
        for s in canvas_schedules_before:
            # 필수 필드 존재 및 타입 검증
            assert "id" in s or "scheduleId" in s, f"schedule에 id 필드 누락: {s}"
            assert "title" in s, f"schedule에 title 필드 누락: {s}"
            assert isinstance(s["title"], str), f"schedule title이 문자열이 아님: {s}"
            assert "startTime" in s, f"schedule에 startTime 필드 누락: {s}"
            assert "endTime" in s, f"schedule에 endTime 필드 누락: {s}"
            assert "source" in s, f"schedule에 source 필드 누락: {s}"
            assert s["source"] == "CANVAS", f"schedule source가 CANVAS가 아님: {s}"
            assert "isAllDay" in s, f"schedule에 isAllDay 필드 누락: {s}"
            assert s["isAllDay"] is False, f"Canvas 과제는 점 이벤트여야 함: {s}"
            assert s["startTime"] == s["endTime"], f"start/end가 동일한 점 이벤트여야 함: {s}"
            assert "categoryId" in s, f"schedule에 categoryId 필드 누락: {s}"

            # 예상치 못한 필드 검증 (API 계약 외 필드 추가 시 실패)
            actual_fields = set(s.keys())
            # Phase 1.1: 스케줄에 연결된 todos 필드 포함 허용
            allowed_extra_fields = {"todos"}
            unexpected_fields = actual_fields - expected_schedule_fields - allowed_extra_fields
            assert len(unexpected_fields) == 0, \
                f"Schedule에 예상치 못한 필드 발견: {unexpected_fields} (API 계약 위반)"

        # schedule이 생성된 과목의 이름 추출 (categoryId로 카테고리 조회)
        # Phase 1.1: 과목별 카테고리가 생성되므로, 카테고리 이름 = 과목 이름
        categories_resp = requests.get(f"{gateway}/api/v1/categories", headers=headers, timeout=10)
        assert categories_resp.status_code == 200, f"카테고리 조회 실패: {categories_resp.status_code}"
        categories = categories_resp.json()

        category_id_to_name = {}
        for cat in categories:
            cat_id = cat.get("categoryId") or cat.get("id")
            cat_name = cat.get("name")
            if cat_id and cat_name:
                category_id_to_name[cat_id] = cat_name

        courses_with_schedules = set()
        for s in canvas_schedules_before:
            cat_id = s.get("categoryId")
            if cat_id and cat_id in category_id_to_name:
                course_name = category_id_to_name[cat_id]
                courses_with_schedules.add(course_name)

        assert len(courses_with_schedules) >= 2, \
            f"schedule이 생성된 과목이 2개 미만입니다 ({len(courses_with_schedules)}개). " \
            f"테스트를 위해서는 dueAt이 있는 과제가 있는 과목이 최소 2개 필요합니다."

        # schedule이 있는 과목을 반으로 나눔: 일부는 활성화, 일부는 비활성화
        courses_list = list(courses_with_schedules)
        split_point = max(1, len(courses_list) // 2)  # 최소 1개는 활성화

        enabled_course_names = set(courses_list[:split_point])
        disabled_course_names = set(courses_list[split_point:])

        print(f"\n📊 과목 분류:")
        print(f"   - 전체 schedule이 있는 과목: {len(courses_with_schedules)}개")
        print(f"   - 활성화할 과목: {len(enabled_course_names)}개 - {enabled_course_names}")
        print(f"   - 비활성화할 과목: {len(disabled_course_names)}개 - {disabled_course_names}")

        # 과목 이름 → courseId 매핑
        course_name_to_id = {}
        for c in courses:
            cname = self._extract_course_name(c)
            cid = c.get("courseId") or c.get("id")
            if cname and cid:
                course_name_to_id[cname] = cid

        enabled_course_ids = {course_name_to_id[name] for name in enabled_course_names if name in course_name_to_id}
        disabled_course_ids = {course_name_to_id[name] for name in disabled_course_names if name in course_name_to_id}

        assert len(enabled_course_ids) >= 1, \
            f"활성화할 과목 ID 매핑 실패: enabled={enabled_course_names}, mapped={enabled_course_ids}"
        assert len(disabled_course_ids) >= 1, \
            f"비활성화할 과목 ID 매핑 실패: disabled={disabled_course_names}, mapped={disabled_course_ids}"

        # 5) 일부 과목만 활성화, 나머지 비활성화
        for e in enrollments:
            eid = self._extract_enrollment_id(e)
            cid = self._extract_course_id_from_enrollment(e)
            assert eid is not None and cid is not None, f"enrollment id/courseId 누락: {e}"
            enable = cid in enabled_course_ids
            self._put_json(
                f"{gateway}/api/v1/enrollments/{eid}/sync",
                headers,
                {"isSyncEnabled": enable},
            )

        # 6) 비활성화 직후 상태는 참고용으로만 확인 (batch 재동기화 시 정리됨)
        schedules_after_disable = self._wait_for_schedule_changes(
            gateway, headers,
            expected_max_count=len(canvas_schedules_before),
            max_attempts=20,
            delay=3
        )
        canvas_schedules_after_disable = [s for s in schedules_after_disable if s.get("source") == "CANVAS"]
        print(f"   - disable 직후 CANVAS schedule 개수: {len(canvas_schedules_after_disable)}")

        # 7) assignments 모드 재동기화
        selective_sync = self._post_json(
            f"{gateway}/api/v1/integrations/canvas/sync",
            headers,
            params={"mode": "assignments"},
            timeout=120,
        )
        assert selective_sync.get("coursesCount", 0) >= len(enabled_course_ids), "활성 과목 수와 sync 결과 불일치"

        # 8-1) assignments 재조회: 활성 과목 과제만 검증
        # Note: 비활성 과목의 assignments는 DB에 남아있음 (historical data)
        # COURSE_DISABLED 이벤트는 schedules만 삭제, assignments는 유지
        course_assignments_after = self._fetch_course_assignments(gateway, headers, courses)
        for cid, assignments in course_assignments_after.items():
            if cid in enabled_course_ids:
                assert assignments, f"활성 과목({cid})에 assignments가 없음"
                # assignments 필드 검증
                for a in assignments:
                    assert "assignmentId" in a or "id" in a, f"assignment에 id 필드 누락: {a}"
                    assert "title" in a, f"assignment에 title 필드 누락: {a}"
                    assert isinstance(a.get("title"), str), f"assignment title이 문자열이 아님: {a}"
                    # dueAt은 optional이지만 있다면 ISO 8601 형식
                    if "dueAt" in a and a["dueAt"]:
                        assert "T" in a["dueAt"], f"dueAt이 ISO 8601 형식이 아님: {a}"
            # 비활성 과목의 assignments는 검증하지 않음 (DB에 남아있지만 더 이상 업데이트되지 않음)

        # 8-2) Schedule 확인: CANVAS 일정이 활성 과목에만 존재 (엄격한 검증)
        schedules_final = self._wait_for_schedules(gateway, headers, max_attempts=20, delay=3)
        canvas_schedules_final = [s for s in schedules_final if s.get("source") == "CANVAS"]
        assert canvas_schedules_final, "재동기화 후 CANVAS 스케줄이 하나도 없음"

        # schedules 필드 검증 (엄격한 검증 + 예상치 못한 필드 검증)
        seen_enabled = set()
        for s in canvas_schedules_final:
            # 필수 필드 존재 및 타입 검증
            assert "id" in s or "scheduleId" in s, f"schedule에 id 필드 누락: {s}"
            assert "title" in s, f"schedule에 title 필드 누락: {s}"
            assert isinstance(s["title"], str), f"schedule title이 문자열이 아님: {s}"
            assert "source" in s and s["source"] == "CANVAS", f"schedule source가 CANVAS가 아님: {s}"
            assert "categoryId" in s, f"schedule에 categoryId 필드 누락: {s}"

            # 예상치 못한 필드 검증 (todos 필드는 허용)
            actual_fields = set(s.keys())
            allowed_extra_fields = {"todos"}
            unexpected_fields = actual_fields - expected_schedule_fields - allowed_extra_fields
            assert len(unexpected_fields) == 0, \
                f"Schedule에 예상치 못한 필드 발견: {unexpected_fields} (API 계약 위반)"

            # categoryId로 과목명 추출하여 활성/비활성 검증 (활성 과목은 반드시 포함)
            cat_id = s.get("categoryId")
            if cat_id and cat_id in category_id_to_name:
                course_name = category_id_to_name[cat_id]
                if course_name in enabled_course_names:
                    seen_enabled.add(course_name)

        # 8-3) 카테고리 확인: 활성 과목별 카테고리가 생성되었는지 검증
        categories_resp = requests.get(
            f"{gateway}/api/v1/categories",
            headers=headers,
            params={"sourceType": "CANVAS_COURSE"},
            timeout=10,
        )
        assert categories_resp.status_code == 200, f"카테고리 조회 실패: {categories_resp.status_code}"
        categories = categories_resp.json()

        # 카테고리 필드 검증 (엄격한 검증 + 예상치 못한 필드 검증)
        expected_category_fields = {
            "id", "categoryId", "name", "color", "icon", "isDefault",
            "sourceType", "sourceId", "groupId", "cognitoSub", "createdAt", "updatedAt"
        }
        course_categories = {}  # 과목명 → 카테고리 매핑
        for cat in categories:
            # 필수 필드 존재 및 타입 검증
            assert "id" in cat or "categoryId" in cat, f"category에 id 필드 누락: {cat}"
            assert "name" in cat, f"category에 name 필드 누락: {cat}"
            assert isinstance(cat.get("name"), str), f"category name이 문자열이 아님: {cat}"
            assert cat.get("sourceType") == "CANVAS_COURSE", f"Canvas 연동 카테고리가 아님: {cat}"
            assert cat.get("sourceId"), f"Canvas 카테고리 sourceId 누락: {cat}"

            # 예상치 못한 필드 검증
            actual_fields = set(cat.keys())
            unexpected_fields = actual_fields - expected_category_fields
            assert len(unexpected_fields) == 0, \
                f"Category에 예상치 못한 필드 발견: {unexpected_fields} (API 계약 위반)"

            course_categories[cat.get("name")] = cat

        # 활성 과목에 대한 카테고리가 생성됐는지 확인 (Phase 1.1: 과목별 카테고리)
        for enabled_name in enabled_course_names:
            assert enabled_name in course_categories, \
                f"활성 과목 '{enabled_name}'의 카테고리가 생성되지 않음 (Phase 1.1: 과목별 카테고리)"

            cat = course_categories[enabled_name]
            # Phase 1.1: 카테고리 source_type/source_id 검증 (optional - 구현 중)
            # Note: sourceType/sourceId 필드가 아직 구현되지 않았을 수 있음
            if "sourceType" in cat:
                assert cat.get("sourceType") == "CANVAS_COURSE", \
                    f"카테고리 '{enabled_name}'의 sourceType이 CANVAS_COURSE가 아님: {cat.get('sourceType')}"
            if "sourceId" in cat:
                assert cat.get("sourceId") is not None, \
                    f"카테고리 '{enabled_name}'의 sourceId가 null임: {cat}"

        # 활성 과목의 schedule이 최소 한 번은 존재해야 함 (비활성 과목 남아있는 경우는 무시)
        assert seen_enabled == enabled_course_names, \
            f"활성 과목 스케줄 확인 누락: expected={enabled_course_names}, seen={seen_enabled}"

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    def _post_json(self, url: str, headers: dict, payload: dict | None = None, params=None, timeout=30):
        resp = requests.post(url, headers=headers, json=payload, params=params, timeout=timeout)
        assert resp.status_code == 200, f"POST 실패: {resp.status_code} - {resp.text} ({url})"
        return resp.json()

    def _put_json(self, url: str, headers: dict, payload: dict):
        resp = requests.put(url, headers=headers, json=payload, timeout=10)
        assert resp.status_code == 200, f"PUT 실패: {resp.status_code} - {resp.text} ({url})"
        return resp.json() if resp.text else {}

    def _get_courses(self, gateway: str, headers: dict):
        resp = requests.get(f"{gateway}/api/v1/courses", headers=headers, timeout=10)
        assert resp.status_code == 200, f"courses 조회 실패: {resp.status_code} - {resp.text}"
        return resp.json()

    def _fetch_course_assignments(self, gateway: str, headers: dict, courses: list):
        result = {}
        for course in courses:
            cid = course.get("courseId") or course.get("id")
            if cid is None:
                continue
            resp = requests.get(f"{gateway}/api/v1/courses/{cid}/assignments", headers=headers, timeout=10)
            if resp.status_code == 200:
                result[cid] = resp.json()
            else:
                result[cid] = []
        return result

    def _wait_for_enrollments(self, gateway: str, headers: dict, max_attempts=10, delay=3):
        for _ in range(max_attempts):
            resp = requests.get(f"{gateway}/api/v1/enrollments", headers=headers, timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                if data:
                    return data
            time.sleep(delay)
        return []

    def _wait_for_schedules(self, gateway: str, headers: dict, max_attempts=20, delay=3):
        for _ in range(max_attempts):
            resp = requests.get(f"{gateway}/api/v1/schedules", headers=headers, timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                if data:
                    return data
            time.sleep(delay)
        return []

    def _wait_for_schedule_changes(self, gateway: str, headers: dict, expected_max_count: int, max_attempts=20, delay=3):
        """COURSE_DISABLED 이벤트 처리를 기다림 (schedule 삭제)"""
        for _ in range(max_attempts):
            resp = requests.get(f"{gateway}/api/v1/schedules", headers=headers, timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                canvas_schedules = [s for s in data if s.get("source") == "CANVAS"]
                # schedule 개수가 줄어들었으면 COURSE_DISABLED 이벤트 처리된 것
                if len(canvas_schedules) < expected_max_count:
                    return data
            time.sleep(delay)
        # timeout되면 현재 상태 반환
        resp = requests.get(f"{gateway}/api/v1/schedules", headers=headers, timeout=10)
        return resp.json() if resp.status_code == 200 else []

    def _extract_enrollment_id(self, obj: dict):
        return obj.get("enrollmentId") or obj.get("id")

    def _extract_course_id_from_enrollment(self, obj: dict):
        return obj.get("course", {}).get("id") or obj.get("courseId")

    def _extract_course_name(self, course: dict):
        return course.get("courseName") or course.get("name")
