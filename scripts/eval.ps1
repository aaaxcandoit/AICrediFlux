param(
  [string]$BaseUrl = $(if ($env:AICREDIFLUX_EVAL_BASE_URL) { $env:AICREDIFLUX_EVAL_BASE_URL } else { 'http://127.0.0.1:9527' }),
  [string]$UserToken = $env:AICREDIFLUX_EVAL_USER_TOKEN,
  [string]$AdminToken = $env:AICREDIFLUX_EVAL_ADMIN_TOKEN,
  [string]$Model = $(if ($env:AICREDIFLUX_EVAL_MODEL) { $env:AICREDIFLUX_EVAL_MODEL } else { 'deepseek-v4-flash' }),
  [string]$JMeterResultsDir = 'scripts/jmeter/results',
  [string]$JMeterResult = '',
  [string]$AgentResult = 'scripts/agent/results/agent-summary.json',
  [string]$OutputDir = 'docs/eval/reports',
  [switch]$RunBackendTests,
  [switch]$RunFrontendChecks,
  [switch]$RunAgentEval,
  [switch]$SeedRag
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $scriptDir '..')
Set-Location $root

function Assert-WebLoginToken {
  param([string]$Name, [string]$Token)
  if ([string]::IsNullOrWhiteSpace($Token)) { return }
  if ($Token -match '^sk-') {
    throw "$Name looks like an API Key starting with sk-. Agent/RAG eval requires the Web login Sa-Token from /api/user/login or browser local storage, not an API Key used for /v1 model calls."
  }
}

function New-StepResult {
  param([string]$Name, [string]$Status, [object]$Data = $null)
  [ordered]@{
    name = $Name
    status = $Status
    data = $Data
    at = (Get-Date).ToUniversalTime().ToString('o')
  }
}

function Invoke-Step {
  param([string]$Name, [scriptblock]$Block)
  Write-Host "==> $Name"
  try {
    $data = & $Block
    return New-StepResult $Name 'PASSED' $data
  } catch {
    return New-StepResult $Name 'FAILED' @{ message = $_.Exception.Message }
  }
}

Assert-WebLoginToken 'UserToken' $UserToken
Assert-WebLoginToken 'AdminToken' $AdminToken

$startedAt = (Get-Date).ToUniversalTime().ToString('o')
$tmpDir = Join-Path $OutputDir '.tmp'
New-Item -ItemType Directory -Force $tmpDir | Out-Null
New-Item -ItemType Directory -Force $OutputDir | Out-Null

$jmeterJson = if ($JMeterResult) { $JMeterResult } else { Join-Path $OutputDir 'jmeter-summary.json' }
$agentJson = $AgentResult
$summaryJson = Join-Path $tmpDir 'eval-summary.json'
$steps = @()

if ($RunBackendTests) {
  $steps += Invoke-Step 'backend mvn test' { Push-Location 'aicrediflux-token-server'; try { & mvn -o test | Out-Host } finally { Pop-Location } }
} else {
  $steps += New-StepResult 'backend mvn test' 'NOT_RUN' 'Use -RunBackendTests to execute.'
}

if ($RunFrontendChecks) {
  $steps += Invoke-Step 'frontend type-check and build' { Push-Location 'aicrediflux-token-web'; try { & pnpm type-check | Out-Host; & pnpm build:prod | Out-Host } finally { Pop-Location } }
} else {
  $steps += New-StepResult 'frontend quality checks' 'NOT_RUN' 'Use -RunFrontendChecks to execute.'
}

if ($RunAgentEval) {
  $agentArgs = @(
    '-ExecutionPolicy', 'Bypass',
    '-File', '.\scripts\agent-eval.ps1',
    '-BaseUrl', $BaseUrl,
    '-Model', $Model,
    '-Output', $agentJson
  )
  if ($UserToken) { $agentArgs += @('-UserToken', $UserToken) }
  if ($AdminToken) { $agentArgs += @('-AdminToken', $AdminToken) }
  if ($SeedRag) { $agentArgs += '-SeedRag' }
  $steps += Invoke-Step 'agent/rag eval' { & powershell @agentArgs | Out-Host }
} elseif (Test-Path $agentJson) {
  $steps += New-StepResult 'agent/rag eval' 'PASSED' "Use existing result: $agentJson"
} else {
  $steps += New-StepResult 'agent/rag eval' 'MISSING' "Agent result not found: $agentJson. Run scripts/agent-eval.ps1 first."
}

if ($JMeterResult) {
  if (-not (Test-Path $jmeterJson)) { throw "JMeter result not found: $jmeterJson" }
  $steps += New-StepResult 'parse jmeter reports' 'PASSED' "Use existing result: $jmeterJson"
} elseif (Test-Path $JMeterResultsDir) {
  $steps += Invoke-Step 'parse jmeter reports' { & node scripts/eval/parse-jmeter-report.mjs --results-dir $JMeterResultsDir --output $jmeterJson | Out-Host }
} else {
  $steps += New-StepResult 'parse jmeter reports' 'MISSING' "JMeter results dir not found: $JMeterResultsDir. Run scripts/jmeter-eval.ps1 first."
}

& node scripts/eval/validate-eval-inputs.mjs --agent $agentJson --jmeter $jmeterJson | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'eval inputs are not full measured results' }

$stepsJson = Join-Path $tmpDir 'eval-steps.json'
[System.IO.File]::WriteAllText($stepsJson, ($steps | ConvertTo-Json -Depth 20), [System.Text.UTF8Encoding]::new($false))

$summaryArgs = @(
  'scripts/eval/build-eval-summary.mjs',
  '--output', $summaryJson,
  '--generated-at', $startedAt,
  '--base-url', $BaseUrl,
  '--model', $Model,
  '--steps', $stepsJson,
  '--jmeter', $jmeterJson,
  '--agent', $agentJson
)
& node @summaryArgs | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'build eval summary failed' }

$renderResult = & node scripts/eval/render-eval-report.mjs --input $summaryJson --output-dir $OutputDir
if ($LASTEXITCODE -ne 0) { throw 'render eval report failed' }
Write-Host $renderResult


