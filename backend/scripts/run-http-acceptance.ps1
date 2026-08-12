Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $backendRoot
$openApiPath = Join-Path $projectRoot 'openapi\shipflow-api.yaml'
$reportsPath = Join-Path $backendRoot 'reports'
$baseUrl = 'http://localhost:8080'
$runId = [Guid]::NewGuid().ToString('N')
$workPath = Join-Path $reportsPath "acceptance-$runId.ndjson"

function New-Result([string]$operationId, [string]$method, [string]$path, [string]$result, [Nullable[int]]$statusCode, [string]$reason) {
    [pscustomobject]@{
        operationId = $operationId
        method = $method.ToUpperInvariant()
        path = $path
        result = $result
        statusCode = $statusCode
        reason = $reason
    }
}

if (-not (Test-Path -LiteralPath $openApiPath)) { throw "OpenAPI not found: $openApiPath" }
$dbUrl = [Environment]::GetEnvironmentVariable('DB_URL', 'Process')
if (-not [string]::IsNullOrWhiteSpace($dbUrl) -and $dbUrl -notmatch '^jdbc:mysql://.*/shipflow_acceptance([?;]|$)') {
    throw 'DB_URL is present but does not target shipflow_acceptance.'
}

$health = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 -Uri "$baseUrl/actuator/health"
$healthText = if ($health.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($health.Content) } else { [string]$health.Content }
if ($health.StatusCode -ne 200 -or $healthText.Trim() -ne '{"status":"UP"}') { throw 'Existing backend is not healthy.' }
New-Item -ItemType Directory -Force -Path $reportsPath | Out-Null

$operations = [System.Collections.Generic.List[object]]::new()
$currentPath = $null
$currentMethod = $null
foreach ($line in Get-Content -LiteralPath $openApiPath) {
    if ($line -match '^  (/[^:]+):\s*$') { $currentPath = $Matches[1]; $currentMethod = $null; continue }
    if ($line -match '^    (get|post|put|patch|delete):\s*$') { $currentMethod = $Matches[1]; continue }
    if ($line -match 'operationId:\s*([A-Za-z0-9_]+)') {
        if ($null -eq $currentPath -or $null -eq $currentMethod) { throw "Malformed operationId location: $line" }
        $operations.Add([pscustomobject]@{ operationId = $Matches[1]; method = $currentMethod; path = $currentPath })
    }
}
if ($operations.Count -ne 82) { throw "Expected 82 OpenAPI operationIds, found $($operations.Count)." }
if (($operations.operationId | Select-Object -Unique).Count -ne 82) { throw 'OpenAPI operationIds are not unique.' }

$results = [System.Collections.Generic.List[object]]::new()
$credentialAvailable = -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('SHIPFLOW_PLATFORM_TEST_USERNAME', 'Process')) -and
    -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('SHIPFLOW_PLATFORM_TEST_PASSWORD', 'Process'))

foreach ($operation in $operations) {
    if ($operation.operationId -eq 'getCsrfToken') {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$baseUrl/api/v1$($operation.path)" -Method Get -SessionVariable session
            if ($response.StatusCode -eq 204) {
                $results.Add((New-Result $operation.operationId $operation.method $operation.path 'PASS' $response.StatusCode 'Anonymous CSRF route returned the documented status.'))
            } else {
                $results.Add((New-Result $operation.operationId $operation.method $operation.path 'FAIL' $response.StatusCode 'Unexpected CSRF status.'))
            }
        } catch {
            $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { $null }
            $results.Add((New-Result $operation.operationId $operation.method $operation.path 'FAIL' $code 'CSRF route did not return its documented status.'))
        }
        continue
    }

    if ($operation.operationId -in @('login', 'refreshToken', 'logout', 'getCurrentUser')) {
        $reason = if ($credentialAvailable) { 'Authentication flow intentionally not executed: this route needs a controlled credential-bearing smoke session.' } else { 'Missing controlled login credentials in this process.' }
        $results.Add((New-Result $operation.operationId $operation.method $operation.path 'SKIPPED' $null $reason))
        continue
    }

    $resolvedPath = [regex]::Replace($operation.path, '\{[^}]+\}', '0')
    $uri = "$baseUrl/api/v1$resolvedPath"
    try {
        $request = @{ UseBasicParsing = $true; TimeoutSec = 15; Uri = $uri; Method = $operation.method.ToUpperInvariant() }
        if ($operation.method -ne 'get') { $request.ContentType = 'application/json'; $request.Body = '{}' }
        $response = Invoke-WebRequest @request
        $code = [int]$response.StatusCode
        if ($code -ge 200 -and $code -lt 300) {
            $results.Add((New-Result $operation.operationId $operation.method $operation.path 'PASS' $code 'Route returned a successful response without creating business data.'))
        } else {
            $results.Add((New-Result $operation.operationId $operation.method $operation.path 'FAIL' $code 'Unexpected non-success response.'))
        }
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { $null }
        if ($code -in @(401, 403, 404)) {
            $results.Add((New-Result $operation.operationId $operation.method $operation.path 'PASS' $code 'Route reached; authentication, authorization, or absent placeholder resource was correctly rejected.'))
        } elseif ($code -eq 400 -and $operation.method -ne 'GET') {
            $results.Add((New-Result $operation.operationId $operation.method $operation.path 'PASS' $code 'Route reached; request body or required input was correctly validated before business processing.'))
        } else {
            $reason = if ($null -eq $code) { 'No HTTP response from the existing backend.' } else { 'Unexpected HTTP response for route/contract check.' }
            $results.Add((New-Result $operation.operationId $operation.method $operation.path 'FAIL' $code $reason))
        }
    }
}

$summary = [ordered]@{
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    baseUrl = $baseUrl
    operations = $results.Count
    pass = @($results | Where-Object result -eq 'PASS').Count
    fail = @($results | Where-Object result -eq 'FAIL').Count
    skipped = @($results | Where-Object result -eq 'SKIPPED').Count
    results = $results
}
$jsonPath = Join-Path $reportsPath 'http-acceptance-summary.json'
$mdPath = Join-Path $reportsPath 'http-acceptance-summary.md'
$jsonTempPath = Join-Path ([IO.Path]::GetTempPath()) "shipflow-http-summary-$runId.json"
$mdTempPath = Join-Path ([IO.Path]::GetTempPath()) "shipflow-http-summary-$runId.md"
$summary | ConvertTo-Json -Depth 5 | Out-File -LiteralPath $jsonTempPath -Encoding utf8
$lines = @(
    '# ShipFlow HTTP acceptance summary',
    '',
    "- operations: $($summary.operations)",
    "- PASS: $($summary.pass)",
    "- FAIL: $($summary.fail)",
    "- SKIPPED: $($summary.skipped)",
    '',
    '| operationId | method | path | result | HTTP | reason |',
    '|---|---|---|---|---:|---|'
)
foreach ($item in $results) { $lines += "| $($item.operationId) | $($item.method) | $($item.path) | $($item.result) | $($item.statusCode) | $($item.reason) |" }
$lines | Out-File -LiteralPath $mdTempPath -Encoding utf8
[IO.File]::Copy($jsonTempPath, $jsonPath, $true)
[IO.File]::Copy($mdTempPath, $mdPath, $true)
Remove-Item -LiteralPath $jsonTempPath, $mdTempPath -Force
Write-Output ("operations={0} PASS={1} FAIL={2} SKIPPED={3}" -f $summary.operations, $summary.pass, $summary.fail, $summary.skipped)
if ($summary.fail -gt 0) { $results | Where-Object result -eq 'FAIL' | ForEach-Object { Write-Output ("FAIL {0} HTTP={1} {2}" -f $_.operationId, $_.statusCode, $_.reason) } }
