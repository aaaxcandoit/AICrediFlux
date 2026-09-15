param(
  [string]$JMeterHome = 'D:\Project\Tools\apache-jmeter-5.6.3',
  [string]$UsersCsv = '.\scripts\jmeter\users.csv',
  [string]$HostName = '127.0.0.1',
  [int]$Port = 9527,
  [string[]]$CampaignIds,
  [string[]]$ThreadCounts = @('100', '500', '1000'),
  [string[]]$RampSeconds = @('2', '5', '10'),
  [string]$ResultsDir = '.\scripts\jmeter\results',
  [string]$Output = 'docs/eval/reports/jmeter-summary.json',
  [switch]$NoOverwrite,
  [switch]$ParseOnly
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $scriptDir '..')
Set-Location $root

function Convert-IntList {
  param([string[]]$Values, [string]$Name)
  $items = @()
  foreach ($value in $Values) {
    if ([string]::IsNullOrWhiteSpace($value)) { continue }
    $items += ($value -split '[,;\s]+' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  }
  if ($items.Count -eq 0) { return @() }
  $numbers = @()
  foreach ($item in $items) {
    $parsed = 0
    if (-not [int]::TryParse($item, [ref]$parsed)) {
      throw "$Name contains invalid integer: $item"
    }
    $numbers += $parsed
  }
  return $numbers
}

$campaignIdList = Convert-IntList $CampaignIds 'CampaignIds'
$threadCountList = Convert-IntList $ThreadCounts 'ThreadCounts'
$rampSecondList = Convert-IntList $RampSeconds 'RampSeconds'

if (-not $ParseOnly -and $campaignIdList.Count -lt $threadCountList.Count) {
  throw "CampaignIds parsed as $($campaignIdList -join ',') but $($threadCountList.Count) ids are required. Use quotes: -CampaignIds '17,18,19', or pass space-separated values: -CampaignIds 17 18 19."
}

if (-not $ParseOnly) {
  if (-not $campaignIdList -or $campaignIdList.Count -eq 0) {
    throw 'CampaignIds is required unless ParseOnly is enabled. Example: -CampaignIds "4,5,6"'
  }

  $plan = @()
  for ($i = 0; $i -lt $threadCountList.Count; $i++) {
    $plan += "$($threadCountList[$i])->$($campaignIdList[$i])"
  }
  Write-Host "==> JMeter plan: $($plan -join ', ')"

  $runnerArgs = @(
    '-ExecutionPolicy', 'Bypass',
    '-File', '.\scripts\jmeter\run-flash-sale-jmeter.ps1',
    '-JMeterHome', $JMeterHome,
    '-UsersCsv', $UsersCsv,
    '-HostName', $HostName,
    '-Port', $Port,
    '-CampaignIds', ($campaignIdList -join ','),
    '-ThreadCounts', ($threadCountList -join ','),
    '-RampSeconds', ($rampSecondList -join ','),
    '-ResultsDir', $ResultsDir
  )
  if ($NoOverwrite) { $runnerArgs += '-NoOverwrite' }

  Write-Host "==> JMeter flash-sale eval"
  & powershell @runnerArgs
  if ($LASTEXITCODE -ne 0) { throw "JMeter eval failed with exit code $LASTEXITCODE" }
}

Write-Host "==> Parse JMeter reports"
& node scripts/eval/parse-jmeter-report.mjs --results-dir $ResultsDir --output $Output
if ($LASTEXITCODE -ne 0) { throw "JMeter report parse failed with exit code $LASTEXITCODE" }
Write-Host "JMeter eval result: $Output"




