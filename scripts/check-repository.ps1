[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot
$required = @('AGENTS.md','CLAUDE.md','README.md','build.gradle','settings.gradle','gradlew','gradlew.bat',
    'gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties',
    'spec/progress/CURRENT.md','spec/architecture/BASELINE.md','spec/contracts/API.md',
    'spec/governance/WRITE_SCOPE.md','spec/governance/WORKFLOW.md','spec/governance/BRANCH_POLICY.md',
    'spec/governance/VALIDATION.md','.github/branch-scopes.json','.github/workflows/validation.yml')
foreach ($file in $required) { if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing baseline file: $file" } }
foreach ($branch in @('dev-manager','dev-a','dev-b','dev-c')) {
    foreach ($file in @('README.md','STATUS.md','HANDOFF.md')) {
        if (-not (Test-Path -LiteralPath "spec/tasks/$branch/$file")) { throw "Missing task file: $branch/$file" }
    }
}
$policy = Get-Content '.github/branch-scopes.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$expected = @('dev-a','dev-b','dev-c','dev-manager','main')
if (@(Compare-Object ($policy.branches | Sort-Object) $expected).Count -gt 0) { throw 'Branch policy must name exactly five fixed branches.' }
$localBranches = @(& git for-each-ref '--format=%(refname:short)' refs/heads)
if ($LASTEXITCODE -ne 0) { throw 'Git repository is not initialized.' }
foreach ($branch in $localBranches) { if ($branch -notin $expected) { throw "Unexpected local branch: $branch" } }
[xml]$descriptor = Get-Content 'src/main/resources/META-INF/plugin.xml' -Raw -Encoding UTF8
if ($descriptor.'idea-plugin'.id -ne 'edu.courseassist.f0') { throw 'Plugin identity mismatch.' }
if (Get-ChildItem 'src/main' -Recurse -Filter '*.kt') { throw 'Business sources must be pure Java.' }
# Raw discussion state and source replies remain local to the coordinator, outside public Git.
if (Test-Path -LiteralPath 'spec/architecture/WORKING.json') {
    $working = Get-Content 'spec/architecture/WORKING.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($working.phase -eq 'frozen' -and -not (Test-Path 'spec/architecture/CURRENT')) { throw 'Frozen state requires formal architecture artifacts.' }
}
Write-Output 'Repository baseline passed. This is not an IDE runtime or API capability test.'
