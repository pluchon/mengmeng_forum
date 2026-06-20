#!/usr/bin/env bash
# 用法: cd nginx && bash scripts/build-all.sh [--skip-docker]
set -euo pipefail

NGINX_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$NGINX_ROOT/.." && pwd)"
SKIP_DOCKER=false
[[ "${1:-}" == "--skip-docker" ]] && SKIP_DOCKER=true

step() { echo -e "\n==> $*"; }

sync_dist() {
  local src="$1" dst="$2"
  [[ -d "$src" ]] || { echo "未找到: $src"; exit 1; }
  rm -rf "$dst"/*
  mkdir -p "$dst"
  cp -a "$src"/. "$dst"/
}

sync_live2d() {
  local src="$REPO_ROOT/live2d/live2d-master"
  local dst="$NGINX_ROOT/dist/user/live2d-assets"
  if [[ ! -d "$src" ]]; then
    echo "WARN: Live2D models not found at $src — skip live2d-assets sync"
    return
  fi
  rm -rf "$dst"
  mkdir -p "$dst"
  cp -a "$src"/. "$dst"/
  local n
  n=$(find "$dst" -type f | wc -l | tr -d ' ')
  echo "Synced live2d to nginx/dist/user/live2d-assets ($n files)"
}

step "构建用户端"
cd "$REPO_ROOT/forum-vue/front"
[[ -d node_modules ]] || npm ci
npm run build
sync_dist dist "$NGINX_ROOT/dist/user"
sync_live2d

step "构建管理端"
cd "$REPO_ROOT/forum-vue-admin/admin"
[[ -d node_modules ]] || npm ci
npm run build
sync_dist dist "$NGINX_ROOT/dist/admin"

if [[ "$SKIP_DOCKER" == true ]]; then
  step "Maven 打包后端"
  cd "$REPO_ROOT/backend"
  mvn -q -B package -DskipTests
else
  step "Docker 构建 forum-backend"
  docker build -t forum-backend:latest "$REPO_ROOT/backend"
  step "Docker 构建 ai-server"
  cd "$NGINX_ROOT"
  docker compose build ai-server
fi

echo -e "\n完成。本地: cd nginx && docker compose up -d"
echo -e "服务器: 运行 export-images 脚本，只上传 nginx/package/ 目录"
