[CmdletBinding()]
param(
    [Uri]$BaseUrl = 'https://agi.tongji.edu.cn/v1',
    [string]$Model = 'Qwen3-Embedding'
)
$ErrorActionPreference = 'Stop'
if (!$BaseUrl.IsAbsoluteUri -or $BaseUrl.UserInfo -or $BaseUrl.Query -or $BaseUrl.Fragment -or
    ($BaseUrl.Scheme -ne 'https' -and !($BaseUrl.Scheme -eq 'http' -and $BaseUrl.IsLoopback))) {
    throw 'Use an HTTPS base URL without credentials, query or fragment (HTTP is allowed only for loopback tests).'
}
if ([string]::IsNullOrWhiteSpace($Model)) { throw 'Model is required.' }
Add-Type -AssemblyName System.Net.Http
$secureKey = $null
$keyPointer = [IntPtr]::Zero
$plainKey = $null
$client = $null
$handler = $null
$request = $null
$response = $null
function Protect-ProviderText([string]$Value, [string]$Secret) {
    if (!$Value) { return '' }
    $safe = $Value.Replace($Secret, '[REDACTED]')
    $safe = [regex]::Replace($safe, '(?i)bearer\s+[^\s"''<>]+', 'Bearer [REDACTED]')
    $safe = [regex]::Replace($safe, '(?i)\bsk[-_][a-z0-9_-]+', '[REDACTED]')
    $safe = [regex]::Replace($safe, '(?i)(api[-_ ]?key|authorization|token)\s*[:=]\s*[^\s,;<>]+', '$1=[REDACTED]')
    $safe = [regex]::Replace($safe, '[A-Za-z0-9_-]{32,}', '[REDACTED]')
    $safe = [regex]::Replace($safe, '[\x00-\x1f\x7f-\x9f]', ' ')
    if ($safe.Length -gt 500) { $safe = $safe.Substring(0, 500) + ' [truncated]' }
    return $safe
}
try {
    Write-Host 'One real embedding request; no retries, redirects, key files, or response log files.'
    Write-Host 'Paste the EMBEDDING API key at the hidden prompt, not into the command line.'
    $secureKey = Read-Host 'Embedding API key' -AsSecureString
    if (!$secureKey -or $secureKey.Length -eq 0) { throw 'Empty key.' }
    $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    if ($plainKey -match '[^\x21-\x7e]') { throw 'Key contains whitespace or non-ASCII characters.' }
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    $client.MaxResponseContentBufferSize = 1048576
    $uri = $BaseUrl.AbsoluteUri.TrimEnd('/') + '/embeddings'
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $uri)
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $plainKey)
    $payload = @{ model = $Model; input = @('embedding connectivity check'); encoding_format = 'float' } | ConvertTo-Json -Compress
    $request.Content = [System.Net.Http.StringContent]::new($payload, [Text.Encoding]::UTF8, 'application/json')
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $contentType = if ($response.Content.Headers.ContentType) { $response.Content.Headers.ContentType.MediaType } else { '' }
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $parsed = $null
    try { $parsed = $body | ConvertFrom-Json -ErrorAction Stop } catch { }
    $result = [ordered]@{ Time = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz'); HTTP = [int]$response.StatusCode }
    $result.ResponseFormat = if ($parsed) { 'JSON' } elseif ($contentType -eq 'text/html') { 'HTML' } else { 'OTHER' }
    if ($response.IsSuccessStatusCode) {
        $entries = @($parsed.data | Where-Object { $null -ne $_ })
        $result.EmbeddingCount = $entries.Count
        $firstEmbedding = if ($entries.Count -gt 0) { $entries[0].embedding } else { $null }
        $result.VectorDimensions = if ($firstEmbedding -is [Array]) { $firstEmbedding.Count } else { 'No float array returned' }
    } elseif ($parsed) {
        $providerError = if ($parsed.error) { $parsed.error } else { $parsed }
        foreach ($name in @('code', 'type', 'message', 'detail', 'msg', 'reason')) {
            $value = if ($providerError -is [string] -and $name -eq 'message') { $providerError } else { $providerError.$name }
            if ($value -is [string]) { $result[$name] = Protect-ProviderText $value $plainKey }
        }
        if ($result.Count -eq 3) { $result.Note = 'No simple error fields; full body intentionally omitted.' }
    } else { $result.Note = 'Non-JSON response body intentionally omitted.' }
    $result | ConvertTo-Json -Depth 3
} catch {
    # Exception messages may include a request, proxy credentials or a response. Types only.
    $types = @()
    for ($problem = $_.Exception; $problem; $problem = $problem.InnerException) { $types += $problem.GetType().FullName }
    [ordered]@{ Result = 'REQUEST_FAILED'; ExceptionTypes = $types } | ConvertTo-Json -Depth 3
    exit 1
} finally {
    if ($request) { $request.Headers.Authorization = $null; $request.Dispose() }
    if ($response) { $response.Dispose() }
    if ($client) { $client.Dispose() } elseif ($handler) { $handler.Dispose() }
    if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
    if ($secureKey) { $secureKey.Dispose() }
    $plainKey = $null
    $body = $null
    $parsed = $null
}
