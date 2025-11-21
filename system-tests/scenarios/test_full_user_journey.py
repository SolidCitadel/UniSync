"""
전체 시나리오 E2E 테스트

사용자의 완전한 사용 플로우:
1. 회원가입 및 로그인 (JWT 토큰 획득)
2. Canvas 토큰 등록
3. 자동 동기화 (Assignment → Schedule 변환)
4. 일정에서 과제 확인
5. 일정 CRUD (생성, 조회, 수정, 삭제)
"""

import pytest
import requests
import time
from datetime import datetime, timedelta


class TestFullUserJourney:
    """전체 사용자 시나리오 E2E 테스트"""

    def test_complete_user_journey(self, canvas_token, jwt_auth_tokens, service_urls):
        """
        완전한 사용자 여정 테스트:
        회원가입 → Canvas 연동 → 과제 동기화 → 일정 확인 → 일정 CRUD
        """
        gateway_url = service_urls.get("gateway", "http://localhost:8080")
        id_token = jwt_auth_tokens["id_token"]
        cognito_sub = jwt_auth_tokens["cognito_sub"]
        email = jwt_auth_tokens["email"]

        headers = {
            "Authorization": f"Bearer {id_token}",
            "Content-Type": "application/json"
        }

        print("\n" + "=" * 100)
        print("전체 시나리오 E2E 테스트: 회원가입 → Canvas 연동 → 과제 동기화 → 일정 CRUD")
        print("=" * 100)
        print(f"[User] {email}")
        print(f"[Cognito Sub] {cognito_sub}")
        print("=" * 100)

        # ============================================================
        # STEP 1: 회원가입 및 로그인 (JWT 토큰 획득) - fixture에서 완료
        # ============================================================
        print("\n[STEP 1/7] 회원가입 및 로그인")
        print(f"  ✅ JWT 토큰 획득 완료: {id_token[:30]}...")

        # ============================================================
        # STEP 2: Canvas 토큰 등록
        # ============================================================
        print("\n[STEP 2/7] Canvas 토큰 등록")
        register_response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/credentials",
            headers=headers,
            json={"canvasToken": canvas_token},
            timeout=10
        )

        assert register_response.status_code == 200, \
            f"Canvas 토큰 등록 실패: {register_response.status_code}"

        register_data = register_response.json()
        assert register_data.get("success") is True
        print(f"  ✅ Canvas 토큰 등록 완료")

        # ============================================================
        # STEP 3: Canvas 연동 상태 확인
        # ============================================================
        print("\n[STEP 3/7] Canvas 연동 상태 확인")
        status_response = requests.get(
            f"{gateway_url}/api/v1/integrations/status",
            headers=headers,
            timeout=5
        )

        assert status_response.status_code == 200
        status_data = status_response.json()
        assert status_data.get("canvas", {}).get("isConnected") is True

        canvas_username = status_data["canvas"].get("externalUsername")
        print(f"  ✅ Canvas 연동 확인: {canvas_username}")

        # ============================================================
        # STEP 4: 수동 동기화 실행 (Phase 1: Manual Sync)
        # ============================================================
        print("\n[STEP 4/7] 수동 동기화 실행 (Assignment → Schedule 변환)")
        print("  [플로우] POST /integrations/canvas/sync → Lambda → Course-Service → SQS → Schedule-Service")

        # 수동 동기화 API 호출
        sync_response = requests.post(
            f"{gateway_url}/api/v1/integrations/canvas/sync",
            headers=headers,
            timeout=30
        )
        assert sync_response.status_code in [200, 202], f"동기화 호출 실패: {sync_response.status_code} - {sync_response.text}"
        print(f"  ✅ 동기화 요청 완료: {sync_response.status_code}")

        # Courses 동기화 대기
        courses = self._wait_for_courses(gateway_url, headers)
        print(f"  ✅ {len(courses)} courses 동기화 완료")

        # Schedules 동기화 대기 (Assignment → Schedule 변환)
        schedules = self._wait_for_schedules(gateway_url, headers)
        canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']
        print(f"  ✅ {len(schedules)} schedules 생성 완료 (Canvas 과제: {len(canvas_schedules)}개)")

        # Canvas 카테고리 확인 (Canvas 과제가 있을 때만)
        categories = self._get_categories(gateway_url, headers)
        canvas_category = next((c for c in categories if c['name'] == 'Canvas'), None)

        if len(canvas_schedules) > 0:
            # Canvas 과제가 있으면 카테고리가 자동 생성되어야 함
            assert canvas_category is not None, "Canvas 카테고리가 자동 생성되지 않음"
            print(f"  ✅ Canvas 카테고리 자동 생성: ID={canvas_category['categoryId']}")
        else:
            # Canvas 과제가 없으면 카테고리 생성을 직접 수행
            print(f"  ⚠️  Canvas 과제가 없어 카테고리가 자동 생성되지 않음 - 직접 생성")
            canvas_category = self._create_canvas_category(gateway_url, headers)
            print(f"  ✅ Canvas 카테고리 직접 생성: ID={canvas_category['categoryId']}")

        # ============================================================
        # STEP 5: 일정에서 과제 확인
        # ============================================================
        print("\n[STEP 5/7] 일정에서 과제 확인")

        if len(canvas_schedules) > 0:
            print(f"  ✅ {len(canvas_schedules)}개의 Canvas 과제가 일정으로 변환됨")
            for i, schedule in enumerate(canvas_schedules[:3], 1):
                print(f"     {i}. {schedule['title']}")
                print(f"        - 시작: {schedule.get('startTime', 'N/A')}")
                print(f"        - 마감: {schedule.get('endTime', 'N/A')}")
                print(f"        - 상태: {schedule.get('status', 'N/A')}")

            # 첫 번째 Canvas 과제 상세 조회
            first_schedule = canvas_schedules[0]
            schedule_detail = self._get_schedule_detail(
                gateway_url, headers, first_schedule['scheduleId']
            )
            assert schedule_detail['title'] == first_schedule['title']
            print(f"\n  ✅ 과제 상세 조회 성공: {schedule_detail['title']}")
        else:
            print(f"  ⚠️  Canvas 과제가 없음 (정상: 과제가 없는 과목일 수 있음)")

        # ============================================================
        # STEP 6: 일정 CRUD - CREATE (사용자 일정 생성)
        # ============================================================
        print("\n[STEP 6/7] 일정 CRUD 테스트")

        # 6-1. CREATE: 새 일정 생성
        print("\n  [CREATE] 새 일정 생성...")
        new_schedule = {
            "title": "E2E 테스트 일정",
            "description": "전체 시나리오 테스트를 위한 일정",
            "startTime": (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT10:00:00"),
            "endTime": (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT11:00:00"),
            "isAllDay": False,
            "categoryId": canvas_category['categoryId']
        }

        create_response = requests.post(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            json=new_schedule,
            timeout=5
        )

        assert create_response.status_code == 201, \
            f"일정 생성 실패: {create_response.status_code} - {create_response.text}"

        created_schedule = create_response.json()
        created_schedule_id = created_schedule['scheduleId']
        print(f"    ✅ 일정 생성 성공: ID={created_schedule_id}, Title={created_schedule['title']}")

        # 6-2. READ: 일정 목록 조회
        print("\n  [READ] 일정 목록 조회...")
        all_schedules = self._get_schedules(gateway_url, headers)
        user_schedule = next(
            (s for s in all_schedules if s['scheduleId'] == created_schedule_id),
            None
        )
        assert user_schedule is not None, "생성한 일정이 목록에 없음"
        print(f"    ✅ 일정 목록에서 확인: {user_schedule['title']}")

        # 6-3. UPDATE: 일정 수정
        print("\n  [UPDATE] 일정 수정...")
        updated_data = {
            "title": "E2E 테스트 일정 (수정됨)",
            "description": "수정된 설명",
            "startTime": new_schedule['startTime'],
            "endTime": new_schedule['endTime'],
            "isAllDay": False,
            "categoryId": canvas_category['categoryId']
        }

        update_response = requests.put(
            f"{gateway_url}/api/v1/schedules/{created_schedule_id}",
            headers=headers,
            json=updated_data,
            timeout=5
        )

        assert update_response.status_code == 200, \
            f"일정 수정 실패: {update_response.status_code}"

        updated_schedule = update_response.json()
        assert updated_schedule['title'] == "E2E 테스트 일정 (수정됨)"
        print(f"    ✅ 일정 수정 성공: {updated_schedule['title']}")

        # 6-4. DELETE: 일정 삭제
        print("\n  [DELETE] 일정 삭제...")
        delete_response = requests.delete(
            f"{gateway_url}/api/v1/schedules/{created_schedule_id}",
            headers=headers,
            timeout=5
        )

        assert delete_response.status_code == 204, \
            f"일정 삭제 실패: {delete_response.status_code}"

        print(f"    ✅ 일정 삭제 성공: ID={created_schedule_id}")

        # 삭제 확인
        final_schedules = self._get_schedules(gateway_url, headers)
        deleted_schedule = next(
            (s for s in final_schedules if s['scheduleId'] == created_schedule_id),
            None
        )
        assert deleted_schedule is None, "삭제한 일정이 여전히 존재함"
        print(f"    ✅ 삭제 검증 완료: 일정이 목록에 없음")

        # ============================================================
        # STEP 7: 최종 검증 및 요약
        # ============================================================
        print("\n[STEP 7/7] 최종 검증 및 요약")

        # 최종 데이터 확인
        final_courses = self._get_courses(gateway_url, headers)
        final_schedules = self._get_schedules(gateway_url, headers)
        final_categories = self._get_categories(gateway_url, headers)

        # ============================================================
        # 최종 결과 출력
        # ============================================================
        print("\n" + "=" * 100)
        print("[PASS] 전체 시나리오 E2E 테스트 성공!")
        print("=" * 100)
        print(f"✅ STEP 1: 회원가입 및 로그인 (JWT)")
        print(f"✅ STEP 2: Canvas 토큰 등록")
        print(f"✅ STEP 3: Canvas 연동 확인 ({canvas_username})")
        print(f"✅ STEP 4: 자동 동기화 (Courses: {len(final_courses)}, Schedules: {len(final_schedules)})")
        print(f"✅ STEP 5: 일정에서 과제 확인 (Canvas 과제: {len(canvas_schedules)})")
        print(f"✅ STEP 6: 일정 CRUD (생성 → 조회 → 수정 → 삭제)")
        print(f"✅ STEP 7: 최종 검증 완료")
        print("=" * 100)
        print(f"[Summary]")
        print(f"  - User: {email}")
        print(f"  - Courses: {len(final_courses)}개")
        print(f"  - Canvas Schedules: {len(canvas_schedules)}개")
        print(f"  - Total Schedules: {len(final_schedules)}개")
        print(f"  - Categories: {len(final_categories)}개")
        print("=" * 100 + "\n")

        # 최소 검증
        assert len(final_courses) > 0, "최소 1개 이상의 Course가 동기화되어야 함"

    # ============================================================
    # Helper Methods
    # ============================================================

    def _wait_for_courses(self, gateway_url, headers, timeout=60):
        """Courses 동기화 대기"""
        start = time.time()
        while time.time() - start < timeout:
            try:
                response = requests.get(
                    f"{gateway_url}/api/v1/courses",
                    headers=headers,
                    timeout=5
                )
                if response.status_code == 200:
                    courses = response.json()
                    if len(courses) > 0:
                        return courses
            except Exception as e:
                print(f"  [WAIT] Courses 대기 중... ({str(e)[:50]})")
            time.sleep(2)
        raise TimeoutError("Courses 동기화 타임아웃")

    def _wait_for_schedules(self, gateway_url, headers, timeout=60):
        """Schedules 동기화 대기 (Assignment → Schedule 변환)"""
        start = time.time()
        while time.time() - start < timeout:
            try:
                response = requests.get(
                    f"{gateway_url}/api/v1/schedules",
                    headers=headers,
                    timeout=5
                )
                if response.status_code == 200:
                    schedules = response.json()
                    canvas_schedules = [s for s in schedules if s.get('source') == 'CANVAS']
                    if len(canvas_schedules) > 0:
                        return schedules
            except Exception as e:
                print(f"  [WAIT] Schedules 대기 중... ({str(e)[:50]})")
            time.sleep(2)

        print(f"  [WARN] Canvas 과제 일정이 생성되지 않음 (과제가 없는 과목일 수 있음)")
        return []

    def _get_courses(self, gateway_url, headers):
        """Courses 조회"""
        response = requests.get(
            f"{gateway_url}/api/v1/courses",
            headers=headers,
            timeout=5
        )
        assert response.status_code == 200
        return response.json()

    def _get_schedules(self, gateway_url, headers):
        """Schedules 조회"""
        response = requests.get(
            f"{gateway_url}/api/v1/schedules",
            headers=headers,
            timeout=5
        )
        assert response.status_code == 200
        return response.json()

    def _get_schedule_detail(self, gateway_url, headers, schedule_id):
        """Schedule 상세 조회"""
        response = requests.get(
            f"{gateway_url}/api/v1/schedules/{schedule_id}",
            headers=headers,
            timeout=5
        )
        assert response.status_code == 200
        return response.json()

    def _get_categories(self, gateway_url, headers):
        """Categories 조회"""
        response = requests.get(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            timeout=5
        )
        assert response.status_code == 200
        return response.json()

    def _create_canvas_category(self, gateway_url, headers):
        """Canvas 카테고리 생성 (과제가 없을 때 직접 생성)"""
        category_data = {
            "name": "Canvas",
            "color": "#FF6B6B",
            "icon": "📚"
        }
        response = requests.post(
            f"{gateway_url}/api/v1/categories",
            headers=headers,
            json=category_data,
            timeout=5
        )
        assert response.status_code == 201, \
            f"Canvas 카테고리 생성 실패: {response.status_code} - {response.text}"
        return response.json()
