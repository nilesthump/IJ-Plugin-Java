[CmdletBinding()]
param(
    [ValidateSet('Repository','All')][string]$Scope = 'All',
    [string]$BaseRef,
    [string]$Branch,
    [string]$LocalIdePath,
    [string]$GradleUserHome,
    [switch]$IdeSmoke
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot
& "$PSScriptRoot/check-repository.ps1"
if ($BaseRef) { & "$PSScriptRoot/check-scope.ps1" -BaseRef $BaseRef -Branch $Branch }
if ($Scope -eq 'All') {
    $gradleArgs = @('--no-daemon','--console=plain')
    if ($LocalIdePath) { $gradleArgs += "-PlocalIdePath=$LocalIdePath" }
    if ($GradleUserHome) { $gradleArgs += @('--gradle-user-home',$GradleUserHome) }
    $gradleArgs += @('check','buildPlugin')
    & "$repoRoot/gradlew.bat" @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle validation failed: $LASTEXITCODE" }
    if ($IdeSmoke) {
        $smokeReport = Join-Path $repoRoot 'build/reports/ide-smoke/result.json'
        if (Test-Path -LiteralPath $smokeReport) { Remove-Item -LiteralPath $smokeReport }
        $smokeArgs = $gradleArgs[0..($gradleArgs.Count - 3)] + @('-PideSmoke=true','runIde')
        & "$repoRoot/gradlew.bat" @smokeArgs
        if ($LASTEXITCODE -ne 0) { throw "IDE runtime validation failed: $LASTEXITCODE" }
        if (!(Test-Path -LiteralPath $smokeReport)) { throw 'IDE runtime report missing' }
        $report = Get-Content -LiteralPath $smokeReport -Raw | ConvertFrom-Json
        if ($report.status -ne 'PASS') { throw 'IDE runtime report is not PASS' }
    }
} elseif ($IdeSmoke) {
    throw '-IdeSmoke requires -Scope All'
}
