#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UniSync Serverless 통합 테스트 런처

모든 테스트를 대화형 메뉴로 실행할 수 있습니다.
"""

import os
import sys
import subprocess
from pathlib import Path

# Windows 콘솔 UTF-8 인코딩 설정
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

# 프로젝트 루트 경로
ROOT_DIR = Path(__file__).parent.parent.parent
TEST_SCRIPTS_DIR = Path(__file__).parent
SERVERLESS_DIR = ROOT_DIR / 'app' / 'serverless'

# .env 파일 자동 로드
DOTENV_LOADED = False
try:
    from dotenv import load_dotenv
    env_path = ROOT_DIR / '.env'
    if env_path.exists():
        load_dotenv(env_path)
        DOTENV_LOADED = True
except ImportError:
    print("⚠️  python-dotenv가 설치되지 않았습니다.")
    print("  pip install python-dotenv")
except Exception as e:
    print(f"⚠️  .env 파일 로드 실패: {e}")


def print_header():
    """헤더 출력"""
    print("\n" + "=" * 70)
    print("  UniSync Serverless 통합 테스트 런처")
    print("=" * 70 + "\n")


def print_menu():
    """메뉴 출력"""
    print("테스트 유형을 선택하세요:\n")
    print("  [1] 단위 테스트 (Unit Tests)")
    print("      - 모든 Lambda 함수의 단위 테스트 실행")
    print("      - 실행 시간: ~2초 | 외부 의존성: 없음")
    print()
    print("  [2] Canvas API 테스트")
    print("      - 실제 Canvas API 연동 확인")
    print("      - 실행 시간: ~5초 | 외부 의존성: Canvas 토큰")
    print()
    print("  [3] LocalStack Lambda 통합 테스트")
    print("      - Lambda 배포 및 호출 테스트")
    print("      - 실행 시간: ~30초 | 외부 의존성: Docker")
    print()
    print("  [4] 모두 실행 (순차적으로)")
    print("      - 1 → 2 → 3 순서로 모든 테스트 실행")
    print()
    print("  [0] 종료")
    print()


def run_unit_tests():
    """단위 테스트 실행 (각 Lambda를 개별적으로 테스트)"""
    print("\n" + "=" * 70)
    print("  [1] 단위 테스트 실행")
    print("=" * 70 + "\n")

    try:
        # 각 Lambda를 개별적으로 테스트 (모듈 충돌 방지)
        lambdas = [
            ('Canvas Sync Lambda', SERVERLESS_DIR / 'canvas-sync-lambda' / 'tests'),
            ('LLM Lambda', SERVERLESS_DIR / 'llm-lambda' / 'tests')
        ]

        all_passed = True
        total_tests = 0
        
        for name, test_path in lambdas:
            print(f"\n🧪 테스트 중: {name}")
            print("-" * 50)
            
            cmd = [
                sys.executable, '-m', 'pytest',
                str(test_path),
                '-v',
                '--tb=short',
                '-q'  # quiet mode for cleaner output
            ]

            result = subprocess.run(cmd, cwd=ROOT_DIR)

            if result.returncode == 0:
                print(f"✅ {name} 테스트 통과")
            else:
                print(f"❌ {name} 테스트 실패")
                all_passed = False

        if all_passed:
            print("\n" + "=" * 70)
            print("✅ 모든 단위 테스트 통과!")
            print("=" * 70)
            return True
        else:
            print("\n" + "=" * 70)
            print("❌ 일부 테스트 실패")
            print("=" * 70)
            return False

    except FileNotFoundError:
        print("\n❌ pytest를 찾을 수 없습니다.")
        print("다음 명령어로 의존성을 설치하세요:")
        print("  pip install -r app/serverless/requirements-dev.txt")
        return False
    except Exception as e:
        print(f"\n❌ 에러 발생: {str(e)}")
        return False


def run_canvas_api_test():
    """Canvas API 테스트 실행"""
    print("\n" + "=" * 70)
    print("  [2] Canvas API 테스트 실행")
    print("=" * 70 + "\n")

    # 디버그: .env 로딩 상태 출력
    if DOTENV_LOADED:
        print("✅ .env 파일 로드됨\n")
    else:
        print("⚠️  .env 파일이 로드되지 않았습니다.\n")

    # Canvas 토큰 확인
    canvas_token = os.getenv('CANVAS_API_TOKEN')

    # 디버그: 토큰 값 확인
    if canvas_token:
        print(f"🔍 CANVAS_API_TOKEN 감지됨: {canvas_token[:10]}...{canvas_token[-4:]}\n")

    if not canvas_token or canvas_token == 'your-canvas-api-token-here':
        print("⚠️  Canvas API 토큰이 설정되지 않았습니다.")
        print("\n.env 파일에 CANVAS_API_TOKEN을 설정하거나,")
        print("지금 입력하시겠습니까? (y/n): ", end='')

        choice = input().strip().lower()
        if choice == 'y':
            canvas_token = input("\nCanvas API Token: ").strip()
            if canvas_token:
                os.environ['CANVAS_API_TOKEN'] = canvas_token
            else:
                print("\n❌ 토큰이 입력되지 않았습니다. 건너뜁니다.")
                return False
        else:
            print("\n⏭️  Canvas API 테스트를 건너뜁니다.")
            return False

    try:
        script_path = ROOT_DIR / 'tests' / 'api' / 'test_canvas_api.py'

        cmd = [sys.executable, str(script_path)]

        print(f"실행 명령: {' '.join(cmd)}\n")

        result = subprocess.run(cmd, cwd=ROOT_DIR)

        if result.returncode == 0:
            print("\n✅ Canvas API 테스트 성공!")
            return True
        else:
            print("\n❌ Canvas API 테스트 실패!")
            return False

    except Exception as e:
        print(f"\n❌ 에러 발생: {str(e)}")
        return False


def run_localstack_integration_test():
    """LocalStack 통합 테스트 실행"""
    print("\n" + "=" * 70)
    print("  [3] LocalStack Lambda 통합 테스트 실행")
    print("=" * 70 + "\n")

    # LocalStack 실행 여부 확인
    print("⚠️  이 테스트는 LocalStack이 실행 중이어야 합니다.")
    print("LocalStack이 실행 중입니까? (y/n): ", end='')

    choice = input().strip().lower()
    if choice != 'y':
        print("\n💡 다음 명령어로 LocalStack을 시작하세요:")
        print("  docker-compose up -d localstack")
        print("\n⏭️  LocalStack 통합 테스트를 건너뜁니다.")
        return False

    try:
        script_path = ROOT_DIR / 'tests' / 'integration' / 'test_lambda_integration.py'

        cmd = [sys.executable, str(script_path)]

        print(f"\n실행 명령: {' '.join(cmd)}\n")

        result = subprocess.run(cmd, cwd=ROOT_DIR)

        if result.returncode == 0:
            print("\n✅ LocalStack 통합 테스트 성공!")
            return True
        else:
            print("\n❌ LocalStack 통합 테스트 실패!")
            return False

    except Exception as e:
        print(f"\n❌ 에러 발생: {str(e)}")
        return False


def run_all_tests():
    """모든 테스트 순차 실행"""
    print("\n" + "=" * 70)
    print("  [4] 모든 테스트 순차 실행")
    print("=" * 70 + "\n")

    results = {
        '단위 테스트': False,
        'Canvas API 테스트': False,
        'LocalStack 통합 테스트': False
    }

    # 1. 단위 테스트
    print("\n[1/3] 단위 테스트 실행 중...")
    results['단위 테스트'] = run_unit_tests()

    input("\n계속하려면 Enter를 누르세요...")

    # 2. Canvas API 테스트
    print("\n[2/3] Canvas API 테스트 실행 중...")
    results['Canvas API 테스트'] = run_canvas_api_test()

    input("\n계속하려면 Enter를 누르세요...")

    # 3. LocalStack 통합 테스트
    print("\n[3/3] LocalStack 통합 테스트 실행 중...")
    results['LocalStack 통합 테스트'] = run_localstack_integration_test()

    # 결과 요약
    print("\n" + "=" * 70)
    print("  전체 테스트 결과 요약")
    print("=" * 70 + "\n")

    all_passed = True
    for test_name, passed in results.items():
        status = "✅ 성공" if passed else "❌ 실패"
        print(f"  {test_name}: {status}")
        if not passed:
            all_passed = False

    print("\n" + "=" * 70)
    if all_passed:
        print("  🎉 모든 테스트 통과!")
    else:
        print("  ⚠️  일부 테스트가 실패했습니다.")
    print("=" * 70 + "\n")

    return all_passed


def check_venv():
    """venv 활성화 여부 확인"""
    # venv가 활성화되어 있는지 확인
    if not hasattr(sys, 'real_prefix') and not (hasattr(sys, 'base_prefix') and sys.base_prefix != sys.prefix):
        print("\n⚠️  venv가 활성화되지 않은 것 같습니다.")
        print("\n다음 명령어로 venv를 활성화하세요:")
        print("  Windows: venv\\Scripts\\activate")
        print("  Linux/Mac: source venv/bin/activate")
        print("\nvenv 없이 계속하시겠습니까? (y/n): ", end='')

        choice = input().strip().lower()
        if choice != 'y':
            sys.exit(0)


def main():
    """메인 함수"""
    check_venv()

    print_header()

    while True:
        print_menu()

        choice = input("선택 (0-4): ").strip()

        if choice == '0':
            print("\n👋 종료합니다.\n")
            sys.exit(0)

        elif choice == '1':
            run_unit_tests()
            input("\n계속하려면 Enter를 누르세요...")

        elif choice == '2':
            run_canvas_api_test()
            input("\n계속하려면 Enter를 누르세요...")

        elif choice == '3':
            run_localstack_integration_test()
            input("\n계속하려면 Enter를 누르세요...")

        elif choice == '4':
            run_all_tests()
            input("\n계속하려면 Enter를 누르세요...")

        else:
            print("\n❌ 잘못된 선택입니다. 0-4 사이의 숫자를 입력하세요.\n")
            input("계속하려면 Enter를 누르세요...")


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n👋 사용자가 중단했습니다.\n")
        sys.exit(0)
    except Exception as e:
        print(f"\n\n❌ 예상치 못한 에러 발생: {str(e)}\n")
        sys.exit(1)