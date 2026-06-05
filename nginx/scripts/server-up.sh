#!/bin/bash
# 上传 package 后在服务器执行：bash up.sh
# 作用：修权限、校验 dist、加载离线镜像、重建容器（比裸 compose up --build 完整）
set -euo pipefail
cd "$(dirname "$0")"
COMPOSE="docker compose -f docker-compose.yaml -f docker-compose.prod.yml"

for f in .env up.sh start.sh verify-frontend-dist.sh; do
  [[ -f "$f" ]] && sed -i 's/\r$//' "$f" 2>/dev/null || true
done

chmod -R a+rX dist conf.d ssl 2>/dev/null || true
mkdir -p logs/backend
chmod -R a+rX logs 2>/dev/null || true

if [[ -f ./verify-frontend-dist.sh ]]; then
  chmod +x ./verify-frontend-dist.sh
  ./verify-frontend-dist.sh .
fi

for tar in images/forum-backend.tar images/forum-ai-server.tar images/infra.tar; do
  if [[ ! -f "$tar" ]]; then
    echo "ERROR: missing $tar — re-upload full package/"
    exit 1
  fi
done

echo "==> docker load (offline images)"
docker load -i images/forum-backend.tar
docker load -i images/forum-ai-server.tar
docker load -i images/infra.tar

for img in forum-backend:latest forum-ai-server:latest forum-ffmpeg:latest nginx:1.30.1; do
  docker image inspect "$img" >/dev/null 2>&1 || {
    echo "ERROR: image missing after load: $img"
    exit 1
  }
done

echo "==> compose up --force-recreate"
$COMPOSE up -d --force-recreate

sleep 3
if curl -sf http://127.0.0.1/healthz >/dev/null; then
  echo "healthz OK"
else
  echo "WARN: healthz failed — check: docker logs forum-nginx --tail 30"
fi

IDX="dist/user/index.html"
if [[ -f "$IDX" ]]; then
  ASSET="$(grep -oE '/assets/[^"'\'' ]+\.js' "$IDX" | head -1 || true)"
  if [[ -n "$ASSET" ]]; then
    CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1${ASSET}" || echo 000)"
    echo "probe ${ASSET} -> HTTP ${CODE} (expect 200)"
  fi
fi

echo "Done. If browser still 403: docker logs forum-nginx --tail 50"
