param(
  [string]$BaseUrl = "http://127.0.0.1:9527",
  [int]$Count = 1000,
  [int]$StartIndex = 1,
  [string]$UsernamePrefix = "jmeter",
  [string]$Password = "Jmeter@123456",
  [string]$EmailDomain = "jmeter.local",
  [string]$AccountsCsv = ".\scripts\jmeter\accounts.csv",
  [string]$OutputCsv = ".\scripts\jmeter\users.csv",
  [ValidateSet("AutoRegister", "LoginOnly")]
  [string]$Mode = "AutoRegister",
  [int]$DelayMs = 0,
  [switch]$Force
)

$ErrorActionPreference = "Stop"

function Invoke-Json {
  param(
    [string]$Method,
    [string]$Uri,
    [object]$Body = $null
  )
  $args = @{
    Method = $Method
    Uri = $Uri
    ContentType = "application/json"
  }
  if ($null -ne $Body) {
    $args.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }
  Invoke-RestMethod @args
}

function Get-ApiMessage {
  param([object]$Response)
  if ($null -eq $Response) { return "empty response" }
  foreach ($name in @("message", "msg")) {
    if ($Response.PSObject.Properties.Name -contains $name) { return [string]$Response.$name }
  }
  return ($Response | ConvertTo-Json -Depth 5 -Compress)
}

function Assert-Success {
  param([object]$Response, [string]$Action)
  if ($null -eq $Response) { throw "$Action returned empty response" }
  if ($Response.PSObject.Properties.Name -contains "flag" -and -not $Response.flag) {
    throw "$Action failed: $(Get-ApiMessage $Response)"
  }
  if ($Response.PSObject.Properties.Name -contains "success" -and -not $Response.success) {
    throw "$Action failed: $(Get-ApiMessage $Response)"
  }
}

function Get-LoginToken {
  param([string]$Username, [string]$Password)
  $response = Invoke-Json "POST" "$BaseUrl/api/user/login" @{ username = $Username; password = $Password }
  Assert-Success $response "login $Username"
  if ($response.data.token) { return [string]$response.data.token }
  if ($response.data -is [string]) { return [string]$response.data }
  throw "login $Username did not return data.token"
}

function New-GeneratedAccounts {
  $items = @()
  $end = $StartIndex + $Count - 1
  for ($i = $StartIndex; $i -le $end; $i++) {
    $username = "{0}{1:D4}" -f $UsernamePrefix, $i
    $items += [pscustomobject]@{
      username = $username
      password = $Password
      email = "$username@$EmailDomain"
    }
  }
  return $items
}

$root = Resolve-Path "."
$outputFull = Join-Path $root $OutputCsv
$outputDir = Split-Path $outputFull -Parent
if (-not (Test-Path $outputDir)) { New-Item -ItemType Directory -Force $outputDir | Out-Null }
if ((Test-Path $outputFull) -and -not $Force) {
  throw "Output already exists: $outputFull. Pass -Force to overwrite."
}

try {
  Invoke-Json "GET" "$BaseUrl/api/setup" | Out-Null
} catch {
  throw "Backend is not reachable at $BaseUrl. Please start backend first, or pass -BaseUrl. Detail: $($_.Exception.Message)"
}

$accounts = @()
$loadedFromCsv = Test-Path $AccountsCsv
if ($loadedFromCsv) {
  $accounts = @(Import-Csv $AccountsCsv)
  if ($accounts.Count -eq 0) { throw "AccountsCsv is empty: $AccountsCsv" }
  Write-Host "Loaded $($accounts.Count) accounts from $AccountsCsv"
} elseif ($Mode -eq "LoginOnly") {
  throw "LoginOnly mode requires AccountsCsv: $AccountsCsv"
} else {
  $accounts = @(New-GeneratedAccounts)
  Write-Host "Generated $($accounts.Count) accounts in memory: $UsernamePrefix$('{0:D4}' -f $StartIndex) ..."
}

$tokens = New-Object System.Collections.Generic.List[string]
$registered = 0
$existed = 0
$failed = 0
$total = $accounts.Count
$index = 0

foreach ($account in $accounts) {
  $index++
  $username = [string]$account.username
  $accountPassword = [string]$account.password
  $email = [string]$account.email
  if ([string]::IsNullOrWhiteSpace($username) -or [string]::IsNullOrWhiteSpace($accountPassword)) {
    throw "account row $index requires username/password"
  }

  Write-Progress -Activity "Generating JMeter users.csv" -Status "$index / $total : $username" -PercentComplete (($index * 100) / $total)

  if ($Mode -eq "AutoRegister") {
    try {
      $body = @{ username = $username; password = $accountPassword }
      if (-not [string]::IsNullOrWhiteSpace($email)) { $body.email = $email }
      $register = Invoke-Json "POST" "$BaseUrl/api/user/register" $body
      Assert-Success $register "register $username"
      $registered++
    } catch {
      $message = $_.Exception.Message
      if ($message -match "用户名或邮箱已存在|already|exist") {
        $existed++
      } else {
        $failed++
        throw "register $username failed: $message"
      }
    }
  }

  try {
    $token = Get-LoginToken $username $accountPassword
    $tokens.Add($token)
  } catch {
    $failed++
    throw $_
  }

  if ($DelayMs -gt 0) { Start-Sleep -Milliseconds $DelayMs }
}

Write-Progress -Activity "Generating JMeter users.csv" -Completed
if ($tokens.Count -eq 0) { throw "No tokens generated" }
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("token")
foreach ($tokenValue in $tokens) { $lines.Add($tokenValue) }
[System.IO.File]::WriteAllLines($outputFull, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Generated $($tokens.Count) tokens -> $outputFull"
Write-Host "Registered: $registered, existed/ignored: $existed, failed: $failed"
Write-Host "Use this CSV in JMeter as USERS_CSV. Keep it local; it contains active Sa-Token values."