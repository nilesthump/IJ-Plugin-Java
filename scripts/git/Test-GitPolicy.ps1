[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$testRoot = Join-Path $sourceRoot ('tmp/bootstrap-git-tests/run-' + [guid]::NewGuid().ToString('N'))
$repoPath = Join-Path $testRoot 'checkout'
$barePath = Join-Path $testRoot 'remote.git'
$clonePath = Join-Path $testRoot 'clone'
$worktreePath = Join-Path $testRoot 'detached-worktree'
New-Item -ItemType Directory -Path $repoPath -Force | Out-Null
$results = [Collections.Generic.List[object]]::new()

function Invoke-TestGit {
    param([string] $Name, [string] $WorkingDirectory, [string[]] $GitArguments, [bool] $ShouldSucceed = $true)
    # Captured native output allows expected rejections without changing error policy.
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = (Get-Command git -ErrorAction Stop).Source
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $GitArguments) { $startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw "Cannot start Git test: $Name" }
    $standardOutput = $process.StandardOutput.ReadToEndAsync()
    $standardError = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $output = $standardOutput.Result + $standardError.Result
    $actualSuccess = $process.ExitCode -eq 0
    $entry = [ordered]@{
        name = $Name; arguments = $GitArguments; expectedSuccess = $ShouldSucceed
        exitCode = $process.ExitCode; passed = ($actualSuccess -eq $ShouldSucceed); output = $output.Trim()
    }
    $results.Add([pscustomobject] $entry)
    if (-not $entry.passed) { throw "Git policy test failed: $Name`n$output" }
    return $output.Trim()
}

Invoke-TestGit 'init checkout' $repoPath @('init', '-b', 'main') | Out-Null
Invoke-TestGit 'local identity name' $repoPath @('config', '--local', 'user.name', 'Branch Policy Test') | Out-Null
Invoke-TestGit 'local identity email' $repoPath @('config', '--local', 'user.email', 'branch-policy-test@example.invalid') | Out-Null
[IO.File]::WriteAllText((Join-Path $repoPath 'fixture.txt'), "initial`n")
Invoke-TestGit 'stage fixture' $repoPath @('add', 'fixture.txt') | Out-Null
Invoke-TestGit 'initial commit' $repoPath @('commit', '-m', 'test fixture') | Out-Null
$initialCommit = Invoke-TestGit 'record initial commit' $repoPath @('rev-parse', 'HEAD')
foreach ($branchName in @('dev-manager', 'dev-a', 'dev-b', 'dev-c')) {
    Invoke-TestGit "create fixed $branchName" $repoPath @('branch', $branchName) | Out-Null
}
New-Item -ItemType Directory -Path (Join-Path $repoPath '.githooks'), (Join-Path $repoPath 'scripts/git') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $sourceRoot '.githooks/reference-transaction'), (Join-Path $sourceRoot '.githooks/pre-push'), (Join-Path $sourceRoot '.githooks/pre-commit'), (Join-Path $sourceRoot '.githooks/pre-merge-commit') -Destination (Join-Path $repoPath '.githooks')
Copy-Item -LiteralPath (Join-Path $sourceRoot 'scripts/git/Configure-Git.ps1') -Destination (Join-Path $repoPath 'scripts/git')
# This fixture checks hook delegation only. The production scope validator is
# validated separately against the repository's actual ownership mapping.
$scopeFixture = @'
param([switch] $Staged)
if (-not $Staged.IsPresent) { exit 20 }
$branchName = & git symbolic-ref --quiet --short HEAD
if ($LASTEXITCODE -ne 0 -or $branchName -eq 'main') { exit 21 }
$paths = @(& git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { exit 22 }
if (@($paths | Where-Object { $_ -ne 'fixture.txt' }).Count -gt 0) { exit 23 }
exit 0
'@
[IO.File]::WriteAllText((Join-Path $repoPath 'scripts/check-scope.ps1'), $scopeFixture, [Text.UTF8Encoding]::new($false))
& (Join-Path $repoPath 'scripts/git/Configure-Git.ps1') -RepositoryPath $repoPath
Invoke-TestGit 'pre-commit delegates main rejection' $repoPath @('commit', '--allow-empty', '-m', 'must be rejected on main') $false | Out-Null
Invoke-TestGit 'create extra branch rejected' $repoPath @('branch', 'feature-extra') $false | Out-Null
Invoke-TestGit 'create extra via switch rejected' $repoPath @('switch', '-c', 'feature-switch') $false | Out-Null
Invoke-TestGit 'create extra via update-ref rejected' $repoPath @('update-ref', 'refs/heads/feature-direct', 'HEAD') $false | Out-Null
Invoke-TestGit 'delete fixed branch rejected' $repoPath @('branch', '-D', 'dev-a') $false | Out-Null
Invoke-TestGit 'rename fixed branch rejected' $repoPath @('branch', '-m', 'dev-a', 'feature-renamed') $false | Out-Null
Invoke-TestGit 'detach HEAD allowed' $repoPath @('switch', '--detach', 'HEAD') | Out-Null
Invoke-TestGit 'reattach fixed branch allowed' $repoPath @('switch', 'dev-manager') | Out-Null
[IO.File]::WriteAllText((Join-Path $repoPath 'outside-scope.txt'), "must be rejected`n")
Invoke-TestGit 'stage out-of-scope fixture' $repoPath @('add', 'outside-scope.txt') | Out-Null
Invoke-TestGit 'pre-commit forwards scope failure' $repoPath @('commit', '-m', 'must be rejected by scope fixture') $false | Out-Null
Invoke-TestGit 'unstage scope fixture' $repoPath @('restore', '--staged', 'outside-scope.txt') | Out-Null
Invoke-TestGit 'detached worktree allowed' $repoPath @('worktree', 'add', '--detach', $worktreePath, 'HEAD') | Out-Null
Invoke-TestGit 'remote tracking update allowed' $repoPath @('update-ref', 'refs/remotes/test/feature-upstream', 'HEAD') | Out-Null
Invoke-TestGit 'init bare test remote' $testRoot @('init', '--bare', $barePath) | Out-Null
Invoke-TestGit 'attach test remote' $repoPath @('remote', 'add', 'origin', $barePath) | Out-Null
Invoke-TestGit 'push fixed branches allowed' $repoPath @('push', 'origin', '--all') | Out-Null
Invoke-TestGit 'push additional remote branch rejected' $repoPath @('push', 'origin', 'HEAD:refs/heads/feature-push') $false | Out-Null
Invoke-TestGit 'push fixed deletion rejected' $repoPath @('push', 'origin', '--delete', 'dev-a') $false | Out-Null
Invoke-TestGit 'test remote branch for tracking only' $barePath @('update-ref', 'refs/heads/external-only', $initialCommit) | Out-Null
Invoke-TestGit 'fetch foreign remote tracking branch allowed' $repoPath @('fetch', 'origin') | Out-Null
[IO.File]::WriteAllText((Join-Path $repoPath 'fixture.txt'), "second`n")
Invoke-TestGit 'stage second fixture' $repoPath @('add', 'fixture.txt') | Out-Null
Invoke-TestGit 'fixed branch commit allowed' $repoPath @('commit', '-m', 'test second commit') | Out-Null
Invoke-TestGit 'fast forward push allowed' $repoPath @('push', 'origin', 'dev-manager') | Out-Null
Invoke-TestGit 'forced rewind push rejected' $repoPath @('push', '--force', 'origin', "${initialCommit}:refs/heads/dev-manager") $false | Out-Null
$localBranches = Invoke-TestGit 'verify local branch set' $repoPath @('for-each-ref', '--format=%(refname:short)', 'refs/heads/')
if (($localBranches -split '\r?\n').Count -ne 5) { throw 'Test checkout must retain exactly five local branches.' }

# Exercise teammate clone setup: one initial local branch, five fixed tracking refs.
Invoke-TestGit 'clone teammate branch' $testRoot @('clone', '--branch', 'dev-a', $barePath, $clonePath) | Out-Null
New-Item -ItemType Directory -Path (Join-Path $clonePath '.githooks'), (Join-Path $clonePath 'scripts/git') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $sourceRoot '.githooks/reference-transaction'), (Join-Path $sourceRoot '.githooks/pre-push'), (Join-Path $sourceRoot '.githooks/pre-commit'), (Join-Path $sourceRoot '.githooks/pre-merge-commit') -Destination (Join-Path $clonePath '.githooks')
Copy-Item -LiteralPath (Join-Path $sourceRoot 'scripts/git/Configure-Git.ps1') -Destination (Join-Path $clonePath 'scripts/git')
& (Join-Path $clonePath 'scripts/git/Configure-Git.ps1') -RepositoryPath $clonePath
$cloneBranches = Invoke-TestGit 'clone setup retains fixed five only' $clonePath @('for-each-ref', '--format=%(refname:short)', 'refs/heads/')
if (($cloneBranches -split '\r?\n').Count -ne 5) { throw 'Clone setup did not produce exactly five local branches.' }

$report = [ordered]@{
    testedAt = [DateTimeOffset]::Now.ToString('o'); gitVersion = (& git --version)
    testRoot = $testRoot; successful = $true; operations = $results.ToArray()
    limits = @('Only local Git hooks and a local bare remote were tested; GitHub enforcement requires separate API verification.', 'The pre-commit uses an isolated scope fixture to test delegation and exit propagation; production path scope rules require separate validation.')
}
$reportPath = Join-Path $testRoot 'results.json'
[IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10) + "`n", [Text.UTF8Encoding]::new($false))
Write-Output "Passed $($results.Count) Git operations. Report: $reportPath"
