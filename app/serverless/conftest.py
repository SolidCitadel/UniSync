"""
Serverless 테스트 전역 설정
각 Lambda 테스트 파일 수집 시 모듈 캐시를 정리하여 충돌 방지
"""
import sys
import pytest


# 마지막으로 테스트한 Lambda 추적
_last_lambda_dir = None


def pytest_collection_modifyitems(session, config, items):
    """
    테스트 수집 완료 후, 각 테스트 아이템에 setup hook 추가
    """
    pass


def pytest_runtest_protocol(item, nextitem):
    """
    각 테스트 아이템 실행 전 호출
    다른 Lambda로 전환 시 src.* 모듈 캐시 제거
    """
    global _last_lambda_dir
    
    # 현재 테스트 파일 경로
    test_file = str(item.fspath)
    
    # 현재 Lambda 디렉토리 추출
    if 'canvas-sync-lambda' in test_file:
        current_lambda = 'canvas-sync-lambda'
    elif 'llm-lambda' in test_file:
        current_lambda = 'llm-lambda'
    else:
        current_lambda = None
    
    # Lambda가 바뀌면 모듈 캐시 정리
    if current_lambda and current_lambda != _last_lambda_dir:
        # src.* 모듈 모두 제거
        modules_to_remove = [key for key in list(sys.modules.keys()) if key.startswith('src')]
        for module in modules_to_remove:
            del sys.modules[module]
        
        _last_lambda_dir = current_lambda
    
    # 기본 프로토콜 실행
    return None

