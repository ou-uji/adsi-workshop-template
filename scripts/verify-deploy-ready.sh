#!/bin/bash
# デプロイ準備確認スクリプト
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=========================================="
echo "AWS デプロイ準備確認"
echo "=========================================="
echo ""

ERRORS=0

# 1. Backend JAR ビルド確認
echo "[1/5] Backend ビルド確認"
cd "$PROJECT_ROOT/packages/backend"
if ./gradlew build -x test > /dev/null 2>&1; then
  echo "✓ Backend build: OK"
else
  echo "✗ Backend build: FAILED"
  ERRORS=$((ERRORS + 1))
fi
echo ""

# 2. Frontend 静的ビルド確認
echo "[2/5] Frontend 静的ビルド確認"
cd "$PROJECT_ROOT/packages/frontend"
if DEPLOY=1 npm run build > /dev/null 2>&1; then
  if [ -d "out" ] && [ -f "out/index.html" ]; then
    echo "✓ Frontend static export: OK (out/ directory exists)"
  else
    echo "✗ Frontend static export: FAILED (out/ directory missing or incomplete)"
    ERRORS=$((ERRORS + 1))
  fi
else
  echo "✗ Frontend build: FAILED"
  ERRORS=$((ERRORS + 1))
fi
echo ""

# 3. CDK TypeScript コンパイル確認
echo "[3/5] CDK TypeScript コンパイル確認"
cd "$PROJECT_ROOT/packages/infra"
if npm run build > /dev/null 2>&1; then
  echo "✓ CDK TypeScript: OK"
else
  echo "✗ CDK TypeScript: FAILED"
  ERRORS=$((ERRORS + 1))
fi
echo ""

# 4. AWS 認証情報確認
echo "[4/5] AWS 認証情報確認"
if aws sts get-caller-identity > /dev/null 2>&1; then
  ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
  echo "✓ AWS Credentials: OK (Account: $ACCOUNT)"
else
  echo "✗ AWS Credentials: FAILED (not configured)"
  ERRORS=$((ERRORS + 1))
fi
echo ""

# 5. Docker 確認
echo "[5/5] Docker 確認"
if docker info > /dev/null 2>&1; then
  echo "✓ Docker: OK"
else
  echo "✗ Docker: FAILED (not available or not running)"
  ERRORS=$((ERRORS + 1))
fi
echo ""

echo "=========================================="
if [ $ERRORS -eq 0 ]; then
  echo "✅ すべてのチェックに合格しました"
  echo ""
  echo "デプロイを実行できます:"
  echo "  npm run deploy"
  echo ""
  exit 0
else
  echo "❌ $ERRORS 個のエラーがあります"
  echo ""
  echo "エラーを修正してから再度実行してください。"
  echo ""
  exit 1
fi
