param(
  [string]$BaseUrl = "http://127.0.0.1:9527",
  [string]$Username = "root",
  [string]$Password = "Gt45GLpcaWxF",
  [switch]$SkipPublish
)

$ErrorActionPreference = "Stop"

function Invoke-AicfApi {
  param(
    [ValidateSet("GET", "POST", "PUT")][string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [string]$Token = $null
  )

  $headers = @{}
  if ($Token) { $headers["aicrediflux"] = $Token }
  $args = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    Headers = $headers
  }
  if ($null -ne $Body) {
    $args.ContentType = "application/json; charset=utf-8"
    $args.Body = ($Body | ConvertTo-Json -Depth 20 -Compress)
  }

  $resp = Invoke-RestMethod @args
  if ($null -ne $resp.flag -and -not $resp.flag) {
    throw "API failed: $Path $($resp.msg)"
  }
  return $resp.data
}

function New-IsoTime {
  param([int]$OffsetMinutes)
  return (Get-Date).ToUniversalTime().AddMinutes($OffsetMinutes).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
}

Write-Host "Logging in as $Username ..."
$login = Invoke-AicfApi -Method POST -Path "/api/user/login" -Body @{ username = $Username; password = $Password }
$token = $login.token
if (-not $token) { throw "Login did not return token" }

$packages = Invoke-AicfApi -Method GET -Path "/api/admin/token-packages" -Token $token

function Ensure-Package {
  param(
    [string]$Name,
    [string]$Description,
    [long]$CreditAmount,
    [long]$OriginalPrice,
    [long]$SalePrice,
    [int]$Sort
  )
  $existing = @($packages | Where-Object { $_.name -eq $Name }) | Select-Object -First 1
  if ($existing) {
    Write-Host "Package exists: $Name (#$($existing.id))"
    return $existing
  }
  $created = Invoke-AicfApi -Method POST -Path "/api/admin/token-packages" -Token $token -Body @{
    name = $Name
    description = $Description
    creditAmount = $CreditAmount
    originalPrice = $OriginalPrice
    salePrice = $SalePrice
    status = 1
    sort = $Sort
  }
  Write-Host "Package created: $Name (#$($created.id))"
  $script:packages += $created
  return $created
}

$starter = Ensure-Package -Name "Demo 10万 AI Credit 体验包" -Description "前后端联调用小额套餐" -CreditAmount 100000 -OriginalPrice 990 -SalePrice 490 -Sort 101
$standard = Ensure-Package -Name "Demo 100万 AI Credit 标准包" -Description "普通购买和模拟支付联调" -CreditAmount 1000000 -OriginalPrice 5900 -SalePrice 3900 -Sort 102
$flashPack = Ensure-Package -Name "Demo 500万 AI Credit 秒杀包" -Description "秒杀、库存和钱包到账联调" -CreditAmount 5000000 -OriginalPrice 19900 -SalePrice 9900 -Sort 103

$runId = Get-Date -Format "yyyyMMdd-HHmmss"

function New-Campaign {
  param(
    [string]$Name,
    [long]$PackageId,
    [long]$FlashPrice,
    [long]$CreditAmount,
    [int]$TotalStock,
    [int]$StartOffsetMinutes,
    [int]$EndOffsetMinutes,
    [bool]$Publish
  )
  $campaign = Invoke-AicfApi -Method POST -Path "/api/admin/flash-sale" -Token $token -Body @{
    packageId = $PackageId
    activityName = "$Name $runId"
    flashPrice = $FlashPrice
    creditAmount = $CreditAmount
    totalStock = $TotalStock
    startTime = (New-IsoTime $StartOffsetMinutes)
    endTime = (New-IsoTime $EndOffsetMinutes)
  }
  Write-Host "Campaign created: $($campaign.activityName) (#$($campaign.id), stock=$TotalStock)"
  if ($Publish -and -not $SkipPublish) {
    $campaign = Invoke-AicfApi -Method POST -Path "/api/admin/flash-sale/$($campaign.id)/publish" -Token $token
    Write-Host "Campaign published and warmed up in Redis: #$($campaign.id)"
  }
  return $campaign
}

$active = New-Campaign -Name "Demo 正在进行秒杀" -PackageId $flashPack.id -FlashPrice 990 -CreditAmount 5000000 -TotalStock 50 -StartOffsetMinutes -5 -EndOffsetMinutes 120 -Publish $true
$upcoming = New-Campaign -Name "Demo 5分钟后开始秒杀" -PackageId $flashPack.id -FlashPrice 1990 -CreditAmount 5000000 -TotalStock 30 -StartOffsetMinutes 5 -EndOffsetMinutes 125 -Publish $true
$tiny = New-Campaign -Name "Demo 小库存秒杀" -PackageId $standard.id -FlashPrice 390 -CreditAmount 1000000 -TotalStock 3 -StartOffsetMinutes -1 -EndOffsetMinutes 60 -Publish $true
$draft = New-Campaign -Name "Demo 管理端草稿活动" -PackageId $starter.id -FlashPrice 99 -CreditAmount 100000 -TotalStock 10 -StartOffsetMinutes 30 -EndOffsetMinutes 180 -Publish $false

Write-Host ""
Write-Host "Seed complete. Frontend pages:"
Write-Host "  $BaseUrl is backend; open frontend http://127.0.0.1:5180/token-mall"
Write-Host "  /token-mall       shows active/upcoming published campaigns"
Write-Host "  /orders/token     shows user orders after purchase/flash sale"
Write-Host "  /admin/flash-sale shows published and draft campaigns"
Write-Host ""
Write-Host "Created campaign ids: active=$($active.id), upcoming=$($upcoming.id), tiny=$($tiny.id), draft=$($draft.id)"