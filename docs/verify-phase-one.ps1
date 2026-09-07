param(
  [switch]$SkipFrontend,
  [switch]$SkipManualChecklist
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$aiPython = Join-Path $root 'ai\.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $aiPython)) {
  throw "AI virtual environment is missing. Run start-ai.bat once with Python 3.12, then rerun this script."
}

$aiPythonVersion = & $aiPython -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"
if ($aiPythonVersion.Trim() -ne '3.12') {
  throw "AI virtual environment must use Python 3.12, but found $($aiPythonVersion.Trim()). Delete ai/.venv, run start-ai.bat once, then rerun this script."
}

Push-Location (Join-Path $root 'ai')
& $aiPython -m pytest app\tests -q -p no:cacheprovider
Pop-Location

Push-Location (Join-Path $root 'backend')
& .\mvnw.cmd -q '-Dtest=AuthInterceptorTest,AgentRunServiceTest,AgentInsightsServiceTest' test
& .\mvnw.cmd -q -DskipTests compile
Pop-Location

if (-not $SkipFrontend) {
  Push-Location (Join-Path $root 'frontend')
  npm run build
  Pop-Location
}

if (-not $SkipManualChecklist) {
  Write-Host ""
  Write-Host "Manual two-account checklist:"
  Write-Host "1. Start Spring Boot, the AI service, and the frontend."
  Write-Host "2. Sign in as user A and submit one buyer Agent request."
  Write-Host "3. Sign in as user B: A's Run must be unreadable and uncleareable."
  Write-Host "4. As B, ask for A's order id: the Agent must not return that order."
  Write-Host "5. Stop the AI service or make the service tokens inconsistent: the UI must show basic-filter and a failed timeline step."
}
