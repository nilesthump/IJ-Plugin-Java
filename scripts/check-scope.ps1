[CmdletBinding()]
param([switch]$Staged, [string]$BaseRef, [string]$Branch,
      [string]$RepositoryPath, [string]$PolicyPath)
$ErrorActionPreference = 'Stop'
if (-not $RepositoryPath) { $RepositoryPath = Split-Path -Parent $PSScriptRoot }
$repoRoot = (Resolve-Path -LiteralPath $RepositoryPath).Path
Set-Location -LiteralPath $repoRoot
if (-not $Branch) {
    $Branch = (& git symbolic-ref --quiet --short HEAD)
    if ($LASTEXITCODE -ne 0) { throw 'Detached HEAD: specify -Branch only for CI review, never commit here.' }
}
if (-not $PolicyPath) { $PolicyPath = Join-Path $repoRoot '.github/branch-scopes.json' }
$policy = Get-Content -LiteralPath $PolicyPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($Branch -notin $policy.branches) { throw "Forbidden branch: $Branch" }
if ($Branch -eq 'main') { throw 'main accepts reviewed PRs. Develop on the assigned existing dev branch.' }
if ($Staged) {
    $changed = @(& git -c core.quotePath=false diff --cached --name-only --no-renames --diff-filter=ACDMRTUXB)
} elseif ($BaseRef) {
    & git rev-parse --verify "$BaseRef^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve base: $BaseRef" }
    $changed = @(& git -c core.quotePath=false diff --name-only --no-renames --diff-filter=ACDMRTUXB "$BaseRef...HEAD")
} else { throw 'Provide -Staged or -BaseRef.' }
if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate changed paths.' }
foreach ($path in $changed) {
    if (-not $path) { continue }
    if ($Branch -eq 'dev-manager') {
        $outside = @($policy.managerExcluded | Where-Object { $path.StartsWith($_, [StringComparison]::Ordinal) }).Count -gt 0
    } else {
        $allowed = $policy.owners.$Branch
        $outside = @($allowed | Where-Object { $path.StartsWith($_, [StringComparison]::Ordinal) }).Count -eq 0
    }
    if ($outside) { throw "Outside $Branch write scope: $path. Record a request in your task; do not edit shared files." }
}
Write-Output "Write scope passed for $Branch ($($changed.Count) paths)."
