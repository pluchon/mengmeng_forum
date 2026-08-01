# Multi-user interaction probe for java-cloud-standalone (reports under test-output/)
$ErrorActionPreference = "Continue"
$Base = "http://127.0.0.1:10086"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$OutDir = Join-Path $RepoRoot "test-output\cloud-multiuser"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Report = Join-Path $OutDir "report.json"
$Results = [System.Collections.Generic.List[object]]::new()
$Bugs = [System.Collections.Generic.List[object]]::new()

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
    [int]$TimeoutSec = 90,
    [scriptblock]$OkWhen = $null,
    [string]$ExpectNote = ""
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
    note = $ExpectNote
  }
  try {
    $hdr = @{}
    foreach ($k in $Headers.Keys) { $hdr[$k] = $Headers[$k] }
    $params = @{ Method = $Method; Uri = $uri; Headers = $hdr; TimeoutSec = $TimeoutSec }
    if ($null -ne $Body) {
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
          if ($j.PSObject.Properties.Name -contains "data") { $item.detail = $j.data }
        } catch {}
      }
    } catch {}
    if ($OkWhen) {
      try { $item.ok = [bool](& $OkWhen $null $null) } catch { $item.ok = $false }
    }
  }
  $Results.Add([pscustomobject]$item) | Out-Null
  $script:LastApi = [pscustomobject]$item
  $flag = if ($item.ok) { "PASS" } else { "FAIL" }
  Write-Host ("[{0}] {1} {2} http={3} code={4} {5}ms {6}" -f $flag, $Method, $Name, $item.http, $item.code, $item.ms, $item.message)
  return $script:LastApi
}

function Add-Bug([string]$Title, [string]$Severity, [string]$Repro, [object]$Evidence) {
  $Bugs.Add([pscustomobject]@{
    title = $Title
    severity = $Severity
    repro = $Repro
    evidence = $Evidence
  }) | Out-Null
  Write-Host ("!! BUG [{0}] {1}" -f $Severity, $Title) -ForegroundColor Yellow
}

function Register-User([string]$userName, [string]$password, [string]$nickname) {
  $ticket = Issue-Ticket "REGISTER"
  return Invoke-Api -Name "register:$userName" -Method POST -Path "/user/register" -Body @{
    userName = $userName; password = $password; nickname = $nickname; captchaTicket = $ticket
  }
}

function Login-User([string]$userName, [string]$password) {
  $ticket = Issue-Ticket "USER_LOGIN"
  $item = Invoke-Api -Name "login:$userName" -Method POST -Path "/user/login" `
    -Headers @{ "X-Captcha-Ticket" = $ticket } `
    -Body @{ userName = $userName; password = $password } `
    -OkWhen { param($j,$r) return ($j.code -eq 0 -and $j.data -and $j.data.token) }
  $token = $null; $uid = $null
  if ($item.ok) {
    $token = [string]$item.detail.token
    $uid = [long]$item.detail.id
  }
  return @{ item = $item; token = $token; userId = $uid; userName = $userName }
}

Write-Host "=== Multi-user probe $(Get-Date -Format o) ==="

# AI hub readiness
$aiOk = $false
try {
  $ah = Invoke-WebRequest "http://127.0.0.1:5000/health" -UseBasicParsing -TimeoutSec 3
  $aiOk = ($ah.StatusCode -eq 200)
} catch {}
Write-Host "AI_HUB=$aiOk"

$suffix = Get-Date -Format "HHmmss"
$pass = "Test123456"
$accounts = @(
  @{ user = "mua$suffix"; nick = "MuA$suffix" },
  @{ user = "mub$suffix"; nick = "MuB$suffix" },
  @{ user = "muc$suffix"; nick = "MuC$suffix" }
)
$sessions = @()
foreach ($a in $accounts) {
  $null = Register-User $a.user $pass $a.nick
  $s = Login-User $a.user $pass
  Write-Host ("SESSION {0} ok={1} uid={2}" -f $a.user, $s.item.ok, $s.userId)
  $sessions += $s
}
$A = $sessions[0]; $B = $sessions[1]; $C = $sessions[2]
if (-not ($A.token -and $B.token -and $C.token)) {
  Write-Host "FATAL: login failed"
  $Results | ConvertTo-Json -Depth 8 | Set-Content $Report -Encoding UTF8
  exit 1
}
$hA = @{ Authorization = $A.token }
$hB = @{ Authorization = $B.token }
$hC = @{ Authorization = $C.token }

# Promote A to certified creator for group create
docker exec forum-mysql-dev mysql -uroot -p123456789 forum_db -e "UPDATE user SET creator_state=1 WHERE id=$($A.userId);" 2>$null | Out-Null
Write-Host "Promoted creator_state=1 for A uid=$($A.userId)"

# ---------- 1) Follow graph (mutual + third party) ----------
$f1 = Invoke-Api -Name "follow-A-B" -Method PUT -Path "/user/followUser?followeeId=$($B.userId)" -Headers $hA
$f2 = Invoke-Api -Name "follow-B-A" -Method PUT -Path "/user/followUser?followeeId=$($A.userId)" -Headers $hB
$f3 = Invoke-Api -Name "follow-C-A" -Method PUT -Path "/user/followUser?followeeId=$($A.userId)" -Headers $hC
$statsB = Invoke-Api -Name "follow-stats-B" -Path "/user/followStats?userId=$($B.userId)" -Headers $hA
if ($statsB.ok) {
  $isFollowing = $statsB.detail.isFollowing
  if ($isFollowing -ne $true) {
    Add-Bug "关注后 followStats.isFollowing 对关注者不为 true" "P1" "A follow B 后 A 查 B 的 followStats" $statsB
  }
}
$statsAFromB = Invoke-Api -Name "follow-stats-A-from-B" -Path "/user/followStats?userId=$($A.userId)" -Headers $hB
# A follows B and B follows A -> mutual; B viewing A should see isFollowing true
if ($statsAFromB.ok -and $statsAFromB.detail.isFollowing -ne $true) {
  Add-Bug "互关后 B 查 A 的 isFollowing 不为 true" "P1" "A<->B 互关后 B GET followStats(A)" $statsAFromB
}

# self-follow should fail
$selfFollow = Invoke-Api -Name "follow-self-A" -Method PUT -Path "/user/followUser?followeeId=$($A.userId)" -Headers $hA `
  -OkWhen { param($j,$r) return ($j.code -ne 0) } -ExpectNote "expect reject"
if (-not $selfFollow.ok) {
  Add-Bug "允许关注自己（应拒绝）" "P0" "PUT /user/followUser?followeeId=自己" $selfFollow
}

# ---------- 2) Private message A->B->A read/unread/recall ----------
$msgAb = Invoke-Api -Name "msg-A-to-B" -Method POST -Path "/message/sendMessage" -Headers $hA -Body @{
  receiveUserId = $B.userId
  content = "multiuser hello from A to B $suffix"
} -OkWhen {
  param($j,$r)
  if (-not $aiOk) { return ([int]$j.code -eq 0 -or [int]$j.code -eq 1125) }
  return ([int]$j.code -eq 0)
}
if ($aiOk -and -not $msgAb.ok) {
  Add-Bug "AI Hub 已启动但 A->B 私信仍失败" "P0" "POST /message/sendMessage" $msgAb
} elseif (-not $aiOk -and [int]$msgAb.code -eq 1125) {
  Add-Bug "私信依赖 AI Hub，Hub 未启动导致全站私信不可用(1125)" "P1-env" "POST /message/sendMessage without AI Hub" $msgAb
}

$msgBa = $null
$msgIdAb = $null
if ($msgAb.ok -and [int]$msgAb.code -eq 0) {
  if ($msgAb.detail -and $msgAb.detail.messageId) { $msgIdAb = $msgAb.detail.messageId }
  elseif ($msgAb.detail -and $msgAb.detail.id) { $msgIdAb = $msgAb.detail.id }

  $unreadB = Invoke-Api -Name "unread-B" -Path "/message/getUnReadMessage" -Headers $hB
  if ($unreadB.ok) {
    $uc = 0
    try { $uc = [int]$unreadB.detail } catch {
      try { $uc = [int]$unreadB.detail.unReadCount } catch {}
      try { if ($unreadB.detail.count) { $uc = [int]$unreadB.detail.count } } catch {}
    }
    # detail shape varies; just ensure call works; deeper check via session
  }

  $sessB = Invoke-Api -Name "sessions-B" -Path "/message/queryMessageSessionWithPage?pageNum=1&pageSize=20" -Headers $hB
  if ($sessB.ok) {
    $list = @()
    if ($sessB.detail -is [System.Array]) { $list = $sessB.detail }
    elseif ($sessB.detail.list) { $list = @($sessB.detail.list) }
    elseif ($sessB.detail.records) { $list = @($sessB.detail.records) }
    $hit = $list | Where-Object {
      ($_.contactId -eq $A.userId) -or ($_.userId -eq $A.userId) -or ($_.sendUserId -eq $A.userId) -or ($_.receiveUserId -eq $A.userId)
    } | Select-Object -First 1
    if (-not $hit) {
      Add-Bug "B 会话列表未出现刚收到的 A 会话" "P0" "A 发信后 B queryMessageSessionWithPage" @{ sessions = $list; from = $A.userId }
    }
  }

  $detailB = Invoke-Api -Name "detail-B-with-A" -Path "/message/queryMessageDetailWithPage?receiveId=$($A.userId)&pageNum=1&pageSize=20" -Headers $hB
  $mark = Invoke-Api -Name "mark-read-B" -Method PUT -Path "/message/markAllMessageReadBySender?senderId=$($A.userId)" -Headers $hB

  $msgBa = Invoke-Api -Name "msg-B-to-A" -Method POST -Path "/message/sendMessage" -Headers $hB -Body @{
    receiveUserId = $A.userId
    content = "reply from B to A $suffix"
  }

  # C should NOT see A-B private thread
  $leak = Invoke-Api -Name "detail-C-with-A" -Path "/message/queryMessageDetailWithPage?receiveId=$($A.userId)&pageNum=1&pageSize=20" -Headers $hC `
    -OkWhen { param($j,$r) return ($r.StatusCode -lt 500) }
  if ($leak.ok -and $leak.detail) {
    $clist = @()
    if ($leak.detail -is [System.Array]) { $clist = $leak.detail }
    elseif ($leak.detail.list) { $clist = @($leak.detail.list) }
    elseif ($leak.detail.records) { $clist = @($leak.detail.records) }
    $foreign = $clist | Where-Object {
      ($_.content -like "*multiuser hello from A to B*") -or ($_.content -like "*reply from B to A*")
    }
    if ($foreign) {
      Add-Bug "C 可读取 A-B 私信内容（越权）" "P0" "C queryMessageDetailWithPage receiveId=A" $foreign
    }
  }

  # self message should fail
  $selfMsg = Invoke-Api -Name "msg-self-A" -Method POST -Path "/message/sendMessage" -Headers $hA -Body @{
    receiveUserId = $A.userId
    content = "should not send to self $suffix"
  } -OkWhen { param($j,$r) return ($j.code -ne 0) }
  if (-not $selfMsg.ok) {
    Add-Bug "允许给自己发私信" "P1" "POST sendMessage receiveUserId=自己" $selfMsg
  }
}

# ---------- 3) Group chat: create / invite / accept / send / leave ----------
$groupId = $null
$createG = Invoke-Api -Name "group-create-A" -Method POST -Path "/group-chat/create" -Headers $hA -Body @{
  name = "MuGroup$suffix"
  intro = "multiuser group for e2e"
  avatarUrl = $null
  groupType = 0
}
if ($createG.ok) {
  if ($createG.detail.groupId) { $groupId = [long]$createG.detail.groupId }
  elseif ($createG.detail.id) { $groupId = [long]$createG.detail.id }
} else {
  Add-Bug "认证创作者创建公开群失败" "P0" "POST /group-chat/create after SQL creator_state=1" $createG
}

if ($groupId) {
  # B joins public
  $joinB = Invoke-Api -Name "group-join-B" -Method POST -Path "/group-chat/$groupId/join" -Headers $hB
  # invite C
  $invC = Invoke-Api -Name "group-invite-C" -Method POST -Path "/group-chat/$groupId/invite" -Headers $hA -Body @{
    inviteeUserId = $C.userId
  }
  $requestId = $null
  if ($invC.ok) {
    if ($invC.detail.requestId) { $requestId = $invC.detail.requestId }
    elseif ($invC.detail.id) { $requestId = $invC.detail.id }
  }

  # If join creates pending request, try approve path; else accept invite
  if ($requestId) {
    $acc = Invoke-Api -Name "group-accept-C" -Method PUT -Path "/group-chat/requests/$requestId/accept" -Headers $hC
    if (-not $acc.ok) {
      # maybe owner approve needed for join request
      $null = Invoke-Api -Name "group-approve-C" -Method PUT -Path "/group-chat/requests/$requestId/approve" -Headers $hA
    }
  } elseif ($joinB.ok -eq $false -and $joinB.code) {
    # join may return request id in detail
    if ($joinB.detail.requestId) {
      $null = Invoke-Api -Name "group-approve-join-B" -Method PUT -Path "/group-chat/requests/$($joinB.detail.requestId)/approve" -Headers $hA
    }
  }

  # If invite returned no requestId, C may still have pending - soft continue
  $sendA = Invoke-Api -Name "group-msg-A" -Method POST -Path "/group-chat/messages" -Headers $hA -Body @{
    groupId = $groupId
    messageType = 0
    content = "group hello from owner A $suffix"
    replyMessageId = $null
  }
  if (-not $sendA.ok) {
    Add-Bug "群主在自建群发消息失败" "P0" "POST /group-chat/messages" $sendA
  }

  $sendB = Invoke-Api -Name "group-msg-B" -Method POST -Path "/group-chat/messages" -Headers $hB -Body @{
    groupId = $groupId
    messageType = 0
    content = "group hello from member B $suffix"
    replyMessageId = $null
  }
  if ($joinB.ok -or $sendB.ok) {
    if (-not $sendB.ok) {
      Add-Bug "已加入/申请成功后 B 仍无法在群发言" "P1" "B POST /group-chat/messages" $sendB
    }
  }

  $histA = Invoke-Api -Name "group-hist-A" -Path "/group-chat/$groupId/messages?pageNum=1&pageSize=50" -Headers $hA
  $histB = Invoke-Api -Name "group-hist-B" -Path "/group-chat/$groupId/messages?pageNum=1&pageSize=50" -Headers $hB
  $histC = Invoke-Api -Name "group-hist-C" -Path "/group-chat/$groupId/messages?pageNum=1&pageSize=50" -Headers $hC `
    -OkWhen { param($j,$r) return ($r.StatusCode -lt 500) }

  # Non-member C reading history: should be forbidden or empty without owner messages
  if ($histC.ok -and [int]$histC.code -eq 0) {
    $hlist = @()
    if ($histC.detail -is [System.Array]) { $hlist = $histC.detail }
    elseif ($histC.detail.list) { $hlist = @($histC.detail.list) }
    elseif ($histC.detail.records) { $hlist = @($histC.detail.records) }
    $seen = $hlist | Where-Object { $_.content -like "*group hello from owner A*" }
    # If C never joined successfully, seeing messages is a leak
    if ($seen -and -not $invC.ok) {
      Add-Bug "非群成员 C 能读到群消息" "P0" "GET /group-chat/{id}/messages as non-member" $seen
    }
  }

  $readB = Invoke-Api -Name "group-read-B" -Method PUT -Path "/group-chat/$groupId/read" -Headers $hB
  $leaveB = Invoke-Api -Name "group-leave-B" -Method POST -Path "/group-chat/$groupId/leave" -Headers $hB
  if ($leaveB.ok) {
    $sendB2 = Invoke-Api -Name "group-msg-B-after-leave" -Method POST -Path "/group-chat/messages" -Headers $hB -Body @{
      groupId = $groupId
      messageType = 0
      content = "should fail after leave $suffix"
      replyMessageId = $null
    } -OkWhen { param($j,$r) return ($j.code -ne 0) }
    if (-not $sendB2.ok) {
      Add-Bug "退群后仍可发言" "P0" "leave then POST messages" $sendB2
    }
  }

  # owner leave should be rejected
  $leaveA = Invoke-Api -Name "group-leave-owner-A" -Method POST -Path "/group-chat/$groupId/leave" -Headers $hA `
    -OkWhen { param($j,$r) return ($j.code -ne 0) }
  if (-not $leaveA.ok) {
    Add-Bug "群主可直接退群（应要求解散）" "P1" "owner POST leave" $leaveA
  }
}

# ---------- 4) Drift bottle cross-user ----------
$mood = -join ([char[]]@(0x968F, 0x4FBF, 0x8BF4, 0x8BF4))
$bottle = Invoke-Api -Name "drift-create-A" -Method POST -Path "/drift-bottle/create" -Headers $hA -Body @{
  content = "multiuser drift bottle content padding for length $suffix ok"
  moodType = $mood
}
$bottleId = $null
if ($bottle.ok) {
  if ($bottle.detail.bottleId) { $bottleId = $bottle.detail.bottleId }
  elseif ($bottle.detail.id) { $bottleId = $bottle.detail.id }
}
$pickB = Invoke-Api -Name "drift-pick-B" -Path "/drift-bottle/pick" -Headers $hB
$pickedId = $bottleId
if ($pickB.ok -and $pickB.detail) {
  if ($pickB.detail.id) { $pickedId = $pickB.detail.id }
  elseif ($pickB.detail.bottleId) { $pickedId = $pickB.detail.bottleId }
}
if ($pickedId) {
  $cmtB = Invoke-Api -Name "drift-comment-B" -Method POST -Path "/drift-bottle/$pickedId/comment" -Headers $hB -Body @{
    content = "comment from B $suffix"
  }
  $cmtA = Invoke-Api -Name "drift-comment-A-again" -Method POST -Path "/drift-bottle/$pickedId/comment" -Headers $hA -Body @{
    content = "owner comment after B $suffix"
  }
  # alternating rule: A then A again without B might fail — try double B
  $cmtB2 = Invoke-Api -Name "drift-comment-B-double" -Method POST -Path "/drift-bottle/$pickedId/comment" -Headers $hB -Body @{
    content = "B second comment maybe blocked $suffix"
  } -OkWhen { param($j,$r) return ($r.StatusCode -lt 500) }
}

# ---------- 5) Article reply cross-user ----------
$articleId = 14
$replyA = Invoke-Api -Name "article-reply-A" -Method PUT -Path "/articleReply/replyArticle" -Headers $hA -Body @{
  articleId = $articleId
  content = "multi reply from A $suffix"
  mediaList = @()
}
$replyId = $null
if ($replyA.ok) {
  if ($replyA.detail.replyId) { $replyId = $replyA.detail.replyId }
  elseif ($replyA.detail.id) { $replyId = $replyA.detail.id }
}
$replyB = Invoke-Api -Name "article-reply-B" -Method PUT -Path "/articleReply/replyArticle" -Headers $hB -Body @{
  articleId = $articleId
  content = "multi reply from B $suffix"
  mediaList = @()
}
if ($replyId) {
  $sub = Invoke-Api -Name "article-subreply-B" -Method PUT -Path "/articleSubReply/subReply" -Headers $hB -Body @{
    articleId = $articleId
    replyId = $replyId
    replyUserId = $A.userId
    content = "subreply from B to A $suffix"
    mediaList = @()
  }
  $list = Invoke-Api -Name "article-replies" -Path "/articleReply/getArticleReplyByArticleIdWithPage?articleId=$articleId&pageNum=1&pageSize=20" -Headers $hC
}

# ---------- 6) Private voice start/accept/decline (signaling state) ----------
$vs = Invoke-Api -Name "voice-start-A-B" -Method POST -Path "/message/private-voice/$($B.userId)/start" -Headers $hA
$statusB = Invoke-Api -Name "voice-status-B" -Path "/message/private-voice/$($A.userId)" -Headers $hB
if ($vs.ok) {
  $acc = Invoke-Api -Name "voice-accept-B" -Method POST -Path "/message/private-voice/$($A.userId)/accept" -Headers $hB
  $leave = Invoke-Api -Name "voice-leave-A" -Method POST -Path "/message/private-voice/$($B.userId)/leave" -Headers $hA
} else {
  # decline path with a fresh attempt if start failed due to busy
  $null = Invoke-Api -Name "voice-start2-A-C" -Method POST -Path "/message/private-voice/$($C.userId)/start" -Headers $hA `
    -OkWhen { param($j,$r) return ($r.StatusCode -lt 500) }
  $null = Invoke-Api -Name "voice-decline-C" -Method POST -Path "/message/private-voice/$($A.userId)/decline" -Headers $hC `
    -OkWhen { param($j,$r) return ($r.StatusCode -lt 500) }
}

# self voice
$selfVoice = Invoke-Api -Name "voice-self" -Method POST -Path "/message/private-voice/$($A.userId)/start" -Headers $hA `
  -OkWhen { param($j,$r) return ($j.code -ne 0) }
if (-not $selfVoice.ok) {
  Add-Bug "允许对自己发起语音通话" "P1" "POST private-voice/{self}/start" $selfVoice
}

# ---------- summary ----------
$passN = @($Results | Where-Object { $_.ok }).Count
$failN = @($Results | Where-Object { -not $_.ok }).Count
$summary = [ordered]@{
  startedAt = (Get-Date).ToString("o")
  aiHub = $aiOk
  accounts = @($A.userName, $B.userId, $C.userId)
  usernames = @($accounts.user)
  groupId = $groupId
  total = $Results.Count
  pass = $passN
  fail = $failN
  bugCount = $Bugs.Count
  bugs = $Bugs
  failures = @($Results | Where-Object { -not $_.ok } | Select-Object name, method, path, http, code, message)
  results = $Results
}
$summary | ConvertTo-Json -Depth 10 | Set-Content $Report -Encoding UTF8
Write-Host "=== DONE pass=$passN fail=$failN bugs=$($Bugs.Count) report=$Report ==="
foreach ($b in $Bugs) { Write-Host ("BUG/{0}: {1}" -f $b.severity, $b.title) }
if ($Bugs.Count -gt 0) { exit 3 } elseif ($failN -gt 0) { exit 2 } else { exit 0 }
