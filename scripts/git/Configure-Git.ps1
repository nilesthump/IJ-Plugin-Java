[CmdletBinding()]
param(
    [string] $RepositoryPath,
    [string] $RemoteName = 'origin'
)

$ErrorActionPreference = 'Stop'
if (-not $RepositoryPath) { $RepositoryPath = Join-Path $PSScriptRoot '../..' }
$repoPath = (Resolve-Path -LiteralPath $RepositoryPath).Path
$expectedRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
if (-not $repoPath.TrimEnd('\', '/').Equals($expectedRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Configure-Git must operate on the checkout containing this script. Use that checkout''s own script.'
}

function Invoke-RepositoryGit {
    param([Parameter(Mandatory)][string[]] $GitArguments)
    $result = @(& git -C $repoPath @GitArguments)
    if ($LASTEXITCODE -ne 0) {
        throw "Git operation failed ($LASTEXITCODE): git $($GitArguments -join ' ')"
    }
    return $result
}

$gitRoot = (Invoke-RepositoryGit -GitArguments @('rev-parse', '--show-toplevel')) -join ''
if (-not [IO.Path]::GetFullPath($gitRoot).TrimEnd('\', '/').Equals($repoPath.TrimEnd('\', '/'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'RepositoryPath must be the checkout root, not a nested directory.'
}
$allowedBranches = @('main', 'dev-manager', 'dev-a', 'dev-b', 'dev-c')
$localBranches = @(Invoke-RepositoryGit -GitArguments @('for-each-ref', '--format=%(refname:short)', 'refs/heads/'))
$unexpectedBranches = @($localBranches | Where-Object { $_ -cnotin $allowedBranches })
if ($unexpectedBranches.Count -gt 0) {
    throw "Unexpected local branches exist; no references were changed: $($unexpectedBranches -join ', ')"
}

# Install before creating missing fixed tracking refs; hooks permit these names.
$hooksPath = (Join-Path $repoPath '.githooks').Replace('\', '/')
foreach ($hookName in @('reference-transaction', 'pre-push', 'pre-commit')) {
    if (-not (Test-Path -LiteralPath (Join-Path $hooksPath $hookName) -PathType Leaf)) {
        throw "Missing repository hook: $hookName"
    }
}
Invoke-RepositoryGit -GitArguments @('config', '--local', 'core.hooksPath', $hooksPath) | Out-Null
Invoke-RepositoryGit -GitArguments @('config', '--local', 'push.default', 'simple') | Out-Null

foreach ($branchName in $allowedBranches) {
    if ($branchName -cnotin $localBranches) {
        $remoteRef = "refs/remotes/$RemoteName/$branchName"
        & git -C $repoPath show-ref --verify --quiet $remoteRef
        if ($LASTEXITCODE -eq 1) {
            throw "Missing fixed branch $branchName and $remoteRef. Fetch the repository's five branches, then rerun setup."
        }
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $remoteRef (Git exit $LASTEXITCODE)." }
        Invoke-RepositoryGit -GitArguments @('branch', '--track', $branchName, "$RemoteName/$branchName") | Out-Null
    }
}
$actualBranches = @(Invoke-RepositoryGit -GitArguments @('for-each-ref', '--format=%(refname:short)', 'refs/heads/'))
if ($actualBranches.Count -ne 5 -or @($actualBranches | Where-Object { $_ -cnotin $allowedBranches }).Count -ne 0) {
    throw 'Setup did not produce exactly the five fixed local branches.'
}
Write-Output 'Repository-local hooks enabled; five fixed branches verified. No global Git settings changed.'
Write-Output 'Pull requests from every dev branch target main. Keep all five branches after merges.'
