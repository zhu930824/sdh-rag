[CmdletBinding()]
param(
    [string]$ElasticsearchHome = $env:SDH_ES_IT_HOME,
    [string]$MavenPath = 'mvn',
    [string]$MavenSettings,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ElasticsearchHome) -or -not (Test-Path -LiteralPath (Join-Path $ElasticsearchHome 'bin/elasticsearch.bat') -PathType Leaf)) {
    throw 'Supply -ElasticsearchHome for an extracted Elasticsearch 8.18.5 Windows distribution. Tests start an isolated loopback node and never accept an existing cluster URL.'
}
$previousHome = $env:SDH_ES_IT_HOME
$arguments = @('-pl', 'rag-backend', '-am', '-Pes-it', 'verify')
if ($Offline) { $arguments = @('-o') + $arguments }
if ($MavenSettings) { $arguments = @('-s', (Resolve-Path -LiteralPath $MavenSettings).Path) + $arguments }
Push-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
try {
    $env:SDH_ES_IT_HOME = (Resolve-Path -LiteralPath $ElasticsearchHome).Path
    & $MavenPath @arguments
    if ($LASTEXITCODE -ne 0) { throw "Elasticsearch acceptance failed (Maven exit $LASTEXITCODE). See failsafe reports and the printed private evidence directory." }
} finally {
    $env:SDH_ES_IT_HOME = $previousHome
    Pop-Location
}
