[CmdletBinding()]
param(
    [string]$ApiBaseUrl,
    [string]$BackendBaseUrl,
    [string]$FrontendBaseUrl
)

$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $backendRoot
$openApiPath = Join-Path $projectRoot 'openapi\shipflow-api.yaml'
$reportsPath = Join-Path $backendRoot 'reports'

if ([string]::IsNullOrWhiteSpace($FrontendBaseUrl)) { $FrontendBaseUrl = 'http://localhost:5173' }
if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) { $ApiBaseUrl = $FrontendBaseUrl }
if ([string]::IsNullOrWhiteSpace($BackendBaseUrl)) { $BackendBaseUrl = 'http://localhost:8080' }

function New-Result(
    [string]$OperationId,
    [string]$Method,
    [string]$Path,
    [string]$AuthRequirement,
    [string]$DocumentedStatuses,
    [string]$Result,
    [Nullable[int]]$StatusCode,
    [string]$Reason
) {
    [pscustomobject]@{
        operationId = $OperationId
        method = $Method.ToUpperInvariant()
        path = $Path
        authRequirement = $AuthRequirement
        documentedStatuses = $DocumentedStatuses
        result = $Result
        statusCode = $StatusCode
        reason = $Reason
    }
}

function Get-StatusCodeFromError($ErrorRecord) {
    if ($null -eq $ErrorRecord.Exception.Response) { return $null }
    try { return [int]$ErrorRecord.Exception.Response.StatusCode } catch { return $null }
}

function Invoke-ReadRequest([string]$Uri, [Microsoft.PowerShell.Commands.WebRequestSession]$Session, [hashtable]$Headers) {
    try {
        $request = @{ UseBasicParsing = $true; TimeoutSec = 15; Uri = $Uri; Method = 'GET' }
        if ($null -ne $Session) { $request.WebSession = $Session }
        if ($null -ne $Headers) { $request.Headers = $Headers }
        $response = Invoke-WebRequest @request
        return [pscustomobject]@{ statusCode = [int]$response.StatusCode; content = [string]$response.Content; error = $null }
    } catch {
        return [pscustomobject]@{ statusCode = Get-StatusCodeFromError $_; content = $null; error = $_.Exception.Message }
    }
}

function Get-OperationsFromOpenApi([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "OpenAPI not found: $Path" }
    $operations = [System.Collections.Generic.List[object]]::new()
    $currentPath = $null
    $current = $null
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^  (/[^:]+):\s*$') {
            if ($null -ne $current) {
                $operations.Add([pscustomobject]@{
                    operationId = $current.operationId; method = $current.method; path = $currentPath
                    authRequirement = $current.authRequirement
                    documentedStatuses = @($current.documentedStatuses | Sort-Object -Unique)
                })
            }
            $currentPath = $Matches[1]
            $current = $null
            continue
        }
        if ($line -match '^    (get|post|put|patch|delete):\s*$') {
            if ($null -ne $current) {
                $operations.Add([pscustomobject]@{
                    operationId = $current.operationId; method = $current.method; path = $currentPath
                    authRequirement = $current.authRequirement
                    documentedStatuses = @($current.documentedStatuses | Sort-Object -Unique)
                })
            }
            $current = [pscustomobject]@{
                operationId = $null
                method = $Matches[1]
                authRequirement = 'BEARER_OR_DECLARED_SECURITY'
                documentedStatuses = [System.Collections.Generic.List[string]]::new()
            }
            continue
        }
        if ($null -eq $current) { continue }
        if ($line -match 'operationId:\s*([A-Za-z0-9_]+)') { $current.operationId = $Matches[1] }
        if ($line -match '^\s{6}security:\s*\[\s*\]\s*$') { $current.authRequirement = 'PUBLIC' }
        elseif ($line -match '^\s{6}security:\s*$') { $current.authRequirement = 'DECLARED_SECURITY' }
        foreach ($status in [regex]::Matches($line, "'(200|201|202|204|400|401|403|404|409|422|500)'\s*:")) {
            $current.documentedStatuses.Add($status.Groups[1].Value)
        }
    }
    if ($null -ne $current) {
        $operations.Add([pscustomobject]@{
            operationId = $current.operationId; method = $current.method; path = $currentPath
            authRequirement = $current.authRequirement
            documentedStatuses = @($current.documentedStatuses | Sort-Object -Unique)
        })
    }
    if ($operations.Count -eq 0 -or @($operations | Where-Object { [string]::IsNullOrWhiteSpace($_.operationId) }).Count -gt 0) {
        throw 'OpenAPI operation parsing failed: an operationId is missing.'
    }
    return $operations
}

function Resolve-OperationUri([string]$BaseUrl, [string]$Path) {
    $resolved = [regex]::Replace($Path, '\{[^}]+\}', '0')
    return "$($BaseUrl.TrimEnd('/'))/api/v1$resolved"
}

function Format-Statuses($Statuses) {
    if ($null -eq $Statuses -or @($Statuses).Count -eq 0) { return 'UNDECLARED' }
    return (@($Statuses) -join ',')
}

$operations = Get-OperationsFromOpenApi $openApiPath
$operationIds = @($operations | ForEach-Object operationId)
if (($operationIds | Sort-Object -Unique).Count -ne $operationIds.Count) { throw 'OpenAPI operationIds are not unique.' }
$requiredP305OperationIds = @(
    'getOperationsWorkbench', 'getWarehouseOverview', 'listStores', 'listAvailableLogisticsChannels',
    'listQuotes', 'listShipmentOrders', 'listTrackingEvents', 'listExceptionCases',
    'listBillImportBatches', 'listReconciliations'
)
$missingP305OperationIds = @($requiredP305OperationIds | Where-Object { $_ -notin $operationIds })
if ($missingP305OperationIds.Count -gt 0) { throw "Missing P3-05 operationId(s): $($missingP305OperationIds -join ', ')" }

$results = [System.Collections.Generic.List[object]]::new()
$anonymousResults = [System.Collections.Generic.List[object]]::new()

foreach ($operation in $operations) {
    $documented = Format-Statuses $operation.documentedStatuses
    if ($operation.method -ne 'get') {
        $item = New-Result $operation.operationId $operation.method $operation.path $operation.authRequirement $documented 'SKIPPED_READ_ONLY' $null 'Write operation not sent: P3-05 is read-only and must not create or modify business data.'
        $results.Add($item)
        $anonymousResults.Add($item)
        continue
    }

    $expected = if ($operation.operationId -eq 'getCsrfToken') { '204' } elseif ($operation.authRequirement -eq 'PUBLIC') { '200,204' } else { '401' }
    $response = Invoke-ReadRequest (Resolve-OperationUri $ApiBaseUrl $operation.path) $null $null
    $actual = if ($null -eq $response.statusCode) { 'NO_RESPONSE' } else { [string]$response.statusCode }
    $pass = ($expected -split ',') -contains $actual
    $reason = if ($pass -and $actual -eq '401') {
        'Anonymous request was rejected with the exact authentication-required status; route existence is not inferred from this response.'
    } elseif ($pass) {
        'Anonymous read-only request returned the exact expected public status.'
    } else {
        "Expected anonymous status $expected, received $actual. No generic 200/401/403/404 acceptance was applied."
    }
    $item = New-Result $operation.operationId $operation.method $operation.path $operation.authRequirement $documented $(if ($pass) { 'PASS' } else { 'FAIL' }) $response.statusCode $reason
    $results.Add($item)
    $anonymousResults.Add($item)
}

$password = [Environment]::GetEnvironmentVariable('SHIPFLOW_TEST_PASSWORD', 'Process')
$credentialAvailable = -not [string]::IsNullOrWhiteSpace($password)
$merchantUsername = [Environment]::GetEnvironmentVariable('SHIPFLOW_TEST_USERNAME', 'Process')
$merchantTenantCode = [Environment]::GetEnvironmentVariable('SHIPFLOW_TEST_TENANT_CODE', 'Process')
if ([string]::IsNullOrWhiteSpace($merchantUsername)) { $merchantUsername = 'merchant_operator_001' }
if ([string]::IsNullOrWhiteSpace($merchantTenantCode)) { $merchantTenantCode = 'TENANT_DEMO_001' }

$authSummary = [ordered]@{ result = 'BLOCKED'; loginStatus = $null; reason = 'SHIPFLOW_TEST_PASSWORD is not present in this process.' }
$authenticatedResults = [System.Collections.Generic.List[object]]::new()
$accessToken = $null
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

if ($credentialAvailable) {
    $csrf = Invoke-ReadRequest (Resolve-OperationUri $ApiBaseUrl '/auth/csrf') $session $null
    if ($csrf.statusCode -ne 204) {
        $authSummary.reason = "CSRF request expected 204, received $($csrf.statusCode)."
    } else {
        $xsrfCookies = @($session.Cookies.GetCookies([Uri]$ApiBaseUrl) | Where-Object Name -eq 'XSRF-TOKEN')
        if ($xsrfCookies.Count -eq 0) {
            $authSummary.reason = 'CSRF returned 204 but no XSRF-TOKEN cookie was available in the same session.'
        } else {
            $xsrfValue = [Uri]::UnescapeDataString($xsrfCookies[-1].Value)
            $loginPayload = @{ username = $merchantUsername; password = $password; tenantCode = $merchantTenantCode } | ConvertTo-Json -Compress
            $loginRequest = @{
                UseBasicParsing = $true; TimeoutSec = 15; Uri = (Resolve-OperationUri $ApiBaseUrl '/auth/login'); Method = 'POST'
                WebSession = $session; ContentType = 'application/json; charset=utf-8'
                Headers = @{ 'X-XSRF-TOKEN' = $xsrfValue }
                Body = [Text.Encoding]::UTF8.GetBytes($loginPayload)
            }
            try {
                $loginResponse = Invoke-WebRequest @loginRequest
                $authSummary.loginStatus = [int]$loginResponse.StatusCode
                if ($loginResponse.StatusCode -eq 200) {
                    $loginJson = $loginResponse.Content | ConvertFrom-Json
                    $accessToken = $loginJson.data.accessToken
                    if ([string]::IsNullOrWhiteSpace($accessToken)) { $authSummary.reason = 'Login returned 200 without an access token.' }
                    else { $authSummary.result = 'PASS'; $authSummary.reason = 'Merchant login returned 200; token retained in memory only.' }
                } else { $authSummary.reason = "Merchant login returned $($loginResponse.StatusCode); response body was not recorded." }
            } catch {
                $authSummary.loginStatus = Get-StatusCodeFromError $_
                $authSummary.reason = if ($null -eq $authSummary.loginStatus) { 'Merchant login did not return an HTTP response.' } else { "Merchant login returned $($authSummary.loginStatus); response body was not recorded." }
            }
        }
    }
}

if ($authSummary.result -eq 'PASS') {
    $authHeaders = @{ Authorization = "Bearer $accessToken" }
    $pageChecks = @(
        @{ page = 'console-operations'; operationId = 'getOperationsWorkbench'; path = '/operations/workbench' },
        @{ page = 'console-warehouse'; operationId = 'getWarehouseOverview'; path = '/warehouse/overview' },
        @{ page = 'stores'; operationId = 'listStores'; path = '/stores' },
        @{ page = 'logistics'; operationId = 'listAvailableLogisticsChannels'; path = '/logistics/channels' },
        @{ page = 'quotes'; operationId = 'listQuotes'; path = '/quotes' },
        @{ page = 'orders'; operationId = 'listShipmentOrders'; path = '/orders' },
        @{ page = 'exceptions'; operationId = 'listExceptionCases'; path = '/exceptions' },
        @{ page = 'billing-batches'; operationId = 'listBillImportBatches'; path = '/billing/import-batches' },
        @{ page = 'billing-reconciliations'; operationId = 'listReconciliations'; path = '/reconciliations' }
    )
    foreach ($check in $pageChecks) {
        $operation = $operations | Where-Object operationId -eq $check.operationId | Select-Object -First 1
        $response = Invoke-ReadRequest (Resolve-OperationUri $ApiBaseUrl $check.path) $session $authHeaders
        $status = $response.statusCode
        $result = if ($status -eq 200) { 'PASS' } elseif ($status -eq 403) { 'FAIL' } elseif ($status -eq 401) { 'FAIL' } elseif ($status -eq 404) { 'FAIL' } else { 'FAIL' }
        $reason = switch ($status) {
            200 { 'Authenticated read returned 200; empty data, if present, must be verified in the browser and is not counted as failure.'; break }
            401 { 'Authenticated read returned 401; login/session evidence is invalid or expired.'; break }
            403 { 'Authenticated read returned 403; merchant account lacks the page permission or scope.'; break }
            404 { 'Authenticated read returned 404; route or resource contract is not available. Not treated as pass.'; break }
            409 { 'Unexpected 409 for a read operation.'; break }
            422 { 'Unexpected 422 for a read operation.'; break }
            default { "Authenticated read returned $status; not treated as pass."; break }
        }
        $authenticatedResults.Add((New-Result $check.operationId 'GET' $check.path $operation.authRequirement (Format-Statuses $operation.documentedStatuses) $result $status "$($check.page)：$reason"))
    }
}

$allResults = @($results) + @($authenticatedResults)
$summary = [ordered]@{
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    frontendBaseUrl = $FrontendBaseUrl
    apiBaseUrl = $ApiBaseUrl
    backendBaseUrl = $BackendBaseUrl
    operations = $operations.Count
    anonymousPass = @($anonymousResults | Where-Object result -eq 'PASS').Count
    anonymousFail = @($anonymousResults | Where-Object result -eq 'FAIL').Count
    readOnlySkipped = @($anonymousResults | Where-Object result -eq 'SKIPPED_READ_ONLY').Count
    authentication = $authSummary
    authenticatedPageChecks = $authenticatedResults.Count
    authenticatedPass = @($authenticatedResults | Where-Object result -eq 'PASS').Count
    authenticatedFail = @($authenticatedResults | Where-Object result -eq 'FAIL').Count
    results = $allResults
}

New-Item -ItemType Directory -Force -Path $reportsPath | Out-Null
$jsonPath = Join-Path $reportsPath 'http-acceptance-summary.json'
$mdPath = Join-Path $reportsPath 'http-acceptance-summary.md'
$summary | ConvertTo-Json -Depth 8 | Out-File -LiteralPath $jsonPath -Encoding utf8
$lines = @(
    '# ShipFlow read-only HTTP acceptance summary',
    '',
    "- operation baseline: $($summary.operations)",
    "- anonymous GET PASS: $($summary.anonymousPass)",
    "- anonymous GET FAIL: $($summary.anonymousFail)",
    "- write operations skipped: $($summary.readOnlySkipped)",
    "- merchant login: $($authSummary.result) HTTP=$($authSummary.loginStatus)",
    "- authenticated page checks PASS: $($summary.authenticatedPass)",
    "- authenticated page checks FAIL: $($summary.authenticatedFail)",
    '',
    '| operationId | method | path | auth | documented | result | HTTP | reason |',
    '|---|---|---|---|---|---|---:|---|'
)
foreach ($item in $allResults) {
    $lines += "| $($item.operationId) | $($item.method) | $($item.path) | $($item.authRequirement) | $($item.documentedStatuses) | $($item.result) | $($item.statusCode) | $($item.reason) |"
}
$lines | Out-File -LiteralPath $mdPath -Encoding utf8

Write-Output ("operations={0} anonymousPass={1} anonymousFail={2} writeSkipped={3} login={4} authenticatedPass={5} authenticatedFail={6}" -f $summary.operations, $summary.anonymousPass, $summary.anonymousFail, $summary.readOnlySkipped, $authSummary.result, $summary.authenticatedPass, $summary.authenticatedFail)
if ($summary.anonymousFail -gt 0) { $anonymousResults | Where-Object result -eq 'FAIL' | ForEach-Object { Write-Output ("FAIL {0} {1} {2} HTTP={3}" -f $_.method, $_.operationId, $_.path, $_.statusCode) } }
if ($summary.authenticatedFail -gt 0) { $authenticatedResults | Where-Object result -eq 'FAIL' | ForEach-Object { Write-Output ("AUTH_FAIL {0} HTTP={1} {2}" -f $_.operationId, $_.statusCode, $_.reason) } }
