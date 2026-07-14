#!/bin/bash
set -euo pipefail

# AWS デプロイスクリプト — Frontend ビルド + CDK deploy
# SageMaker 制約対応: CDK deploy role を assume して実行

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# AWS 設定
AWS_REGION="${AWS_REGION:-ap-northeast-1}"
AWS_ACCOUNT="${AWS_ACCOUNT:-442797924420}"
CDK_DEPLOY_ROLE="arn:aws:iam::${AWS_ACCOUNT}:role/cdk-hnb659fds-deploy-role-${AWS_ACCOUNT}-${AWS_REGION}"

echo "=========================================="
echo "AWS デプロイ — 勤怠管理システム"
echo "=========================================="
echo "Region: $AWS_REGION"
echo "Account: $AWS_ACCOUNT"
echo ""

# ========================================
# 1. Frontend 静的ビルド
# ========================================
echo "[1/3] Frontend 静的ビルド (Next.js export)"
cd "$PROJECT_ROOT/packages/frontend"

# 静的エクスポートモードでビルド
export DEPLOY=1
npm run build

# 出力ディレクトリ確認
if [ ! -d "out" ]; then
  echo "ERROR: Frontend build failed. 'out' directory not found."
  exit 1
fi

echo "✓ Frontend build complete: packages/frontend/out/"
echo ""

# ========================================
# 2. CDK 依存インストール（初回のみ）
# ========================================
echo "[2/3] CDK 依存確認"
cd "$PROJECT_ROOT/packages/infra"

if [ ! -d "node_modules" ]; then
  echo "Installing CDK dependencies..."
  npm install
else
  echo "✓ CDK dependencies already installed"
fi
echo ""

# ========================================
# 3. CDK Deploy（role assume）
# ========================================
echo "[3/3] CDK Deploy (assuming deploy role)"

# SageMaker 環境では直接 CloudFormation 操作できないため、
# CDK deploy role を assume して実行する
export AWS_REGION

# CRITICAL: SageMaker 制約 — Docker build に --network=sagemaker を強制
# CDK_DOCKER 環境変数で docker コマンドをラップする
export CDK_DOCKER="docker --network=sagemaker"

# CDK deploy (requires --require-approval never for automation)
npx cdk deploy \
  --require-approval never \
  --role-arn "$CDK_DEPLOY_ROLE" \
  --verbose

echo ""
echo "=========================================="
echo "デプロイ完了"
echo "=========================================="
echo ""
echo "CloudFront URL を確認してください:"
echo ""
echo "  aws cloudformation describe-stacks \\"
echo "    --stack-name Team-MIH-MSYS-Kintai \\"
echo "    --query 'Stacks[0].Outputs[?OutputKey==\`CloudFrontURL\`].OutputValue' \\"
echo "    --output text"
echo ""
echo "動作確認:"
echo ""
echo "  curl https://<distribution-id>.cloudfront.net/api/health"
echo ""
