param(
  [string]$JMeterHome = 'D:\Project\Tools\apache-jmeter-5.6.3',
  [string]$UsersCsv = '.\scripts\jmeter\users.csv',
  [string]$HostName = '127.0.0.1',
  [int]$Port = 9527,
  [string[]]$CampaignIds,
  [string[]]$ThreadCounts = @('100', '500', '1000'),
  [string[]]$RampSeconds = @('2', '5', '10'),
  [string]$ResultsDir = '.\scripts\jmeter\results',
  [switch]$NoOverwrite
)

$ErrorActionPreference = 'Stop'

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

function Resolve-UnderRoot {
  param([string]$Path, [string]$Root, [string]$Name)
  $rootFull = [System.IO.Path]::GetFullPath((Join-Path (Resolve-Path '.') $Root))
  $targetFull = [System.IO.Path]::GetFullPath((Join-Path (Resolve-Path '.') $Path))
  if (-not $targetFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "$Name path is outside allowed root. target=$targetFull root=$rootFull"
  }
  return $targetFull
}

$campaignIdList = Convert-IntList $CampaignIds 'CampaignIds'
$threadCountList = Convert-IntList $ThreadCounts 'ThreadCounts'
$rampSecondList = Convert-IntList $RampSeconds 'RampSeconds'

if (-not $campaignIdList -or $campaignIdList.Count -eq 0) {
  throw 'CampaignIds is required. Example: -CampaignIds "4,5,6"'
}
if ($campaignIdList.Count -lt $threadCountList.Count) {
  throw "CampaignIds parsed as $($campaignIdList -join ',') but $($threadCountList.Count) ids are required. Use quotes: -CampaignIds '17,18,19', or pass space-separated values: -CampaignIds 17 18 19."
}

$plan = @()
for ($i = 0; $i -lt $threadCountList.Count; $i++) {
  $plan += "$($threadCountList[$i])->$($campaignIdList[$i])"
}
Write-Host "==> JMeter plan: $($plan -join ', ')"

$jmeterBat = Join-Path $JMeterHome 'bin\jmeter.bat'
if (-not (Test-Path $jmeterBat)) { throw "JMeter not found: $jmeterBat" }
if (-not (Test-Path $UsersCsv)) { throw "users.csv not found: $UsersCsv" }

$resultsRoot = Resolve-UnderRoot -Path $ResultsDir -Root '.\scripts\jmeter\results' -Name 'ResultsDir'
if (-not (Test-Path $resultsRoot)) { New-Item -ItemType Directory -Force $resultsRoot | Out-Null }

$usersCsvFull = (Resolve-Path $UsersCsv).Path
$jmx = '.\scripts\jmeter\flash-sale.jmx'
if (-not (Test-Path $jmx)) { throw "JMX not found: $jmx" }

for ($i = 0; $i -lt $threadCountList.Count; $i++) {
  $threads = $threadCountList[$i]
  $ramp = $rampSecondList[[Math]::Min($i, $rampSecondList.Count - 1)]
  $campaignId = $campaignIdList[$i]
  $resultFile = Join-Path $resultsRoot "result-$threads.jtl"
  $reportDir = Join-Path $resultsRoot "report-$threads"

  if (-not $NoOverwrite) {
    foreach ($target in @($resultFile, $reportDir)) {
      if (Test-Path $target) {
        $full = [System.IO.Path]::GetFullPath($target)
        if (-not $full.StartsWith($resultsRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
          throw "Refuse to delete path outside results root: $full"
        }
        Remove-Item -LiteralPath $full -Recurse -Force
      }
    }
  } elseif ((Test-Path $resultFile) -or (Test-Path $reportDir)) {
    throw "Result for $threads already exists. Remove it first or omit -NoOverwrite."
  }

  Write-Host "==> JMeter flash-sale: threads=$threads ramp=${ramp}s campaignId=$campaignId"
  & $jmeterBat -n -t $jmx "-JUSERS_CSV=$usersCsvFull" "-JHOST=$HostName" "-JPORT=$Port" "-JCAMPAIGN_ID=$campaignId" "-JTHREADS=$threads" "-JRAMP_SECONDS=$ramp" -l $resultFile -e -o $reportDir
  if ($LASTEXITCODE -ne 0) { throw "JMeter failed for $threads users with exit code $LASTEXITCODE" }
}

Write-Host 'All JMeter runs completed. Parse metrics with:'
Write-Host 'node scripts/eval/parse-jmeter-report.mjs --output docs/eval/reports/jmeter-summary.json'





