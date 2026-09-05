@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"

if not exist "%PROJECT_DIR%ai\start.bat" (
  echo [ERROR] Cannot find ai\start.bat. Run this file from the project root.
  pause
  exit /b 1
)

call "%PROJECT_DIR%ai\start.bat"
endlocal
