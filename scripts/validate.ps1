[CmdletBinding()]
param(
    [ValidateSet('Repository','All')][string]$Scope = 'All',
    [string]$BaseRef,
    [string]$Branch,
    [string]$LocalIdePath,
    [string]$GradleUserHome
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
}
