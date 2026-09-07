@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "AI_DIR=%PROJECT_DIR%ai\"
set "VENV_DIR=%AI_DIR%.venv"
set "PYTHON_CMD="

cd /d "%AI_DIR%" || exit /b 1

echo.
echo [Campus AI] Starting local AI service...
echo.

if not exist "%AI_DIR%app\main.py" (
  echo [ERROR] Cannot find ai\app\main.py. Run this file from the project root.
  pause
  exit /b 1
)

py -3.12 --version >nul 2>&1
if %ERRORLEVEL%==0 (
  set "PYTHON_CMD=py -3.12"
  goto :python_found
)

for %%P in (
  "%LocalAppData%\Programs\Python\Python312\python.exe"
  "%ProgramFiles%\Python312\python.exe"
  "%ProgramFiles(x86)%\Python312\python.exe"
  "C:\Python312\python.exe"
) do (
  if exist %%~P (
    set "PYTHON_CMD=%%~P"
    goto :python_found
  )
)

echo [ERROR] Python 3.12 was not found.
echo Please install Python 3.12, then run this file again.
echo Avoid Python 3.14 for this AI service because FastAPI/LangChain dependencies may fail.
pause
exit /b 1

:python_found
echo [OK] Python command: %PYTHON_CMD%
%PYTHON_CMD% --version

if not exist "%VENV_DIR%\Scripts\python.exe" (
  echo.
  echo [Campus AI] Creating virtual environment: .venv
  %PYTHON_CMD% -m venv "%VENV_DIR%"
  if errorlevel 1 (
    echo [ERROR] Failed to create virtual environment.
    pause
    exit /b 1
  )
)

set "VENV_PY=%VENV_DIR%\Scripts\python.exe"

echo.
echo [Campus AI] Synchronizing Python dependencies...
echo [Campus AI] pip will reuse already installed compatible packages.
"%VENV_PY%" -m pip install --upgrade pip
if errorlevel 1 (
  echo [WARN] Failed to upgrade pip. Continuing with current pip...
)

"%VENV_PY%" -m pip install -r "%AI_DIR%requirements.txt"
if errorlevel 1 (
  echo [ERROR] Dependency installation failed.
  echo You can try running this script again, or check your network / pip mirror.
  pause
  exit /b 1
)

if not exist "%AI_DIR%.env" (
  echo.
  echo [Campus AI] Creating .env from .env.example...
  copy "%AI_DIR%.env.example" "%AI_DIR%.env" >nul
  echo [WARN] .env has been created. Fill EXTERNAL_LLM_API_KEY before using model-enhanced output.
  echo [WARN] Without an API key, the AI service still runs with rule-based fallback.
)

echo.
echo [Campus AI] Service URL: http://127.0.0.1:8001
echo [Campus AI] Health URL:  http://127.0.0.1:8001/health
echo [Campus AI] Press Ctrl+C to stop.
echo.

"%VENV_PY%" -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001

echo.
echo [Campus AI] Service stopped.
pause
