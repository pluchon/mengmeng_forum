#!/usr/bin/env bash
# 用法: cd deploy && bash scripts/build-all.sh [--skip-docker] [--skip-tests]
set -euo pipefail

DEPLOY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$DEPLOY_ROOT/.." && pwd)"
SKIP_DOCKER=false
SKIP_TESTS=false
for arg in "$@"; do
  case "$arg" in
    --skip-docker) SKIP_DOCKER=true ;;
    --skip-tests) SKIP_TESTS=true ;;
    *) echo "ERROR: unsupported argument: $arg"; exit 2 ;;
  esac
done

step() { echo -e "\n==> $*"; }

assert_deploy_child() {
  local target="$1"
  case "$target" in
    "$DEPLOY_ROOT"/*) ;;
    *) echo "ERROR: refusing to modify path outside deploy root: $target"; exit 1 ;;
  esac
}

sync_dist() {
  local src="$1" dst="$2"
  [[ -d "$src" ]] || { echo "未找到: $src"; exit 1; }
  assert_deploy_child "$dst"
  mkdir -p "$dst"
  find "$dst" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  cp -a "$src"/. "$dst"/
}

step "构建用户端"
cd "$REPO_ROOT/forum-vue/front"
[[ -d node_modules ]] || npm ci
npm run build
sync_dist dist "$DEPLOY_ROOT/dist/user"

CLOUD_ROOT="$REPO_ROOT/java-cloud-standalone"
[[ -f "$CLOUD_ROOT/pom.xml" ]] || { echo "ERROR: missing $CLOUD_ROOT/pom.xml"; exit 1; }

step "Maven 打包 java-cloud-standalone"
cd "$CLOUD_ROOT"
MAVEN_ARGS=(-q -B package)
[[ "$SKIP_TESTS" == true ]] && MAVEN_ARGS+=(-DskipTests)
mvn "${MAVEN_ARGS[@]}"

if [[ "$SKIP_DOCKER" == false ]]; then
  [[ -f "$CLOUD_ROOT/Dockerfile.backend" ]] || { echo "ERROR: missing $CLOUD_ROOT/Dockerfile.backend"; exit 1; }
  step "Docker 构建 forum-backend"
  docker build --pull=false -t forum-backend:latest "$CLOUD_ROOT" -f "$CLOUD_ROOT/Dockerfile.backend"
  step "Docker 构建 forum-ffmpeg"
  docker build --pull=false -t forum-ffmpeg:latest "$DEPLOY_ROOT/ffmpeg"
  step "Docker 构建 forum-ai-server"
  docker build --pull=false -t forum-ai-server:latest "$REPO_ROOT/ai-server"
fi

echo -e "\n完成。本地: 在 PowerShell 执行 deploy/scripts/dev-compose.ps1 up -d"
echo -e "服务器: 运行 make-package.ps1，只上传 C:/forum-build/luntan-package"
