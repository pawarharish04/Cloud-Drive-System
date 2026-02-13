@echo off
set "PATH=C:\Users\Lenovo\tools\apache-maven-3.9.6\bin;%PATH%"

echo =========================================
set "TESTCONTAINERS_RYUK_DISABLED=true"
echo Cloud Drive System - Integration Test Runner
echo =========================================

where mvn
if %errorlevel% neq 0 (
    echo [ERROR] Maven not found!
    exit /b 1
)

echo.
echo [INFO] Testing Metadata Service (Logging to metadata_test_output.txt)...
cd metadata-service
call mvn test > metadata_test_output.txt 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Metadata Service tests failed! Check metadata_test_output.txt
) else (
    echo [SUCCESS] Metadata Service tests passed!
)
cd ..

echo.
echo [INFO] Testing File Service (Logging to file_test_output.txt)...
cd file-service
call mvn test > file_test_output.txt 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] File Service tests failed! Check file_test_output.txt
) else (
    echo [SUCCESS] File Service tests passed!
)
cd ..

echo.
echo =========================================
echo Done.
echo =========================================
