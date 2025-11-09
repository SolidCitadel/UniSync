#!/usr/bin/env python3
"""
LocalStack .env 값을 각 서비스의 application-local.yml에 동기화하는 스크립트

사용법:
    python scripts/dev/sync-local-config.py

설명:
    - 루트의 .env 파일에서 모든 환경변수를 읽음
    - 각 서비스의 src/main/resources/application-local.yml 파일을 업데이트
    - YAML 형식과 주석을 유지하면서 값만 교체
"""

import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple


# 서비스별 환경변수 매핑 (YAML 키 -> .env 키)
SERVICE_CONFIG_MAP = {
    "user-service": [
        ("password", "MYSQL_PASSWORD"),
        ("user-pool-id", "COGNITO_USER_POOL_ID"),
        ("client-id", "COGNITO_CLIENT_ID"),
        ("user-token-registered", "SQS_USER_TOKEN_REGISTERED_QUEUE"),
        ("key", "ENCRYPTION_KEY"),
        ("canvas-sync-lambda", "CANVAS_SYNC_API_KEY"),
        ("llm-lambda", "LLM_LAMBDA_API_KEY"),
        ("base-url", "CANVAS_BASE_URL"),
    ],
    "course-service": [
        ("password", "MYSQL_PASSWORD"),
        ("assignment-events", "SQS_ASSIGNMENT_EVENTS_QUEUE"),
        ("submission-events", "SQS_SUBMISSION_EVENTS_QUEUE"),
    ],
    "schedule-service": [
        ("password", "MYSQL_PASSWORD"),
        ("assignment-events", "SQS_ASSIGNMENT_EVENTS_QUEUE"),
        ("task-creation", "SQS_TASK_CREATION_QUEUE"),
    ],
    "api-gateway": [
        ("user-pool-id", "COGNITO_USER_POOL_ID"),
    ],
}


def load_env_file(env_path: Path) -> Dict[str, str]:
    """
    .env 파일에서 환경변수 읽기
    """
    env_vars = {}

    if not env_path.exists():
        print(f"[ERROR] .env 파일을 찾을 수 없습니다: {env_path}")
        sys.exit(1)

    with open(env_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            # 주석이나 빈 줄 무시
            if not line or line.startswith('#'):
                continue

            # KEY=VALUE 형식 파싱
            if '=' in line:
                key, value = line.split('=', 1)
                env_vars[key.strip()] = value.strip()

    return env_vars


def update_yaml_value(content: str, yaml_key: str, new_value: str, verbose: bool = True) -> Tuple[str, bool]:
    """
    YAML 파일에서 특정 키의 값을 업데이트

    Args:
        content: YAML 파일 내용
        yaml_key: 업데이트할 YAML 키
        new_value: 새로운 값
        verbose: 업데이트 메시지 출력 여부

    Returns:
        (업데이트된 YAML 내용, 업데이트 성공 여부)
    """
    # YAML 들여쓰기를 유지하면서 값만 교체
    # 패턴: "  key: value  # 주석"
    pattern = rf'^(\s*{re.escape(yaml_key)}:\s+)(.+?)(\s*(?:#.*)?$)'

    lines = content.split('\n')
    updated_lines = []
    updated = False

    for line in lines:
        match = re.match(pattern, line)
        if match:
            indent = match.group(1)
            comment = match.group(3)
            # 값만 교체, 들여쓰기와 주석은 유지
            updated_line = f"{indent}{new_value}{comment}"
            updated_lines.append(updated_line)
            if verbose:
                print(f"    [OK] {yaml_key}: {new_value}")
            updated = True
        else:
            updated_lines.append(line)

    return '\n'.join(updated_lines), updated


def update_service_config(service_name: str, service_path: Path, env_vars: Dict[str, str]) -> bool:
    """
    서비스의 application-local.yml 파일 업데이트

    Args:
        service_name: 서비스 이름
        service_path: 서비스 디렉토리 경로
        env_vars: .env에서 읽은 환경변수 딕셔너리

    Returns:
        성공 여부
    """
    config_path = service_path / "src" / "main" / "resources" / "application-local.yml"

    if not config_path.exists():
        print(f"  [SKIP] application-local.yml 없음: {service_name}")
        return False

    print(f"  [UPDATE] {service_name}")

    # 파일 읽기
    with open(config_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 서비스별 설정 매핑이 없으면 건너뛰기
    if service_name not in SERVICE_CONFIG_MAP:
        print(f"    [WARN] 설정 매핑 없음 (SERVICE_CONFIG_MAP에 추가 필요)")
        return False

    # 매핑된 환경변수들을 업데이트
    updated_any = False
    for yaml_key, env_key in SERVICE_CONFIG_MAP[service_name]:
        if env_key in env_vars:
            content, updated = update_yaml_value(content, yaml_key, env_vars[env_key])
            if updated:
                updated_any = True

    if not updated_any:
        print(f"    [WARN] 업데이트된 값 없음")
        return False

    # 파일 쓰기
    with open(config_path, 'w', encoding='utf-8') as f:
        f.write(content)

    return True


def remove_localstack_outputs(project_root: Path):
    """
    .localstack-outputs.yml 파일 제거 (더 이상 필요 없음)
    """
    outputs_file = project_root / ".localstack-outputs.yml"
    if outputs_file.exists():
        outputs_file.unlink()
        print("[INFO] .localstack-outputs.yml 파일 제거됨 (더 이상 필요 없음)")


def update_localstack_init_script(project_root: Path):
    """
    LocalStack 초기화 스크립트에서 .localstack-outputs.yml 생성 로직 제거
    """
    script_path = project_root / "localstack-init" / "02-create-cognito.sh"

    if not script_path.exists():
        return

    with open(script_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # .localstack-outputs.yml 관련 섹션 제거
    if ".localstack-outputs.yml" in content:
        # "# Output 파일 생성" 부터 스크립트 끝까지의 내용을 간단한 메시지로 교체
        pattern = r'# Output 파일 생성.*?EOF\n\necho ".*?$OUTPUT_FILE"'
        replacement = '''# 성공 메시지
echo "\\n.env 파일이 업데이트되었습니다."
echo "IDE에서 로컬 개발을 시작하려면 다음 명령을 실행하세요:"
echo "  python scripts/dev/sync-local-config.py"'''

        new_content = re.sub(pattern, replacement, content, flags=re.DOTALL | re.MULTILINE)

        # 마지막 정보 출력 섹션 업데이트
        new_content = re.sub(
            r'echo ""\necho "생성된 값은.*?"\necho "  \$OUTPUT_FILE"\necho ""',
            '',
            new_content,
            flags=re.DOTALL
        )

        new_content = re.sub(
            r'echo "다음 단계:"\necho "  1\. cat \.localstack-outputs\.yml 내용 확인"\necho "  2\. 각 서비스의 application-local\.yml에 복사"\necho "  3\. docker-compose -f docker-compose-app\.yml up"',
            'echo ""\necho "다음 단계:"\necho "  1. python scripts/dev/sync-local-config.py 실행"\necho "  2. IDE에서 서비스 실행 (Profile: local)"',
            new_content
        )

        with open(script_path, 'w', encoding='utf-8') as f:
            f.write(new_content)

        print("[INFO] LocalStack 초기화 스크립트 업데이트됨 (02-create-cognito.sh)")


def main():
    """
    메인 실행 함수
    """
    print("=" * 60)
    print("LocalStack 설정 동기화 스크립트")
    print("=" * 60)
    print()

    # 프로젝트 루트 찾기
    script_dir = Path(__file__).parent.resolve()
    project_root = script_dir.parent.parent  # scripts/dev/ -> scripts/ -> root/

    # .env 파일 읽기
    env_path = project_root / ".env"
    print(f"[INFO] .env 파일 읽는 중: {env_path}")
    env_vars = load_env_file(env_path)

    # 필수 값 확인
    required_vars = [
        'COGNITO_USER_POOL_ID',
        'COGNITO_CLIENT_ID',
        'MYSQL_PASSWORD',
        'ENCRYPTION_KEY',
    ]
    missing_vars = [var for var in required_vars if var not in env_vars]

    if missing_vars:
        print(f"[ERROR] .env 파일에 필수 값이 없습니다: {', '.join(missing_vars)}")
        sys.exit(1)

    print(f"[OK] User Pool ID: {env_vars.get('COGNITO_USER_POOL_ID', 'N/A')}")
    print(f"[OK] Client ID: {env_vars.get('COGNITO_CLIENT_ID', 'N/A')}")
    print(f"[OK] 환경변수 {len(env_vars)}개 로드됨")
    print()

    # 서비스 디렉토리 찾기
    backend_dir = project_root / "app" / "backend"

    if not backend_dir.exists():
        print(f"[ERROR] 백엔드 디렉토리를 찾을 수 없습니다: {backend_dir}")
        sys.exit(1)

    # 각 서비스 업데이트
    print("[INFO] 서비스 설정 파일 업데이트 중...")
    print()

    services = [
        "user-service",
        "course-service",
        "schedule-service",
        "api-gateway"
    ]

    updated_count = 0

    for service_name in services:
        service_path = backend_dir / service_name
        if service_path.exists():
            if update_service_config(service_name, service_path, env_vars):
                updated_count += 1
            print()

    # 정리 작업
    print("[INFO] 정리 작업 중...")
    remove_localstack_outputs(project_root)
    update_localstack_init_script(project_root)
    print()

    # 결과 출력
    print("=" * 60)
    if updated_count > 0:
        print(f"[SUCCESS] {updated_count}개 서비스 설정 파일 업데이트 완료!")
        print()
        print("다음 단계:")
        print("  1. IDE를 재시작하거나 프로젝트를 다시 로드")
        print("  2. Active Profile이 'local'로 설정되어 있는지 확인")
        print("  3. 서비스 실행")
    else:
        print("[WARN] 업데이트된 파일이 없습니다.")
    print("=" * 60)


if __name__ == "__main__":
    main()
