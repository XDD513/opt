@echo off
setlocal enableextensions

rem ==========================================================
rem 1000用户全流程（批量） -> 验收报告（图+表）
rem 运行前确保：
rem - 后端已启动：http://127.0.0.1:8000
rem - D:\Desktop\hospital\acceptance-tests 已 npm install 且 .env 配置完毕
rem ==========================================================

set "ROOT=D:\Desktop\hospital"
set "BASE_URL=http://127.0.0.1:8000"
set "USERNAME_PREFIX=boot5"
set "START_SEQ=1"
set "COUNT=1000"
set "CONCURRENCY=50"

echo.
echo [1/2] Batch fullflow: %COUNT% users, concurrency=%CONCURRENCY%
cd /d "%ROOT%\batch-fullflow" || goto :error

rem 若需要可先执行：npm install
node "batch-fullflow-users.js" --base-url=%BASE_URL% --username-prefix=%USERNAME_PREFIX% --start-seq=%START_SEQ% --count=%COUNT% --concurrency=%CONCURRENCY%
if errorlevel 1 goto :error

echo.
echo [2/2] Acceptance report: generate tables & charts
cd /d "%ROOT%\acceptance-tests" || goto :error

rem 若第一次运行需要：npm install
npm run acceptance
if errorlevel 1 goto :error

echo.
echo Done.
echo Reports:
echo - %ROOT%\acceptance-tests\reports\acceptance-summary.md
echo - %ROOT%\acceptance-tests\reports\acceptance-report.html
echo - %ROOT%\acceptance-tests\reports\batch-1000-user-duration-histogram.svg
echo.
exit /b 0

:error
echo.
echo ERROR: command failed with exit code %errorlevel%.
echo Check console output and logs under:
echo - %ROOT%\batch-fullflow\output\
echo - %ROOT%\acceptance-tests\reports\
echo.
exit /b %errorlevel%

