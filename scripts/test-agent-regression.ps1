[CmdletBinding()]
param(
    [string]$MavenPath = 'mvn',
    [string]$MavenSettings,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$reportPath = Join-Path $workspace 'rag-backend/target/agent-regression-summary.json'
$suitePath = Join-Path $workspace 'rag-backend/target/surefire-reports/TEST-cn.sdh.backend.agent.AgentScenarioRegressionTest.xml'
$datasetPath = Join-Path $workspace 'rag-backend/src/test/resources/agent-regression/scenarios.json'
$arguments = @('-pl', 'rag-backend', '-am', '-Dtest=AgentScenarioRegressionTest',
    '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
if ($Offline) { $arguments = @('-o') + $arguments }
if ($MavenSettings) { $arguments = @('-s', (Resolve-Path -LiteralPath $MavenSettings).Path) + $arguments }

Push-Location -LiteralPath $workspace
try {
    # Remove only this gate's old summary so a failed run cannot leave a stale PASS artifact.
    if (Test-Path -LiteralPath $reportPath) { Remove-Item -LiteralPath $reportPath }
    $started = [DateTime]::UtcNow
    & $MavenPath @arguments
    if ($LASTEXITCODE -ne 0) { throw "Agent regression failed (Maven exit $LASTEXITCODE). See $suitePath" }
    if (-not (Test-Path -LiteralPath $suitePath)) { throw 'Missing JUnit regression report' }
    if ((Get-Item -LiteralPath $suitePath).LastWriteTimeUtc -lt $started) { throw 'Stale JUnit regression report' }
    [xml]$junit = Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8
    $suite = $junit.testsuite
    $cases = @(Get-Content -LiteralPath $datasetPath -Raw -Encoding UTF8 | ConvertFrom-Json)
    $results = @($suite.testcase)
    if ([int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0 -or
        $cases.Count -lt 30 -or $results.Count -ne ($cases.Count + 1)) {
        throw 'Agent regression gate did not execute every scenario and its dataset validation without skips'
    }
    $seen = @{}
    $validation = @($results | Where-Object { $_.name -eq 'datasetKeepsStableIdsAndAllRequiredCategories' })
    if ($validation.Count -ne 1) { throw 'Missing dataset validation result' }
    $scenarioResults = @(foreach ($result in $results) {
        if ($result.name -eq 'datasetKeepsStableIdsAndAllRequiredCategories') { continue }
        if ($result.name -notmatch '^scenario\(Scenario\)\[(\d+)\]$') { throw "Unexpected JUnit scenario name: $($result.name)" }
        $index = [int]$Matches[1] - 1
        if ($index -lt 0 -or $index -ge $cases.Count -or $seen.ContainsKey($index)) { throw 'Invalid or duplicate scenario index' }
        $seen[$index] = $true
        [ordered]@{ id = $cases[$index].id; kind = $cases[$index].kind; status = 'PASS'; junitName = $result.name; seconds = $result.time }
    })
    if ($seen.Count -ne $cases.Count) { throw 'Not all dataset scenarios have JUnit results' }
    [xml]$project = Get-Content -LiteralPath (Join-Path $workspace 'pom.xml') -Raw -Encoding UTF8
    $summary = [ordered]@{
        status = 'PASS'
        scope = 'scripted-model-sdk-contracts'
        completedAtUtc = [DateTime]::UtcNow.ToString('o')
        agentScopeVersion = [string]$project.project.properties.'agentscope.version'
        datasetSha256 = (Get-FileHash -LiteralPath $datasetPath -Algorithm SHA256).Hash
        scenarioCount = $cases.Count
        tests = [int]$suite.tests
        failures = [int]$suite.failures
        errors = [int]$suite.errors
        skipped = [int]$suite.skipped
        datasetValidation = 'PASS'
        scenarios = $scenarioResults
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Output "Agent regression PASS: $($cases.Count) scenarios. Summary: $reportPath"
} finally {
    Pop-Location
}
