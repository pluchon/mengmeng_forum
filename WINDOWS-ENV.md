# Windows 寮€鍙戠幆澧?鈥?瀵嗛挜涓庣幆澧冨彉閲忛厤缃?
浠撳簱鍐?*涓嶅啀淇濆瓨**鐪熷疄 API Key / 涓氬姟瀵嗛挜銆傝鍦ㄦ湰鏈虹敤涓嬮潰浠讳竴鏂瑰紡閰嶇疆銆?
---

## 涓€銆佷袱绉嶆柟寮忔€庝箞閫?
| 鍦烘櫙 | 鎺ㄨ崘鏂瑰紡 | 璇存槑 |
|------|----------|------|
| **Docker 閮ㄧ讲 / `docker compose up`** | `nginx/.env` 鏂囦欢 | Compose 鑷姩璇诲彇锛?*涓嶅繀**璁?Windows 绯荤粺鐜鍙橀噺 |
| **鏈満 IDE 璺?Java**锛坄forum-demo`锛?| Windows 鐢ㄦ埛鐜鍙橀噺 鎴?`dev-secrets.ps1` | Spring 璇诲彇 `PII_CRYPTO_SECRET`銆乣JWT_SECRET`銆乣ALIYUN_*` 绛?|
| **鏈満璺?AI**锛坄python main.py`锛?| 鍚屼笂 | `ai-server/config.py` 鐢ㄧ幆澧冨彉閲忚鐩?`config.yaml` 涓殑绌哄瘑閽?|
| **鎵撳寘涓婁紶鏈嶅姟鍣?* | 鏈嶅姟鍣ㄤ笂 `~/package/.env` | 涓?`nginx/.env.example` 鍚岀粨鏋勶紝鍕挎彁浜?Git |

---

## 浜屻€佹柟寮?A锛歐indows 鐢ㄦ埛鐜鍙橀噺锛堟寔涔咃紝鎺ㄨ崘 IDE 寮€鍙戯級

### 鍥惧舰鐣岄潰

1. `Win + R` 鈫?杈撳叆 `sysdm.cpl` 鈫?鍥炶溅  
2. **楂樼骇** 鈫?**鐜鍙橀噺**  
3. 鍦?**鐢ㄦ埛鍙橀噺**锛堜粎褰撳墠鐢ㄦ埛锛変腑 **鏂板缓**锛屼緥濡傦細

| 鍙橀噺鍚?| 鐢ㄩ€?|
|--------|------|
| `PII_CRYPTO_SECRET` | 鎵嬫満鍙?閭鍔犲瘑锛堚墺32 瀛楃锛?|
| `JWT_SECRET` | JWT 绛惧悕锛堚墺32 瀛楃锛?|
| `ALIYUN_ACCESS_KEY_ID` | 鐭俊 / OSS |
| `ALIYUN_ACCESS_KEY_SECRET` | 鐭俊 / OSS |
| `MAIL_PASSWORD` | 閭欢鎺堟潈鐮侊紙鑻ュ惎鐢級 |
| `DASHSCOPE_API_KEY` | 閫氫箟 / 瀹℃牳 / 鐢熷浘 |
| `DEEPSEEK_API_KEY` | DeepSeek 鍐欎綔 |
| `HUANAPI_IMAGE_KEY` | HuanAPI 鐢熷浘 |
| `HUANAPI_GEMINI_KEY` | HuanAPI Gemini |

4. 纭畾鍚?**鍏抽棴骞堕噸鏂版墦寮€** PowerShell銆両DE銆佺粓绔紝鍙橀噺鎵嶄細鐢熸晥銆?
### PowerShell锛堝綋鍓嶇敤鎴凤紝姘镐箙锛?
```powershell
[Environment]::SetEnvironmentVariable("PII_CRYPTO_SECRET", "浣犵殑闅忔満涓?, "User")
[Environment]::SetEnvironmentVariable("JWT_SECRET", "浣犵殑闅忔満涓?, "User")
[Environment]::SetEnvironmentVariable("DASHSCOPE_API_KEY", "sk-...", "User")
# ... 鍏朵綑鍚岀悊
```

鐢熸垚闅忔満涓诧細

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

### 楠岃瘉鏄惁鐢熸晥

**鏂板紑** PowerShell锛?
```powershell
$env:PII_CRYPTO_SECRET
$env:DASHSCOPE_API_KEY
```

---

## 涓夈€佹柟寮?B锛歚scripts/dev-secrets.ps1`锛堜粎褰撳墠缁堢锛岀伒娲伙級

閫傚悎涓嶆兂鍐欒繘绯荤粺鐜鍙橀噺銆佹垨瀵嗛挜缁忓父鎹㈢殑鎯呭喌銆?
```powershell
cd <项目根目录>
copy scripts\dev-secrets.ps1.example scripts\dev-secrets.ps1
notepad scripts\dev-secrets.ps1   # 濉叆鐪熷疄鍊?```

姣忔寮€鍙戝墠锛屽湪**鍚屼竴 PowerShell 绐楀彛**鎵ц锛?
```powershell
. .\scripts\load-dev-env.ps1
```

鐒跺悗鍦?*璇ョ獥鍙?*鍚姩 IDE 鎴栵細

```powershell
cd ai-server
python main.py
```

`load-dev-env.ps1` 浼氬姞杞?`dev-secrets.ps1`锛堣嫢瀛樺湪锛夈€? 
`dev-secrets.ps1` 宸插姞鍏?`.gitignore`锛?*鍕挎彁浜?*銆?
---

## 鍥涖€丏ocker 閮ㄧ讲锛氫娇鐢?`nginx/.env`

```powershell
cd <项目根目录>\nginx
copy .env.example .env
notepad .env
```

濉啓鎵€鏈夊繀濉」鍚庯細

```powershell
docker compose -f docker-compose.yaml -f docker-compose.prod.yml up -d
```

`build-all.ps1` 涔熶細鑷姩浠?`.env.example` 琛ュ叏缂哄け閿埌 `.env`锛屼絾**涓嶄細**鍐欏叆鐪熷疄 API Key锛岄渶浣犳墜鍔ㄥ～鍐欍€?
涓婁紶鍒版湇鍔″櫒鏃朵娇鐢?**`package/.env`**锛堢敱 `.env.example` 澶嶅埗骞朵慨鏀癸級锛屽悓鏍蜂笉瑕佹彁浜?Git銆?
---

## 浜斻€佸悇妯″潡璇诲彇鐨勭幆澧冨彉閲?
### Java锛坄forum-demo`锛?
| 鍙橀噺 | 閰嶇疆鏂囦欢 |
|------|----------|
| `PII_CRYPTO_SECRET` | `application.yml` / `application-prod.yml` 鈫?`pii.secret` |
| `JWT_SECRET` | `application.yml` 鈫?`jwt.secret` |
| `ALIYUN_ACCESS_KEY_ID` / `ALIYUN_ACCESS_KEY_SECRET` | 鐭俊銆丱SS |
| `MAIL_PASSWORD` | 閭欢锛堣嫢閰嶇疆锛?|
| `DB_PASSWORD` | 鍙€夋鏌ラ」锛汥ocker 甯哥敤 `SPRING_DATASOURCE_PASSWORD` |

鏈湴榛樿 profile 鏈 `prod` 鏃讹紝`application.yml` 涓?JWT/PII 浠嶆湁**寮€鍙戠敤榛樿鍊?*锛涚敓浜?Docker 浣跨敤 `prod`锛屽繀椤诲湪 `.env` 涓厤缃€?
### AI锛坄ai-server`锛?
`config.yaml` 涓瘑閽ュ瓧娈电暀绌猴紝鐢?`config.py` 璇诲彇锛?
| 鍙橀噺 | 瑕嗙洊椤?|
|------|--------|
| `DASHSCOPE_API_KEY` | `dashscope.api_key` |
| `DEEPSEEK_API_KEY` | `deepseek.api_key` |
| `HUANAPI_IMAGE_KEY` | `huanapi.image_key` |
| `HUANAPI_GEMINI_KEY` | `huanapi.gemini_key` |
| `REDIS_PASSWORD` | `redis.password` |
| `RABBITMQ_PASSWORD` | `rabbitmq.password` |
| `POSTGRES_PASSWORD` | `postgres.password` |

### IntelliJ IDEA

**Run 鈫?Edit Configurations 鈫?Environment variables**锛?
```
PII_CRYPTO_SECRET=xxx;JWT_SECRET=xxx;DASHSCOPE_API_KEY=sk-...
```

鎴栧嬀閫?**Include system environment variables**锛堣嫢宸茶鐢ㄦ埛鐜鍙橀噺锛夈€?
### VS Code锛圝ava锛?
`launch.json`锛?
```json
"env": {
  "PII_CRYPTO_SECRET": "浠庣郴缁熺幆澧冭鍙栨垨鍦ㄦ濉啓",
  "JWT_SECRET": "..."
}
```

---

## 鍏€佸畨鍏ㄦ竻鍗?
- [ ] 鍕垮皢 `nginx/.env`銆乣scripts/dev-secrets.ps1` 鎻愪氦鍒?Git  
- [ ] 鍕垮湪 `ai-server/config.yaml` 涓啓鍥炵湡瀹?Key  
- [ ] 鑻?Key 鏇捐鎻愪氦锛岃鍦ㄥ悇浜戝钩鍙?*杞崲瀵嗛挜**  
- [ ] 鐢熶骇鏈嶅姟鍣?`.env` 浣跨敤寮哄瘑鐮佷笌闅忔満 `PII_CRYPTO_SECRET` / `JWT_SECRET`  

---

## 涓冦€佸父瑙侀棶棰?
**Q锛歚docker compose` 璇讳笉鍒版垜璁剧殑 Windows 鐜鍙橀噺锛?*  
A锛欳ompose 榛樿鍙椤圭洰鐩綍涓嬬殑 `.env` 鏂囦欢锛屼笉璇?Windows 鐢ㄦ埛鍙橀噺銆傝鐢?`nginx/.env`銆?
**Q锛氳浜嗗彉閲忎絾 Java 浠嶆姤 `pii.secret` 鏈厤缃紵**  
A锛欼DE 鍚姩鍓嶆湭鍔犺浇鍙橀噺銆傞噸鍚?IDE锛屾垨鍦?Run Configuration 閲屾樉寮忓～鍐欍€?
**Q锛欰I 鎶?`DASHSCOPE_API_KEY 鏈厤缃甡锛?*  
A锛氬厛鎵ц `. .\scripts\load-dev-env.ps1` 鎴栬缃敤鎴风幆澧冨彉閲忥紝鍐?`python main.py`銆?
---

*涓?`DEPLOY-NOTES.md`锛堟湇鍔″櫒閮ㄧ讲锛夈€乣nginx/DEPLOY-SERVER.md`锛堟墦鍖呮祦绋嬶級閰嶅悎浣跨敤銆?

