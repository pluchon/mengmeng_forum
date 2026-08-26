#!/bin/bash
# 校验 SPA 静态资源是否完整（避免 index.html 与 assets/*.js 来自不同次构建导致白屏）
set -euo pipefail
ROOT="${1:-.}"
USER_ROOT="${ROOT}/dist/user"
fail=0

check_spa() {
  local name="$1"
  local base="$2"
  local index="${base}/index.html"
  if [[ ! -f "$index" ]]; then
    echo "[FAIL] ${name}: missing ${index}"
    fail=1
    return
  fi
  echo "[OK] ${name}: ${index}"
  mapfile -t refs < <(grep -oE '/assets/[^"'\'' ]+\.(js|css)' "$index" | sort -u)
  if [[ ${#refs[@]} -eq 0 ]]; then
    echo "[FAIL] ${name}: index.html has no /assets/*.js references"
    fail=1
    return
  fi
  local missing=0
  for ref in "${refs[@]}"; do
    local path="${base}${ref}"
    if [[ ! -f "$path" ]]; then
      echo "[FAIL] ${name}: missing file ${path}"
      missing=1
    fi
  done
  if [[ "$missing" -eq 0 ]]; then
    echo "[OK] ${name}: all ${#refs[@]} asset(s) from index.html exist"
  else
    fail=1
  fi
}

check_spa "user" "$USER_ROOT"

if [[ "$fail" -ne 0 ]]; then
  echo ""
  echo "Fix: re-upload the FULL dist/user folder from the same package build."
  echo "On server, remove stale assets first: rm -rf ~/package/dist/user/assets/*"
  exit 1
fi

echo ""
echo "Frontend dist verification passed."
