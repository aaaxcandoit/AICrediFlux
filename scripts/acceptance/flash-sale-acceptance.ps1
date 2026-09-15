param(
  [string]$BaseUrl = "http://127.0.0.1:9527",
  [string]$ServerDir = ".\aicrediflux-token-server",
  [string]$WebDir = ".\aicrediflux-token-web",
  [string]$JMeterHome = "D:\Project\Tools\apache-jmeter-5.6.3",
  [string]$UsersCsv = ".\scripts\jmeter\users.csv",
  [int[]]$CampaignIds = @(),
  [int[]]$ThreadCounts = @(100, 500, 1000),
  [int[]]$RampSeconds = @(2, 5, 10),
  [string]$AdminUser = $env:AICREDIFLUX_ACCEPTANCE_ADMIN_USER,
  [string]$AdminPassword = $env:AICREDIFLUX_ACCEPTANCE_ADMIN_PASSWORD,
  [string]$UserName = $env:AICREDIFLUX_ACCEPTANCE_USER,
  [string]$UserPassword = $env:AICREDIFLUX_ACCEPTANCE_PASSWORD,
  [string]$UserToken = $env:AICREDIFLUX_ACCEPTANCE_USER_TOKEN,
  [switch]$RunJMeter,
  [switch]$SkipBackendTests,
  [switch]$SkipFrontendBuild,
  [string]$ReportPath = ".\scripts\jmeter\results\acceptance-summary.json"
)

$ErrorActionPreference = "Stop"

function New-StepResult {
  param([string]$Name, [string]$Status, [object]$Detail)
  [ordered]@{
    name = $Name
    status = $Status
    detail = $Detail
    time = (Get-Date).ToString("s")
  }
}

function Invoke-CheckedCommand {
  param([string]$Name, [scriptblock]$Command)
  Write-Host "==> $Name"
  try {
    $output = & $Command 2>&1
    $text = ($output | Out-String).Trim()
    $script:steps += New-StepResult $Name "PASSED" $text
    return $text
  } catch {
    $script:steps += New-StepResult $Name "FAILED" $_.Exception.Message
    throw
  }
}

function Invoke-Json {
  param(
    [string]$Method,
    [string]$Uri,
    [hashtable]$Headers = @{},
    [object]$Body = $null
  )
  $args = @{
    Method = $Method
    Uri = $Uri
    ContentType = "application/json"
  }
  if ($Headers.Count -gt 0) { $args.Headers = $Headers }
  if ($null -ne $Body) { $args.Body = ($Body | ConvertTo-Json -Depth 12 -Compress) }
  Invoke-RestMethod @args
}

function Assert-ApiSuccess {
  param([object]$Response, [string]$Action)
  if ($null -eq $Response) { throw "$Action returned empty response" }
  if ($Response.PSObject.Properties.Name -contains "flag") {
    if (-not $Response.flag) { throw "$Action failed: $($Response.message)" }
  }
  if ($Response.PSObject.Properties.Name -contains "success") {
    if (-not $Response.success) { throw "$Action failed: $($Response.message)" }
  }
}

function Login-Token {
  param([string]$Username, [string]$Password)
  if (-not $Username -or -not $Password) { throw "username/password not provided" }
  $response = Invoke-Json "POST" "$BaseUrl/api/user/login" @{} @{ username = $Username; password = $Password }
  Assert-ApiSuccess $response "login $Username"
  if ($response.data.token) { return $response.data.token }
  if ($response.data) { return $response.data }
  throw "login $Username did not return token"
}

function Read-JtlSummary {
  param([string]$Path)
  if (-not (Test-Path $Path)) {
    return @{ samples = 0; errors = 0; p95 = $null; p99 = $null; note = "result file not found" }
  }
  $rows = Import-Csv $Path
  if (-not $rows -or $rows.Count -eq 0) {
    return @{ samples = 0; errors = 0; p95 = $null; p99 = $null; note = "no samples" }
  }
  $elapsed = @($rows | ForEach-Object { [int]$_.elapsed } | Sort-Object)
  $errorCount = @($rows | Where-Object { $_.success -ne "true" }).Count
  $p95Index = [Math]::Min($elapsed.Count - 1, [Math]::Max(0, [Math]::Ceiling($elapsed.Count * 0.95) - 1))
  $p99Index = [Math]::Min($elapsed.Count - 1, [Math]::Max(0, [Math]::Ceiling($elapsed.Count * 0.99) - 1))
  @{
    samples = $rows.Count
    errors = $errorCount
    errorRate = [Math]::Round(($errorCount * 100.0) / $rows.Count, 2)
    p95 = $elapsed[$p95Index]
    p99 = $elapsed[$p99Index]
  }
}

$script:steps = @()
$startedAt = Get-Date
$root = Resolve-Path "."
$reportFullPath = Join-Path $root $ReportPath
$resultDir = Split-Path $reportFullPath -Parent
if (-not (Test-Path $resultDir)) { New-Item -ItemType Directory -Force $resultDir | Out-Null }

Invoke-CheckedCommand "docker compose mq services" {
  docker compose -f docker-compose.dev.yml --profile mq ps
}

if (-not $SkipBackendTests) {
  Invoke-CheckedCommand "backend full regression" {
    Push-Location $ServerDir
    try { mvn -o test } finally { Pop-Location }
  }
}

if (-not $SkipFrontendBuild) {
  Invoke-CheckedCommand "frontend type-check" {
    Push-Location $WebDir
    try { pnpm type-check } finally { Pop-Location }
  }
  Invoke-CheckedCommand "frontend production build" {
    Push-Location $WebDir
    try { pnpm build:prod } finally { Pop-Location }
  }
}

try {
  Invoke-Json "GET" "$BaseUrl/api/setup" | Out-Null
  $script:steps += New-StepResult "backend api reachable" "PASSED" $BaseUrl
} catch {
  $script:steps += New-StepResult "backend api reachable" "SKIPPED" "Backend is not reachable at $BaseUrl; API smoke and JMeter were not executed."
}

$adminToken = $null
try {
  if ($AdminUser -and $AdminPassword) {
    $adminToken = Login-Token $AdminUser $AdminPassword
    $script:steps += New-StepResult "admin login" "PASSED" $AdminUser
  } else {
    $script:steps += New-StepResult "admin login" "SKIPPED" "Set AICREDIFLUX_ACCEPTANCE_ADMIN_USER/PASSWORD or pass -AdminUser/-AdminPassword."
  }
} catch {
  $script:steps += New-StepResult "admin login" "FAILED" $_.Exception.Message
}

$effectiveUserToken = $UserToken
if (-not $effectiveUserToken -and $UserName -and $UserPassword) {
  $effectiveUserToken = Login-Token $UserName $UserPassword
  $script:steps += New-StepResult "user login" "PASSED" $UserName
}
if (-not $effectiveUserToken -and $adminToken) {
  $effectiveUserToken = $adminToken
  $script:steps += New-StepResult "user token fallback" "PASSED" "Using admin token for local acceptance smoke only."
}

if ($effectiveUserToken) {
  $headers = @{ aicrediflux = $effectiveUserToken }
  try {
    $packages = Invoke-Json "GET" "$BaseUrl/api/token-packages" $headers
    Assert-ApiSuccess $packages "list token packages"
    $firstPackage = @($packages.data)[0]
    if ($null -eq $firstPackage -and $adminToken) {
      $adminHeaders = @{ aicrediflux = $adminToken }
      $createdPackage = Invoke-Json "POST" "$BaseUrl/api/admin/token-packages" $adminHeaders @{
        name = "Acceptance Demo 20万 AI Credit"
        description = "Created by scripts/acceptance/flash-sale-acceptance.ps1"
        creditAmount = 200000
        originalPrice = 100
        salePrice = 100
        status = 1
        sort = 999
      }
      Assert-ApiSuccess $createdPackage "create acceptance token package"
      $firstPackage = $createdPackage.data
      $script:steps += New-StepResult "create acceptance token package" "PASSED" @{
        packageId = $firstPackage.id
        creditAmount = $firstPackage.creditAmount
      }
    }
    if ($null -eq $firstPackage) { throw "no package available" }
    $order = Invoke-Json "POST" "$BaseUrl/api/token-packages/$($firstPackage.id)/orders" $headers
    Assert-ApiSuccess $order "create ordinary order"
    $orderNo = $order.data.orderNo
    $pay = Invoke-Json "POST" "$BaseUrl/api/flash-sale/orders/$orderNo/mock-pay" $headers
    Assert-ApiSuccess $pay "mock pay ordinary order"
    Start-Sleep -Seconds 2
    $wallet = Invoke-Json "GET" "$BaseUrl/api/flash-sale/wallet" $headers
    Assert-ApiSuccess $wallet "wallet"
    $script:steps += New-StepResult "ordinary purchase mock-pay wallet smoke" "PASSED" @{
      packageId = $firstPackage.id
      orderNo = $orderNo
      wallet = $wallet.data
    }
  } catch {
    $script:steps += New-StepResult "ordinary purchase mock-pay wallet smoke" "FAILED" $_.Exception.Message
    throw
  }
} else {
  $script:steps += New-StepResult "ordinary purchase mock-pay wallet smoke" "SKIPPED" "Pass -UserToken or -UserName/-UserPassword to run user API smoke."
}

$jmeterResults = @()
if ($RunJMeter) {
  $jmeterBat = Join-Path $JMeterHome "bin\jmeter.bat"
  if (-not (Test-Path $jmeterBat)) { throw "JMeter not found: $jmeterBat" }
  if (-not (Test-Path $UsersCsv)) { throw "JMeter users csv not found: $UsersCsv" }
  if ($CampaignIds.Count -lt $ThreadCounts.Count) { throw "CampaignIds must contain at least $($ThreadCounts.Count) ids" }

  for ($i = 0; $i -lt $ThreadCounts.Count; $i++) {
    $threads = $ThreadCounts[$i]
    $ramp = $RampSeconds[[Math]::Min($i, $RampSeconds.Count - 1)]
    $campaignId = $CampaignIds[$i]
    $resultFile = ".\scripts\jmeter\results\result-$threads.jtl"
    Invoke-CheckedCommand "jmeter flash-sale $threads users" {
      & $jmeterBat -n -t ".\scripts\jmeter\flash-sale.jmx" "-JUSERS_CSV=$((Resolve-Path $UsersCsv).Path)" "-JHOST=127.0.0.1" "-JPORT=9527" "-JCAMPAIGN_ID=$campaignId" "-JTHREADS=$threads" "-JRAMP_SECONDS=$ramp" -l $resultFile
    }
    $jmeterResults += @{
      threads = $threads
      campaignId = $campaignId
      result = Read-JtlSummary $resultFile
    }
  }
} else {
  $script:steps += New-StepResult "jmeter 100/500/1000" "SKIPPED" "Pass -RunJMeter, -CampaignIds, and prepare scripts/jmeter/users.csv to run high-concurrency acceptance."
}

$summary = [ordered]@{
  project = "AICrediFlux phase 1/2 closeout"
  startedAt = $startedAt.ToString("s")
  finishedAt = (Get-Date).ToString("s")
  baseUrl = $BaseUrl
  jmeterHome = $JMeterHome
  usersCsvExists = (Test-Path $UsersCsv)
  steps = $script:steps
  jmeter = $jmeterResults
}

$summary | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8NoBOM $reportFullPath
Write-Host "Acceptance summary written to $reportFullPath"
$summary | ConvertTo-Json -Depth 12


