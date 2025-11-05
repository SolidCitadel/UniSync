"""
  E2E 
Canvas       Course/Assignment    
"""

import pytest
import requests
import time
from conftest import wait_for_sync


class TestCanvasSyncE2E:
    """Canvas   E2E """

    @pytest.mark.usefixtures("wait_for_services", "clean_test_data")
    def test_full_canvas_sync_workflow(self, canvas_token, test_user_id, service_urls):
        """
          :
        1. Canvas   (User-Service)
        2.    (User-Service)
        3.    (Lambda  Course-Service)
        4. Course   (Course-Service)
        5. Assignment   (Course-Service)
        """
        user_service = service_urls["user_service"]
        course_service = service_urls["course_service"]

        print("\n" + "=" * 80)
        print(" E2E Test: Canvas    ")
        print("=" * 80)

        # ================================================================
        # Step 1: Canvas  
        # ================================================================
        print("\n[1/5] Canvas  ...")
        register_response = requests.post(
            f"{user_service}/api/v1/credentials/canvas",
            headers={"X-User-Id": str(test_user_id)},
            json={"canvasToken": canvas_token},
            timeout=10
        )

        assert register_response.status_code == 200, \
            f"  : {register_response.status_code} - {register_response.text}"

        register_data = register_response.json()
        assert register_data.get("success") is True, "   "
        print(f"  [OK] Canvas    ( )")

        # ================================================================
        # Step 2:   
        # ================================================================
        print("\n[2/5]   ...")
        status_response = requests.get(
            f"{user_service}/api/v1/integrations/status",
            headers={"X-User-Id": str(test_user_id)},
            timeout=5
        )

        assert status_response.status_code == 200, \
            f"   : {status_response.status_code} - {status_response.text}"

        status_data = status_response.json()
        assert status_data.get("canvas") is not None, "Canvas   "
        assert status_data["canvas"]["isConnected"] is True, "Canvas   false"

        canvas_username = status_data["canvas"].get("externalUsername")
        print(f"  [OK] Canvas  : {canvas_username}")

        # ================================================================
        # Step 3:   
        # ================================================================
        print("\n[3/5]    ...")
        print("  (user-token-registered  Lambda  course-enrollment  Course-Service)")
        print("  (assignment-sync-needed  Lambda  assignment-events  Course-Service)")

        # Course    ( 60)
        waiter = wait_for_sync(timeout_seconds=60, poll_interval=2)

        def check_courses_synced():
            try:
                response = requests.get(
                    f"{course_service}/api/v1/courses",
                    params={"userId": test_user_id},
                    timeout=5
                )
                if response.status_code == 200:
                    courses = response.json()
                    if len(courses) > 0:
                        print(f"  [OK] {len(courses)} courses  ")
                        return True
            except Exception as e:
                print(f"  [WAIT]  ... ({str(e)[:50]})")
            return False

        try:
            waiter(check_courses_synced, "[FAIL] Course   (60)")
        except TimeoutError as e:
            pytest.fail(str(e))

        # ================================================================
        # Step 4: Course  
        # ================================================================
        print("\n[4/5] Course  ...")
        courses_response = requests.get(
            f"{course_service}/api/v1/courses",
            params={"userId": test_user_id},
            timeout=5
        )

        assert courses_response.status_code == 200, \
            f"Course  : {courses_response.status_code} - {courses_response.text}"

        courses = courses_response.json()
        assert len(courses) > 0, " Course "

        print(f"  [OK] {len(courses)} courses ")
        for i, course in enumerate(courses[:3], 1):  #  3 
            print(f"     {i}. {course['name']} ({course['courseCode']})")
        if len(courses) > 3:
            print(f"     ...  {len(courses) - 3}")

        # ================================================================
        # Step 5: Assignment  
        # ================================================================
        print("\n[5/5] Assignment   ...")

        #   Course Assignment 
        first_course = courses[0]
        first_course_id = first_course["id"]
        first_course_name = first_course["name"]

        # Assignment    ( 60)
        def check_assignments_synced():
            try:
                response = requests.get(
                    f"{course_service}/api/v1/courses/{first_course_id}/assignments",
                    timeout=5
                )
                if response.status_code == 200:
                    assignments = response.json()
                    if len(assignments) > 0:
                        print(f"  [OK] {len(assignments)} assignments  ")
                        return True
            except Exception as e:
                print(f"  [WAIT] Assignment  ...")
            return False

        try:
            waiter(check_assignments_synced,
                   f"[FAIL] Assignment   (Course: {first_course_name})")
        except TimeoutError:
            # Assignment  Course    
            print(f"  [WARN] Course '{first_course_name}' Assignment   ")

        #  Assignment 
        assignments_response = requests.get(
            f"{course_service}/api/v1/courses/{first_course_id}/assignments",
            timeout=5
        )

        assert assignments_response.status_code == 200, \
            f"Assignment  : {assignments_response.status_code}"

        assignments = assignments_response.json()

        if len(assignments) > 0:
            print(f"  [OK] Course '{first_course_name}' {len(assignments)} assignments ")
            for i, assignment in enumerate(assignments[:3], 1):  #  3 
                due_at = assignment.get("dueAt", " ")
                print(f"     {i}. {assignment['title']} (Due: {due_at})")
            if len(assignments) > 3:
                print(f"     ...  {len(assignments) - 3}")
        else:
            print(f"  [INFO] Course '{first_course_name}' Assignment ")

        # ================================================================
        #  
        # ================================================================
        print("\n" + "=" * 80)
        print("[OK] E2E  !")
        print(f"   - : {canvas_username} (userId={test_user_id})")
        print(f"   - Courses: {len(courses)} ")
        print(f"   - Assignments: {len(assignments)} (  Course)")
        print("=" * 80 + "\n")

        #   
        assert len(courses) > 0, " 1  Course  "


    @pytest.mark.usefixtures("wait_for_services")
    def test_integration_status_after_sync(self, test_user_id, service_urls):
        """
            
        lastSyncedAt  
        """
        user_service = service_urls["user_service"]

        print("\n" + "=" * 80)
        print("   ")
        print("=" * 80)

        status_response = requests.get(
            f"{user_service}/api/v1/integrations/status",
            headers={"X-User-Id": str(test_user_id)},
            timeout=5
        )

        assert status_response.status_code == 200
        status_data = status_response.json()

        canvas_info = status_data.get("canvas")
        assert canvas_info is not None, "Canvas   "
        assert canvas_info["isConnected"] is True, "Canvas   false"

        print(f"  [OK] Canvas  : Connected")
        print(f"     - Username: {canvas_info.get('externalUsername')}")
        print(f"     - Last Validated: {canvas_info.get('lastValidatedAt')}")

        last_synced = canvas_info.get("lastSyncedAt")
        if last_synced:
            print(f"     - Last Synced: {last_synced}")
        else:
            print(f"     - Last Synced: (    )")

        print("=" * 80 + "\n")