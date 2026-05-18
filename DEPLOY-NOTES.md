# 钀岃悓璁哄潧 鈥?閮ㄧ讲涓庡妗堟敞鎰忎簨椤?
鏈枃妗ｆ眹鎬荤敓浜х幆澧冿紙鑵捐浜戞湇鍔″櫒 + 闃块噷浜戝煙鍚嶏級閮ㄧ讲銆佽繍缁翠笌 ICP 澶囨鐩稿叧瑕佺偣銆傝缁嗘墦鍖呮楠よ `nginx/DEPLOY-SERVER.md`銆?
---

## 涓€銆佹灦鏋勯€熻

| 缁勪欢 | 璇存槑 |
|------|------|
| 鍩熷悕 | 闃块噷浜戞敞鍐?/ DNS 瑙ｆ瀽 |
| 鏈嶅姟鍣?| 鑵捐浜?CVM锛堝缓璁?2 鏍?4G 鍙婁互涓婏級 |
| 杩愯鏃?| Docker Compose锛坄nginx/package` 鐩綍閮ㄧ讲锛?|
| 鍏ュ彛 | Nginx 80/443 鈫?`forum-backend-1` + 闈欐€佸墠绔?|
| 鏁版嵁 | MySQL锛堜笟鍔″簱锛夈€丷edis銆丷abbitMQ銆丳ostgreSQL锛圓I锛?|

---

## 浜屻€佹湰鏈烘墦鍖呬笌涓婁紶

```powershell
cd <项目根目录>\nginx
# 纭繚 Docker Desktop 宸插惎鍔紱棣栨鍙鍒?.env.example 涓?.env 骞舵敼瀵嗛挜
.\scripts\build-all.ps1
.\scripts\export-images.ps1
```

灏?**`nginx/package/` 鏁翠釜鐩綍** 涓婁紶鍒版湇鍔″櫒锛堝 `~/package`锛夈€?
**鏀硅繃鍚庣 Java 鎴?`application-prod.yml` 鍚庯紝蹇呴』閲嶆柊 `build-all.ps1` 骞?`export-images.ps1`锛屽啀涓婁紶鏂伴暅鍍忋€?*

---

## 涓夈€佹湇鍔″櫒棣栨鍚姩

```bash
cd ~/package
cp .env.example .env
nano .env   # 蹇呮敼椤硅涓嬫枃

docker load -i images/forum-backend.tar
docker load -i images/forum-ai-server.tar
docker load -i images/infra.tar

mkdir -p logs/backend

docker compose -f docker-compose.yaml -f docker-compose.prod.yml down --remove-orphans
docker compose -f docker-compose.yaml -f docker-compose.prod.yml up -d
```

### `.env` 蹇呮敼椤癸紙涓嶅彲鐢ㄧず渚嬪崰浣嶇涓婄嚎锛?
| 鍙橀噺 | 璇存槑 |
|------|------|
| `PII_CRYPTO_SECRET` | 鈮?2 瀛楃闅忔満涓诧紱鏈缃細瀵艰嚧 `forum-backend-1` 鍙嶅宕╂簝銆丆PU 椋欓珮 |
| `JWT_SECRET` | 鈮?2 瀛楃闅忔満涓诧紙HS256锛?|
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | 鏁版嵁搴撳瘑鐮?|
| `REDIS_PASSWORD` / `RABBITMQ_PASSWORD` / `POSTGRES_PASSWORD` | 涓棿浠跺瘑鐮?|
| `DASHSCOPE_API_KEY` 绛?| AI 鏈嶅姟瀵嗛挜锛堜笌 `ai-server/config.yaml` 瀵归綈锛?|

鐢熸垚闅忔満涓诧細`openssl rand -base64 32`

### 鍒濆鍖栨暟鎹簱锛堜粎棣栨锛?
```bash
docker exec -i forum-mysql mysql -uroot -p浣犵殑ROOT瀵嗙爜 forum_db < create.sql
```

`create.sql` 璺緞锛歚forum-demo/src/main/resources/sql/create.sql`

鍙€夛紙AI 浼氳瘽琛紝Postgres锛夛細

```bash
# 鍦?langgraph_db 涓墽琛?forum-demo/src/main/resources/sql/postgres_ai_session.sql
```

---

## 鍥涖€丏ocker Compose 閲嶈璇存槑

### 4.1 鐢熶骇鍚姩鍛戒护

```bash
docker compose -f docker-compose.yaml -f docker-compose.prod.yml up -d
```

### 4.2 `docker-compose.prod.yml` 涓庣鍙?`!reset`

鍩虹鏂囦欢 `docker-compose.yaml` 宸蹭负 MySQL/Redis 绛夐厤缃簡 `ports`銆傚彔鍔?`prod` 鏃惰嫢鍐嶅啓涓€灞?`ports` 涓?*鏈?*浣跨敤 `!reset`锛孋ompose 浼?*鍚堝苟**涓ゆ閰嶇疆锛屽鑷村悓涓€瀹夸富鏈虹鍙ｇ粦瀹氫袱娆★紝鎶ラ敊锛?
```text
failed to bind host port 127.0.0.1:54320/tcp: address already in use
```

涓?`ss` / `lsof` 鍙兘鏌ヤ笉鍒板崰鐢紙瀹瑰櫒鍚姩澶辫触鍚庣鍙ｅ凡閲婃斁锛夈€?
**姝ｇ‘鍐欐硶锛堟瘡涓腑闂翠欢鍙湁涓€涓?`ports` 閿級锛?*

```yaml
mysql:
  ports: !reset
    - "127.0.0.1:${MYSQL_HOST_PORT:-33061}:3306"
```

瀵?`redis`銆乣rabbitmq`銆乣postgres` 鍚岀悊銆備慨鏀瑰悗鐢ㄤ笅闈㈠懡浠ょ‘璁?*姣忎釜鏈嶅姟鍙湁涓€鏉?* `published` 绔彛锛?
```bash
docker compose -f docker-compose.yaml -f docker-compose.prod.yml config | sed -n '/^  mysql:/,/^  [a-z]/p'
```

### 4.3 涓嶆槧灏勫涓绘満绔彛鏃讹紙Navicat / SSH锛?
鑻?`prod` 涓?`!reset` 鍚庢湭鍔犲洖 `ports`锛孧ySQL 绛変粎鍦?Docker 鍐呯綉鍙闂€傞€氳繃 **SSH + 瀹瑰櫒 IP** 杩炴帴锛堟棤闇€鍏綉寮€鏀炬暟鎹簱绔彛锛夛細

```bash
docker inspect -f '{{.Name}} {{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \
  forum-mysql forum-redis forum-postgres
```

| 鏈嶅姟 | 瀹瑰櫒鍐呯鍙?| Navicat銆屽父瑙勩€嶄富鏈猴紙SSH 钀藉湴鍚庯級 |
|------|------------|-----------------------------------|
| MySQL | 3306 | 瀹瑰櫒 IP锛屽 `172.18.0.2` |
| Redis | 6379 | 瀹瑰櫒 IP + 瀵嗙爜 `REDIS_PASSWORD` |
| PostgreSQL | 5432 | 瀹瑰櫒 IP锛岀敤鎴?`langgraph`锛屽簱 `langgraph_db` |

**SSH 閫夐」鍗?*锛氭湇鍔″櫒鍏綉 IP銆?2銆佺敤鎴枫€乣ubuntu`銆佺閽ャ€? 
**涓嶈鐢ㄥ叕缃?IP 濉?MySQL/Redis 鐨勩€屽父瑙勩€嶄富鏈?*锛堥櫎闈炲鍏綉寮€鏀句簡绔彛锛岀敓浜т笉鎺ㄨ崘锛夈€?
---

## 浜斻€侀獙璇佹湇鍔℃槸鍚︽甯?
```bash
docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
# 8 涓鍣ㄥ簲涓?Up锛涗腑闂翠欢 healthy

docker logs forum-backend-1 --tail 30
# 鏈熸湜锛歋tarted ForumDemoApplication

docker logs forum-ai-server --tail 20
# 鏈熸湜锛氬凡杩炴帴 RabbitMQ锛岃闃?q-audit-article

curl -s http://127.0.0.1/healthz
# ok

curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1/
# 棣栭〉闈欐€?200
```

鍚庣鍚姩鎴愬姛鏍囧織绀轰緥锛?
```text
绛惧埌瑙勫垯缂撳瓨鍔犺浇瀹屾垚: 12 涓湀浠借鍒? 3 涓繛绛惧鍔辨。
Started ForumDemoApplication in xx seconds
```

`鏈鍙栧埌鐜鍙橀噺: DB_PASSWORD / ALIYUN_* / MAIL_PASSWORD` 涓?WARN锛欴ocker 浣跨敤 `SPRING_DATASOURCE_*` 绛夋敞鍏ワ紱闇€鐭俊/閭欢/OSS 鏃跺啀琛ュ搴斿彉閲忋€?
---

## 鍏€両CP 澶囨锛堝煙鍚嶉樋閲屼簯 + 鏈嶅姟鍣ㄨ吘璁簯锛?
### 6.1 鍒嗗伐

| 骞冲彴 | 鍋氫粈涔?| 涓嶅仛浠€涔?|
|------|--------|----------|
| **鑵捐浜?* | **鎺ュ叆澶囨**锛堜富浣?+ 缃戠珯锛夛紱鏈嶅姟鍣ㄥ湪姝?| 涓嶅繀鍦ㄨ吘璁簯鍐嶄拱鍩熷悕 |
| **闃块噷浜?* | 鍩熷悕瀹炲悕銆佸妗堥獙璇?DNS锛圱XT 绛夛級銆?*澶囨閫氳繃鍚?A 璁板綍瑙ｆ瀽** | **涓嶈**鍐嶅湪闃块噷浜戦噸澶嶆彁浜ゅ悓涓€缃戠珯澶囨 |

### 6.2 澶囨娴佺▼瑕佺偣锛堣吘璁簯鎺у埗鍙帮級

1. 鎻愪氦鍒濆  
2. **鑵捐浜戝鏍?* 鈥?娉ㄦ剰鎺ュ惉瀹℃牳鐢佃瘽锛堝 `010-5610-3419` 绛夛級锛屼袱娆℃湭鎺ュ彲鑳借椹冲洖  
3. 寰呮彁浜ょ灞€  
4. **宸ヤ俊閮ㄧ煭淇℃牳楠?* 鈥?鏀跺埌鐭俊鍚?24 灏忔椂鍐呭畬鎴? 
5. **绠″眬瀹℃牳** 鈥?绾?1锝?0 涓伐浣滄棩  

### 6.3 澶囨閫氳繃鍚庯紙闃块噷浜?DNS锛?
| 涓绘満璁板綍 | 绫诲瀷 | 璁板綍鍊?|
|----------|------|--------|
| `www` | A | 鑵捐浜?CVM 鍏綉 IP |
| `admin` | A | 鍚屼笂 |
| `@` | A | 鍚屼笂锛堝彲閫夛級 |

### 6.4 澶囨鍓嶈闂幇璞?
鍥藉唴娴忚鍣ㄨ闂?`https://www.nuonuoya.cn` 鍙兘鍑虹幇鑵捐浜?**`webblock.html` 澶囨鎻愮ず椤?*锛屾帶鍒跺彴閲?`beacon.qq.com` 绛?`ERR_BLOCKED_BY_CLIENT` 澶氫负鎷︽埅椤佃剼鏈骞垮憡鎻掍欢鎷︽埅锛?*涓庤鍧涗唬鐮佹棤鍏?*銆?
澶囨鍓嶅彲鍦ㄦ湰鏈虹敤 SSH 闅ч亾楠岃瘉绔欑偣锛?
```bash
ssh -i 绉侀挜.pem -L 8443:127.0.0.1:443 ubuntu@鍏綉IP
# 娴忚鍣ㄨ闂?https://127.0.0.1:8443锛堣瘉涔﹀彲鑳藉憡璀︼級
```

鏈嶅姟鍣ㄤ笂 `curl -I https://www.nuonuoya.cn` 杩斿洖 200 浠嶅彲鑳戒笌鍥藉唴娴忚鍣ㄦ嫤鎴瓥鐣ヤ笉涓€鑷达紝浠ュ妗堢姸鎬佷负鍑嗐€?
---

## 涓冦€佸父瑙侀棶棰?
| 鐜拌薄 | 鍘熷洜 | 澶勭悊 |
|------|------|------|
| `forum-backend-1` CPU 寰堥珮銆佸弽澶嶉噸鍚?| 缂?`PII_CRYPTO_SECRET` / `JWT_SECRET` | 鍦?`.env` 閰嶇疆鍚?`up -d` |
| `UnknownHostException: mysql` | 鍗婂 `up`銆佺綉缁滄湭灏辩华 | `down --remove-orphans` 鍚庡畬鏁?`up -d` 8/8 |
| `address already in use` 54320/33061 | prod 涓?base 绔彛閲嶅鍚堝苟 | `ports: !reset` 鍚庡彧淇濈暀涓€鏉＄粦瀹?|
| `no queue q-audit-article` | 鍚庣鏈惎鍔ㄣ€侀槦鍒楁湭澹版槑 | 绛?backend `Started` 鍚?AI 浼氳嚜鍔ㄩ噸杩?|
| Navicat 杩炰笉涓?MySQL | 鏃犲涓绘満鏄犲皠鏃剁敤 **瀹瑰櫒 IP** + SSH | 瑙佺鍥涜妭 |
| 娴忚鍣ㄥ妗堥〉 | 澶ч檰鏈烘埧鏈妗堝煙鍚?| 鑵捐浜戝畬鎴愬妗?+ 闃块噷浜戣В鏋?|

---

## 鍏€佸父鐢ㄨ繍缁村懡浠?
```bash
cd ~/package

docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
docker compose logs -f forum-backend-1
docker compose logs -f forum-ai-server
docker compose restart backend-1

# 浠呮洿鏂板墠绔細涓婁紶 dist/ 鍚?docker compose exec nginx nginx -s reload
```

---

## 涔濄€佸畨鍏ㄦ彁閱?
- 瀵嗛挜閰嶇疆瑙?**`WINDOWS-ENV.md`**锛圵indows 鐜鍙橀噺 / `scripts/dev-secrets.ps1` / `nginx/.env`锛? 
- 鍕垮皢 `nginx/.env`銆乣scripts/dev-secrets.ps1`銆佺湡瀹?API Key 鎻愪氦鍒?Git  
- 鐢熶骇鐜鍔″繀鏇挎崲 `CHANGE_ME` 鍙婇粯璁ゅ瘑鐮? 
- 鏁版嵁搴撶鍙ｄ粎缁戝畾 `127.0.0.1` 鎴栦粎瀹瑰櫒鍐呯綉锛屽嬁瀵?`0.0.0.0` 寮€鏀?MySQL/Redis  

---

*鏂囨。闅忛」鐩儴缃叉柟寮忔洿鏂帮紱鑴氭湰涓庨厤缃互 `nginx/DEPLOY-SERVER.md`銆乣nginx/docker-compose*.yaml` 涓哄噯銆?

