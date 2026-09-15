param(
  [string]$BaseUrl = $(if ($env:AICREDIFLUX_EVAL_BASE_URL) { $env:AICREDIFLUX_EVAL_BASE_URL } else { 'http://127.0.0.1:9527' }),
  [string]$UserToken = $env:AICREDIFLUX_EVAL_USER_TOKEN,
  [string]$AdminToken = $env:AICREDIFLUX_EVAL_ADMIN_TOKEN,
  [string]$Model = $(if ($env:AICREDIFLUX_EVAL_MODEL) { $env:AICREDIFLUX_EVAL_MODEL } else { 'deepseek-v4-flash' }),
  [string]$AgentSet = 'data/eval/golden/agent-golden-set.jsonl',
  [string]$RagSet = 'data/eval/golden/rag-golden-set.jsonl',
  [string]$Corpus = 'data/eval/corpus',
  [int]$TimeoutMs = $(if ($env:AICREDIFLUX_EVAL_TIMEOUT_MS) { [int]$env:AICREDIFLUX_EVAL_TIMEOUT_MS } else { 120000 }),
  [switch]$SeedRag,
  [string]$Output = 'scripts/agent/results/agent-summary.json'
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

Assert-WebLoginToken 'UserToken' $UserToken
Assert-WebLoginToken 'AdminToken' $AdminToken

$outputDir = Split-Path -Parent $Output
if (-not $outputDir) { $outputDir = '.' }
New-Item -ItemType Directory -Force $outputDir | Out-Null

$argsList = @(
  'scripts/eval/copilot-golden-eval.mjs',
  '--base-url', $BaseUrl,
  '--model', $Model,
  '--agent-set', $AgentSet,
  '--rag-set', $RagSet,
  '--corpus', $Corpus,
  '--timeout', $TimeoutMs,
  '--output', $Output
)
if ($UserToken) { $argsList += @('--user-token', $UserToken) }
if ($AdminToken) { $argsList += @('--admin-token', $AdminToken) }
if ($SeedRag) { $argsList += '--seed-rag' }

Write-Host "==> RAG corpus coverage preflight"
$coverageRaw = & node scripts/eval/validate-rag-corpus-coverage.mjs --rag-set $RagSet --corpus $Corpus
if ($LASTEXITCODE -ne 0) { throw "RAG corpus coverage preflight failed with exit code $LASTEXITCODE" }
$coverageRaw | Out-Host

$coverageFile = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), ('aicrediflux-rag-coverage-' + [guid]::NewGuid().ToString('N') + '.json'))
[System.IO.File]::WriteAllText($coverageFile, ($coverageRaw -join [Environment]::NewLine), [System.Text.UTF8Encoding]::new($false))

Write-Host "==> Agent/RAG eval"
try {
  & node @argsList
  if ($LASTEXITCODE -ne 0) { throw "Agent/RAG eval failed with exit code $LASTEXITCODE" }
  if (Test-Path $Output) {
    & node scripts/eval/attach-rag-coverage.mjs --input $Output --coverage $coverageFile --output $Output
    if ($LASTEXITCODE -ne 0) { throw "Attach RAG coverage failed with exit code $LASTEXITCODE" }
  }
} finally {
  if (Test-Path $coverageFile) { Remove-Item -LiteralPath $coverageFile -Force }
}
Write-Host "Agent/RAG eval result: $Output"
