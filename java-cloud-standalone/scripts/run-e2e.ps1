# Gateway E2E for java-cloud-standalone (reports under <repo>/test-output/cloud-e2e)
$ErrorActionPreference = "Continue"
$Base = "http://127.0.0.1:10086"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$OutDir = Join-Path $RepoRoot "test-output\cloud-e2e"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Report = Join-Path $OutDir "report.json"
$Results = [System.Collections.Generic.List[object]]::new()

function Issue-Ticket([string]$purpose) {
  $ticket = [guid]::NewGuid().ToString("N")
  docker exec forum-redis-dev redis-cli -a 123456 SET "forum:captchaTicket:$ticket" $purpose EX 180 2>$null | Out-Null
  return $ticket
}

function Invoke-Api {
  param(
    [string]$Name,
    [string]$Method = "GET",
    [string]$Path,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [int]$TimeoutSec = 60,
    [scriptblock]$OkWhen = $null
  )
  $uri = "$Base$Path"
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $item = [ordered]@{
    name = $Name
    method = $Method
    path = $Path
    ok = $false
    http = $null
    code = $null
    message = $null
    ms = 0
    detail = $null
  }
  try {
    $hdr = @{}
    foreach ($k in $Headers.Keys) { $hdr[$k] = $Headers[$k] }
    $params = @{
      Method = $Method
      Uri = $uri
      Headers = $hdr
      TimeoutSec = $TimeoutSec
    }
    if ($null -ne $Body) {
      # Windows PowerShell 默认用系统 ANSI 发 Body，中文会变成乱码；统一 UTF-8 字节
      $params.ContentType = "application/json; charset=utf-8"
      $jsonText = if ($Body -is [string]) { $Body } else { ($Body | ConvertTo-Json -Depth 8 -Compress) }
      $params.Body = [System.Text.Encoding]::UTF8.GetBytes($jsonText)
    }
    $resp = Invoke-WebRequest @params
    $sw.Stop()
    $item.http = [int]$resp.StatusCode
    $item.ms = $sw.ElapsedMilliseconds
    $json = $null
    try { $json = $resp.Content | ConvertFrom-Json } catch {}
    if ($json -and ($json.PSObject.Properties.Name -contains "code")) {
      $item.code = $json.code
      $item.message = $json.message
      $item.detail = $json.data
      if ($OkWhen) { $item.ok = [bool](& $OkWhen $json $resp) }
      else { $item.ok = ($json.code -eq 0) }
    } else {
      # raw boolean / non-Result endpoints
      $item.detail = $resp.Content
      if ($OkWhen) { $item.ok = [bool](& $OkWhen $null $resp) }
      else { $item.ok = ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300) }
    }
  } catch {
    $sw.Stop()
    $item.ms = $sw.ElapsedMilliseconds
    $item.message = $_.Exception.Message
    try {
      $r = $_.Exception.Response
      if ($r) {
        $item.http = [int]$r.StatusCode
        $sr = New-Object System.IO.StreamReader($r.GetResponseStream())
        $txt = $sr.ReadToEnd()
        $item.detail = $txt
        try {
          $j = $txt | ConvertFrom-Json
          $item.code = $j.code
          $item.message = $j.message
        } catch {}
      }
    } catch {}
    if ($OkWhen) {
      try { $item.ok = [bool](& $OkWhen $null $null) } catch { $item.ok = $false }
    }
  }
  $Results.Add([pscustomobject]$item) | Out-Null
  $script:LastApiResult = [pscustomobject]$item
  $flag = if ($item.ok) { "PASS" } else { "FAIL" }
  Write-Host ("[{0}] {1} {2} http={3} code={4} {5}ms {6}" -f $flag, $Method, $Name, $item.http, $item.code, $item.ms, $item.message)
}

function Register-User([string]$userName, [string]$password, [string]$nickname) {
  $ticket = Issue-Ticket "REGISTER"
  $body = @{
    userName = $userName
    password = $password
    nickname = $nickname
    captchaTicket = $ticket
  }
  Invoke-Api -Name "register:$userName" -Method POST -Path "/user/register" -Body $body
  return $script:LastApiResult
}

function Login-User([string]$userName, [string]$password) {
  $ticket = Issue-Ticket "USER_LOGIN"
  $headers = @{ "X-Captcha-Ticket" = $ticket }
  $body = @{ userName = $userName; password = $password }
  Invoke-Api -Name "login:$userName" -Method POST -Path "/user/login" -Headers $headers -Body $body -OkWhen {
    param($json, $resp)
    return ($json.code -eq 0 -and $json.data -and $json.data.token)
  }
  $item = $script:LastApiResult
  $token = $null
  $uid = $null
  if ($item.ok) {
    $token = [string]$item.detail.token
    $uid = [long]$item.detail.id
  }
  return @{ item = $item; token = $token; userId = $uid }
}

Write-Host "=== Cloud E2E start $(Get-Date -Format o) ==="

# 0) health / public
Invoke-Api -Name "gateway-health" -Path "/actuator/health" -OkWhen { param($j,$r) $r.StatusCode -eq 200 }
Invoke-Api -Name "captcha-generate" -Method POST -Path "/captcha/generate" -Body @{ type = "SLIDER" } -OkWhen {
  param($j,$r) $r.StatusCode -eq 200
}
Invoke-Api -Name "hot-articles" -Path "/article/getHotArticleList"
Invoke-Api -Name "search-article" -Path "/search/article?keyword=test&pageNum=1&pageSize=5"
Invoke-Api -Name "search-user" -Path "/search/user?keyword=plu&pageNum=1&pageSize=5"
Invoke-Api -Name "board-top" -Path "/board/topBoardList?orderByStatus=0"
Invoke-Api -Name "board-map" -Path "/board/selectBoardBy"
Invoke-Api -Name "category-boards" -Path "/category/getCategoryWithBoards"
Invoke-Api -Name "recommend-feed" -Path "/recommend/feed?pageNum=1&pageSize=5"
Invoke-Api -Name "shop-list" -Path "/shop/list"
Invoke-Api -Name "mascot-models" -Path "/mascot/public/models"
Invoke-Api -Name "user-internal-exists" -Path "/user/internal/1/exists" -OkWhen {
  param($j,$r) ($r.Content -match "true|True|false|False")
}

# 1) create 3 accounts
$suffix = Get-Date -Format "HHmmss"
$accounts = @(
  @{ user = "e2ea$suffix"; pass = "Test123456"; nick = "E2ENickA$suffix" },
  @{ user = "e2eb$suffix"; pass = "Test123456"; nick = "E2ENickB$suffix" },
  @{ user = "e2ec$suffix"; pass = "Test123456"; nick = "E2ENickC$suffix" }
)
$sessions = New-Object System.Collections.Generic.List[object]
foreach ($a in $accounts) {
  $null = Register-User $a.user $a.pass $a.nick
  $s = Login-User $a.user $a.pass
  Write-Host ("SESSION {0} ok={1} uid={2} tokenLen={3}" -f $a.user, $s.item.ok, $s.userId, ($(if ($s.token) { $s.token.Length } else { 0 })))
  $sessions.Add($s) | Out-Null
}

$A = $sessions[0]; $B = $sessions[1]; $C = $sessions[2]
if (-not $A.token -or -not $B.token -or -not $C.token) {
  Write-Host "FATAL: login failed, abort domain tests"
  $Results | ConvertTo-Json -Depth 6 | Set-Content -Path $Report -Encoding UTF8
  exit 1
}

$hA = @{ Authorization = $A.token }
$hB = @{ Authorization = $B.token }
$hC = @{ Authorization = $C.token }

# 2) auth domain
Invoke-Api -Name "me-A" -Path "/user/getUserByIdForLogin" -Headers $hA
Invoke-Api -Name "profile-interests-A" -Path "/profile/interests" -Headers $hA -OkWhen {
  param($j,$r) $r.StatusCode -lt 500
}
Invoke-Api -Name "login-logs-A" -Path "/user/loginLogs" -Headers $hA
Invoke-Api -Name "follow-A-B" -Method PUT -Path "/user/followUser?followeeId=$($B.userId)" -Headers $hA
Invoke-Api -Name "follow-stats-B" -Path "/user/followStats?userId=$($B.userId)" -Headers $hA
Invoke-Api -Name "following-ids-A" -Path "/user/followingIds" -Headers $hA
Invoke-Api -Name "is-online-A" -Path "/user/isOnline?userId=$($A.userId)" -Headers $hA

# 3) economy
Invoke-Api -Name "points-wallet-A" -Path "/points/wallet" -Headers $hA
Invoke-Api -Name "points-log-A" -Path "/points/log?pageNum=1&pageSize=5" -Headers $hA
Invoke-Api -Name "points-daily-A" -Path "/points/daily?days=7" -Headers $hA
Invoke-Api -Name "checkin-info-A" -Path "/checkin/info" -Headers $hA
Invoke-Api -Name "checkin-rule" -Path "/checkin/rule" -Headers $hA
Invoke-Api -Name "checkin-do-A" -Method POST -Path "/checkin/doCheckin" -Headers $hA
Invoke-Api -Name "growth-overview-A" -Path "/growth/overview" -Headers $hA
Invoke-Api -Name "growth-challenges-A" -Path "/growth/challenges?pageNum=1&pageSize=5" -Headers $hA
Invoke-Api -Name "vip-center-A" -Path "/vip/center" -Headers $hA
Invoke-Api -Name "lottery-info-A" -Path "/lottery/info" -Headers $hA -OkWhen {
  param($j,$r)
  # accept business success or known business fail, not 5xx gateway
  return ($r.StatusCode -lt 500)
}

# 4) content
Invoke-Api -Name "favorite-folders-A" -Path "/favorite/folder/myList" -Headers $hA
Invoke-Api -Name "article-tags" -Path "/article/tag/list?boardId=1"
Invoke-Api -Name "article-tag-suggest" -Path "/article/tag/suggest?boardId=1&keyword=a&title=hello"

# 5) IM
Invoke-Api -Name "msg-sessions-A" -Path "/message/queryMessageSessionWithPage?pageNum=1&pageSize=5" -Headers $hA -OkWhen {
  param($j,$r) $r.StatusCode -lt 500
}
Invoke-Api -Name "msg-send-A-B" -Method POST -Path "/message/sendMessage" -Headers $hA -Body (@{
  receiveUserId = $B.userId
  content = "hello from A $suffix"
}) -OkWhen {
  param($j,$r)
  # AI Hub 未启动时内容审核会失败(1125)，记为环境依赖而非网关/拆分故障
  return ([int]$j.code -eq 0 -or [int]$j.code -eq 1125)
}
Invoke-Api -Name "sysmsg-unread-A" -Path "/system-message/unreadCount" -Headers $hA
Invoke-Api -Name "sysmsg-list-A" -Path "/system-message/list?pageNum=1&pageSize=5" -Headers $hA
Invoke-Api -Name "notice-center-A" -Path "/notice/center/list?pageNum=1&pageSize=5" -Headers $hA -OkWhen {
  param($j,$r) $r.StatusCode -lt 500
}
Invoke-Api -Name "voice-ice" -Path "/voice/ice-config" -Headers $hA
Invoke-Api -Name "group-sessions-A" -Path "/group-chat/sessions" -Headers $hA
Invoke-Api -Name "group-public" -Path "/group-chat/public?pageNum=1&pageSize=5" -Headers $hA -OkWhen {
  param($j,$r) $r.StatusCode -lt 500
}

# 6) game
Invoke-Api -Name "game-center" -Path "/game/center/overview" -Headers $hA
Invoke-Api -Name "game-gobang-profile" -Path "/game/gobang/profile" -Headers $hA
Invoke-Api -Name "game-jinzi-profile" -Path "/game/jinzi/profile" -Headers $hA
Invoke-Api -Name "game-tetris-profile" -Path "/game/tetris/profile" -Headers $hA
Invoke-Api -Name "game-tetris-lb" -Path "/game/tetris/leaderboard" -Headers $hA

# 7) AI — moodType 用码点拼，避免 PS5.1 无 BOM UTF-8 脚本把中文读坏
$driftMood = -join ([char[]]@(0x968F, 0x4FBF, 0x8BF4, 0x8BF4)) # 随便说说
Invoke-Api -Name "mascot-quota-A" -Path "/mascot/quota-hint" -Headers $hA
Invoke-Api -Name "drift-quota-A" -Path "/drift-bottle/quota" -Headers $hA
Invoke-Api -Name "drift-create-A" -Method POST -Path "/drift-bottle/create" -Headers $hA -Body (@{
  content = "e2e bottle content for cloud migration test pad-$suffix-pad"
  moodType = $driftMood
})
Invoke-Api -Name "drift-mine-A" -Path "/drift-bottle/mine?pageNum=1&pageSize=5" -Headers $hA
Invoke-Api -Name "drift-pick-B" -Path "/drift-bottle/pick" -Headers $hB

# 8) cross-service Feign internals (direct through gateway routes)
Invoke-Api -Name "points-internal-balance-A" -Path "/points/internal/$($A.userId)/balance" -OkWhen {
  param($j,$r) $r.StatusCode -eq 200
}
Invoke-Api -Name "growth-require-formal-A" -Method POST -Path "/growth/internal/$($A.userId)/require-formal" -OkWhen {
  param($j,$r) $r.StatusCode -lt 500
}

# 9) multi-user: B replies message path / unfollow
Invoke-Api -Name "msg-send-B-A" -Method POST -Path "/message/sendMessage" -Headers $hB -Body (@{
  receiveUserId = $A.userId
  content = "reply from B $suffix"
}) -OkWhen {
  param($j,$r)
  return ([int]$j.code -eq 0 -or [int]$j.code -eq 1125)
}
Invoke-Api -Name "unfollow-A-B" -Method PUT -Path "/user/unfollowUser?followeeId=$($B.userId)" -Headers $hA
Invoke-Api -Name "follow-C-A" -Method PUT -Path "/user/followUser?followeeId=$($A.userId)" -Headers $hC

# 10) logout A (optional)
Invoke-Api -Name "logout-A" -Method POST -Path "/user/logout" -Headers $hA

$pass = @($Results | Where-Object { $_.ok }).Count
$fail = @($Results | Where-Object { -not $_.ok }).Count
$summary = [ordered]@{
  startedAt = (Get-Date).ToString("o")
  base = $Base
  accounts = $accounts.user
  total = $Results.Count
  pass = $pass
  fail = $fail
  failures = @($Results | Where-Object { -not $_.ok } | Select-Object name, method, path, http, code, message)
  results = $Results
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $Report -Encoding UTF8
Write-Host "=== DONE pass=$pass fail=$fail report=$Report ==="
if ($fail -gt 0) { exit 2 } else { exit 0 }

