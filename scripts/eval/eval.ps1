param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$RemainingArgs
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootScript = Resolve-Path (Join-Path $scriptDir '..\eval.ps1')

Write-Host 'scripts/eval/eval.ps1 is kept for compatibility. Prefer scripts/eval.ps1.'
& powershell -ExecutionPolicy Bypass -File $rootScript @RemainingArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }