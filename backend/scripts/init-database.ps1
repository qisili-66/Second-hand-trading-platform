param(
  [string] $MySqlHost = "127.0.0.1",
  [string] $MySqlUser = $env:DB_USERNAME,
  [string] $MySqlPassword = $env:DB_PASSWORD,
  [string] $Database = "second_hand_trade"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($MySqlUser) -or [string]::IsNullOrWhiteSpace($MySqlPassword)) {
  throw "Set DB_USERNAME and DB_PASSWORD before running this script."
}

$backendRoot = Split-Path -Parent $PSScriptRoot
$schemaFile = Join-Path $backendRoot "sql\schema.sql"
$seedFile = Join-Path $backendRoot "sql\seed_data.sql"

$mysqlCommand = "mysql"

if (-not (Get-Command $mysqlCommand -ErrorAction SilentlyContinue)) {
  throw "mysql command was not found. Please add MySQL bin directory to PATH and retry."
}

function Invoke-MysqlFile {
  param(
    [Parameter(Mandatory = $true)]
    [string] $SqlFile
  )

  $mysqlPath = (Get-Command $mysqlCommand).Source
  $previousMySqlPassword = $env:MYSQL_PWD
  $env:MYSQL_PWD = $MySqlPassword
  $command = "`"$mysqlPath`" --host=$MySqlHost --user=$MySqlUser --default-character-set=utf8mb4 --binary-mode $Database < `"$SqlFile`""

  try {
    cmd.exe /c $command
  } finally {
    $env:MYSQL_PWD = $previousMySqlPassword
  }

  if ($LASTEXITCODE -ne 0) {
    throw "Failed to execute SQL file: $SqlFile"
  }
}

Write-Host "Creating database and tables from $schemaFile"
Invoke-MysqlFile -SqlFile $schemaFile

Write-Host "Seeding initial data from $seedFile"
Invoke-MysqlFile -SqlFile $seedFile

Write-Host "Database $Database is ready."
