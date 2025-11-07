@echo off
REM E2E 테스트 실행 스크립트 (Windows)

echo ================================================
echo E2E 테스트 환경 확인 중...
echo ================================================

REM 스택이 이미 실행 중인지 확인
docker ps | findstr "unisync-test-localstack" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] 스택이 이미 실행 중입니다. 테스트만 실행합니다.
    echo.
    goto run_tests
)

echo.
echo [INFO] 스택을 시작합니다...
echo.
docker-compose -f docker-compose.test.yml up -d

echo.
echo [INFO] LocalStack 초기화 대기 중 (15초)...
timeout /t 15 /nobreak >nul

echo.
echo [INFO] 서비스 재시작 (업데이트된 .env 적용)...
docker-compose -f docker-compose.test.yml stop user-service api-gateway
docker-compose -f docker-compose.test.yml up -d user-service api-gateway

echo.
echo [INFO] 서비스 준비 대기 중 (20초)...
timeout /t 20 /nobreak >nul

:run_tests
echo.
echo ================================================
echo E2E 테스트 실행...
echo ================================================
echo.
cd tests\e2e
pytest -v -s
cd ..\..

echo.
echo ================================================
echo 테스트 완료!
echo ================================================
echo.
echo [TIP] Clean test가 필요하면:
echo   docker-compose -f docker-compose.test.yml down -v
echo   scripts\test-e2e.bat
echo ================================================
