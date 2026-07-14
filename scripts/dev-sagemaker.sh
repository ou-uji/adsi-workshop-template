#!/usr/bin/env bash
# SageMaker Code Editor プレビュー一括起動（.claude/skills/sagemaker-code-editor/SKILL.md §5）。
#
#   backend(8080, H2/workshop) + next(3001, 127.0.0.1) + 復元プロキシ(3000)
#
# 起動後: PORTS タブの 3000 の地球儀 → URL の "ports" を "absports" に置換して開く。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# --- SageMaker フラグ・フル basePath（短いパスのみは禁止） ---
export SAGEMAKER=1
export NEXT_PUBLIC_BASE_PATH="${NEXT_PUBLIC_BASE_PATH:-/codeeditor/default/absports/3000}"

# SageMaker 環境の Java（存在する場合のみ指定）
if [ -d /opt/mise/installs/java/corretto-21 ]; then
  export JAVA_HOME=/opt/mise/installs/java/corretto-21
fi

# --- 既存プロセス停止（3000/3001/8080） ---
echo "[dev:sagemaker] stopping existing processes on 3000/3001/8080 ..."
fuser -k 3000/tcp 3001/tcp 8080/tcp 2>/dev/null || true
sleep 1

# --- frontend 依存（ルート setup とは別に必要） ---
if [ ! -d packages/frontend/node_modules ]; then
  echo "[dev:sagemaker] installing workspace deps ..."
  npm install
fi

# --- frontend build（basePath 反映のため start 前に build） ---
echo "[dev:sagemaker] building frontend (SAGEMAKER=1, basePath=$NEXT_PUBLIC_BASE_PATH) ..."
npm --workspace @adsi/frontend run build

# --- backend 起動（workshop / H2） ---
echo "[dev:sagemaker] starting backend :8080 (workshop) ..."
( cd packages/backend && ./gradlew bootRun --args='--spring.profiles.active=workshop' ) &
BACKEND_PID=$!

# --- next start（127.0.0.1:3001, IPv6 バインド回避） ---
echo "[dev:sagemaker] starting next :3001 ..."
( npm --workspace @adsi/frontend run start ) &
NEXT_PID=$!

# --- 復元プロキシ :3000 ---
echo "[dev:sagemaker] starting restore-proxy :3000 ..."
node packages/frontend/scripts/sagemaker-proxy.mjs &
PROXY_PID=$!

echo ""
echo "[dev:sagemaker] backend=$BACKEND_PID next=$NEXT_PID proxy=$PROXY_PID"
echo "[dev:sagemaker] PORTS タブ 3000 の地球儀 → URL の 'ports' を 'absports' に置換して開く"
echo "[dev:sagemaker] 停止: npm run dev:sagemaker:stop"

# いずれかが落ちたら全体を停止
trap 'kill $BACKEND_PID $NEXT_PID $PROXY_PID 2>/dev/null || true' INT TERM
wait -n $BACKEND_PID $NEXT_PID $PROXY_PID
