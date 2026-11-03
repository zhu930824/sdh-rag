[CmdletBinding()]
param(
    [string]$MysqldPath = $env:SDH_MYSQL_IT_MYSQLD,
    [string]$MavenPath = 'mvn',
    [string]$MavenSettings,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($MysqldPath) -or -not (Test-Path -LiteralPath $MysqldPath -PathType Leaf)) {
    throw 'Supply -MysqldPath pointing to a MySQL 8.x mysqld executable. The suite owns a fresh server and does not connect to an existing database.'
}
$resolvedServer = (Resolve-Path -LiteralPath $MysqldPath).Path
$previousServer = $env:SDH_MYSQL_IT_MYSQLD
$workspace = Split-Path -Parent $PSScriptRoot
$arguments = @('-pl', 'rag-backend', '-am', '-Pmysql-it', 'verify')
if ($Offline) { $arguments = @('-o') + $arguments }
if ($MavenSettings) { $arguments = @('-s', (Resolve-Path -LiteralPath $MavenSettings).Path) + $arguments }
Push-Location -LiteralPath $workspace
try {
    $env:SDH_MYSQL_IT_MYSQLD = $resolvedServer
    & $MavenPath @arguments
    if ($LASTEXITCODE -ne 0) { throw "MySQL acceptance failed (Maven exit $LASTEXITCODE). See rag-backend/target/failsafe-reports and the printed evidence directory." }
} finally {
    $env:SDH_MYSQL_IT_MYSQLD = $previousServer
    Pop-Location
}
