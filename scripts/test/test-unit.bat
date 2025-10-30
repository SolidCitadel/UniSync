@echo off
chcp 65001 >nul
REM UTF-8 encoding for Korean characters

echo ======================================================================
echo   UniSync Lambda Unit Tests
echo ======================================================================
echo.

REM Canvas Sync Lambda Test
echo [1/2] Testing Canvas Sync Lambda...
venv\Scripts\python.exe -m pytest app\serverless\canvas-sync-lambda\tests -v --tb=short
set CANVAS_RESULT=%ERRORLEVEL%

echo.
echo ----------------------------------------------------------------------
echo.

REM LLM Lambda Test
echo [2/2] Testing LLM Lambda...
venv\Scripts\python.exe -m pytest app\serverless\llm-lambda\tests -v --tb=short
set LLM_RESULT=%ERRORLEVEL%

echo.
echo ======================================================================

REM Summary
if %CANVAS_RESULT%==0 if %LLM_RESULT%==0 (
    echo.
    echo [SUCCESS] All tests passed!
    echo   - Canvas Sync Lambda: 8 tests passed
    echo   - LLM Lambda: 11 tests passed
    echo.
    exit /b 0
) else (
    echo.
    echo [FAILED] Some tests failed
    if not %CANVAS_RESULT%==0 echo   - Canvas Sync Lambda: FAILED
    if not %LLM_RESULT%==0 echo   - LLM Lambda: FAILED
    echo.
    exit /b 1
)