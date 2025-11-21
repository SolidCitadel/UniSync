#!/usr/bin/env python3
"""
Canvas API 직접 호출 테스트 스크립트

실제 Canvas API 토큰으로 API 호출을 테스트합니다.
"""

import os
import sys
import requests
import json
from datetime import datetime
from pathlib import Path

# .env 파일 자동 로드
try:
    from dotenv import load_dotenv
    # 프로젝트 루트의 .env 파일 로드 (tests/api/ 기준)
    root_dir = Path(__file__).parent.parent.parent
    env_path = root_dir / '.env'
    if env_path.exists():
        load_dotenv(env_path)
        print(f"✅ .env 파일 로드됨: {env_path}\n")
except ImportError:
    print("⚠️  python-dotenv가 설치되지 않았습니다. 환경 변수를 수동으로 설정하세요.\n")
    print("  pip install python-dotenv\n")


def print_header(text):
    """헤더 출력"""
    print("\n" + "=" * 60)
    print(f"  {text}")
    print("=" * 60)


def print_section(text):
    """섹션 출력"""
    print(f"\n>>> {text}")


def test_canvas_authentication(base_url, token):
    """Canvas API 인증 테스트"""
    print_section("Step 1: Canvas API 인증 테스트")

    try:
        url = f"{base_url}/users/self"
        headers = {'Authorization': f'Bearer {token}'}

        print(f"  URL: {url}")
        print(f"  Headers: Authorization: Bearer {token[:10]}...")

        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()

        user_data = response.json()
        print(f"\n  ✅ 인증 성공!")
        print(f"  - User ID: {user_data.get('id')}")
        print(f"  - Name: {user_data.get('name')}")
        print(f"  - Email: {user_data.get('email', 'N/A')}")

        return True
    except Exception as e:
        print(f"\n  ❌ 인증 실패: {str(e)}")
        return False


def get_courses(base_url, token):
    """Canvas 과목 목록 조회"""
    print_section("Step 2: 과목 목록 조회")

    try:
        url = f"{base_url}/courses"
        headers = {'Authorization': f'Bearer {token}'}
        params = {
            'enrollment_state': 'active',
            'per_page': 10
        }

        print(f"  URL: {url}")
        print(f"  Params: {params}")

        response = requests.get(url, headers=headers, params=params, timeout=10)
        response.raise_for_status()

        courses = response.json()

        if not courses:
            print(f"\n  ⚠️  수강 중인 과목이 없습니다.")
            return []

        print(f"\n  ✅ {len(courses)}개의 과목을 찾았습니다:")
        for i, course in enumerate(courses, 1):
            print(f"\n  [{i}] {course.get('name', 'Unnamed Course')}")
            print(f"      - Course ID: {course.get('id')}")
            print(f"      - Code: {course.get('course_code', 'N/A')}")

        return courses
    except Exception as e:
        print(f"\n  ❌ 과목 조회 실패: {str(e)}")
        return []


def get_assignments(base_url, token, course_id):
    """특정 과목의 과제 목록 조회"""
    print_section(f"Step 3: 과목 {course_id}의 과제 목록 조회")

    try:
        url = f"{base_url}/courses/{course_id}/assignments"
        headers = {'Authorization': f'Bearer {token}'}
        params = {'per_page': 10}

        print(f"  URL: {url}")

        response = requests.get(url, headers=headers, params=params, timeout=10)
        response.raise_for_status()

        assignments = response.json()

        if not assignments:
            print(f"\n  ⚠️  과제가 없습니다.")
            return []

        print(f"\n  ✅ {len(assignments)}개의 과제를 찾았습니다:")
        for i, assignment in enumerate(assignments, 1):
            print(f"\n  [{i}] {assignment.get('name', 'Unnamed Assignment')}")
            print(f"      - Assignment ID: {assignment.get('id')}")
            print(f"      - Due At: {assignment.get('due_at', 'No due date')}")
            print(f"      - Points: {assignment.get('points_possible', 0)}")
            print(f"      - Submission Types: {', '.join(assignment.get('submission_types', []))}")

            # 설명 미리보기 (첫 100자)
            description = assignment.get('description', '')
            if description:
                # HTML 태그 제거
                import re
                clean_desc = re.sub(r'<[^>]+>', '', description)
                preview = clean_desc[:100] + ('...' if len(clean_desc) > 100 else '')
                print(f"      - Description: {preview}")

        return assignments
    except Exception as e:
        print(f"\n  ❌ 과제 조회 실패: {str(e)}")
        return []


def get_announcements(base_url, token, course_id):
    """특정 과목의 공지사항 조회"""
    print_section(f"Step 4: 과목 {course_id}의 공지사항 조회")

    try:
        url = f"{base_url}/courses/{course_id}/discussion_topics"
        headers = {'Authorization': f'Bearer {token}'}
        params = {
            'only_announcements': 'true',
            'per_page': 5
        }

        print(f"  URL: {url}")

        response = requests.get(url, headers=headers, params=params, timeout=10)
        response.raise_for_status()

        announcements = response.json()

        if not announcements:
            print(f"\n  ⚠️  공지사항이 없습니다.")
            return []

        print(f"\n  ✅ {len(announcements)}개의 공지사항을 찾았습니다:")
        for i, announcement in enumerate(announcements, 1):
            print(f"\n  [{i}] {announcement.get('title', 'Untitled')}")
            print(f"      - Topic ID: {announcement.get('id')}")
            print(f"      - Posted At: {announcement.get('posted_at', 'N/A')}")

        return announcements
    except Exception as e:
        print(f"\n  ❌ 공지사항 조회 실패: {str(e)}")
        return []


def get_submissions(base_url, token, course_id):
    """특정 과목의 제출물 조회"""
    print_section(f"Step 5: 과목 {course_id}의 제출물 조회")

    try:
        url = f"{base_url}/courses/{course_id}/students/submissions"
        headers = {'Authorization': f'Bearer {token}'}
        params = {
            'student_ids[]': 'all',
            'per_page': 5
        }

        print(f"  URL: {url}")

        response = requests.get(url, headers=headers, params=params, timeout=10)
        response.raise_for_status()

        submissions = response.json()

        # 제출된 것만 필터링
        submitted = [s for s in submissions if s.get('workflow_state') == 'submitted']

        if not submitted:
            print(f"\n  ⚠️  제출물이 없습니다. (총 {len(submissions)}개 중 제출 상태 0개)")
            return []

        print(f"\n  ✅ {len(submitted)}개의 제출물을 찾았습니다: (총 {len(submissions)}개 중)")
        for i, submission in enumerate(submitted[:5], 1):  # 최대 5개만 출력
            print(f"\n  [{i}] Assignment ID: {submission.get('assignment_id')}")
            print(f"      - Submitted At: {submission.get('submitted_at', 'N/A')}")
            print(f"      - Submission Type: {submission.get('submission_type', 'N/A')}")
            print(f"      - Workflow State: {submission.get('workflow_state', 'N/A')}")

        return submitted
    except Exception as e:
        print(f"\n  ❌ 제출물 조회 실패: {str(e)}")
        return []


def save_result(data, filename):
    """결과를 JSON 파일로 저장"""
    try:
        # 프로젝트 루트의 test-results 디렉토리에 저장 (tests/api/ 기준)
        root_dir = Path(__file__).parent.parent.parent
        results_dir = root_dir / 'test-results'
        results_dir.mkdir(exist_ok=True)
        
        filepath = results_dir / filename
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"\n  💾 결과 저장됨: {filepath}")
    except Exception as e:
        print(f"\n  ❌ 저장 실패: {str(e)}")


def main():
    print_header("Canvas API 연동 테스트")

    # 환경 변수에서 읽기
    canvas_token = os.getenv('CANVAS_API_TOKEN')
    canvas_base_url = os.getenv('CANVAS_API_BASE_URL', 'https://canvas.instructure.com/api/v1')

    # 토큰이 없으면 입력 받기
    if not canvas_token:
        print("\n❓ Canvas API 토큰을 입력하세요:")
        print("   (Canvas → Account → Settings → New Access Token)")
        canvas_token = input("\nToken: ").strip()

        if not canvas_token:
            print("\n❌ 토큰이 입력되지 않았습니다. 종료합니다.")
            sys.exit(1)

    print(f"\n📋 설정:")
    print(f"  - Base URL: {canvas_base_url}")
    print(f"  - Token: {canvas_token[:10]}...{canvas_token[-4:]}")

    # Step 1: 인증 테스트
    if not test_canvas_authentication(canvas_base_url, canvas_token):
        print("\n❌ 인증 실패로 인해 테스트를 중단합니다.")
        sys.exit(1)

    # Step 2: 과목 조회
    courses = get_courses(canvas_base_url, canvas_token)
    if not courses:
        print("\n⚠️  과목이 없어서 테스트를 중단합니다.")
        sys.exit(0)

    # 첫 번째 과목 선택
    selected_course = courses[0]
    course_id = selected_course['id']
    course_name = selected_course.get('name', 'Unnamed')

    print(f"\n✨ 테스트 대상 과목: {course_name} (ID: {course_id})")

    # Step 3-5: 과제, 공지, 제출물 조회
    assignments = get_assignments(canvas_base_url, canvas_token, course_id)
    announcements = get_announcements(canvas_base_url, canvas_token, course_id)
    submissions = get_submissions(canvas_base_url, canvas_token, course_id)

    # 결과 요약
    print_header("테스트 결과 요약")
    print(f"\n  ✅ 인증: 성공")
    print(f"  ✅ 과목 수: {len(courses)}개")
    print(f"  ✅ 과제 수: {len(assignments)}개")
    print(f"  ✅ 공지 수: {len(announcements)}개")
    print(f"  ✅ 제출물 수: {len(submissions)}개")

    # 결과를 JSON으로 저장
    print_section("결과 저장")

    result = {
        'tested_at': datetime.utcnow().isoformat(),
        'course': {
            'id': course_id,
            'name': course_name
        },
        'assignments': assignments,
        'announcements': announcements,
        'submissions': submissions
    }

    save_result(result, 'canvas-api-test-result.json')

    print("\n" + "=" * 60)
    print("  🎉 Canvas API 테스트 완료!")
    print("=" * 60 + "\n")


if __name__ == '__main__':
    main()