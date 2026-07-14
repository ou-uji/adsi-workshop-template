#!/bin/bash
set -euo pipefail

# AWS デプロイスクリプト — CDK deploy（ECS Fargate: backend + frontend）
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
# 1. CDK 依存インストール（初回のみ）
# ========================================
echo "[1/2] CDK 依存確認"
cd "$PROJECT_ROOT/packages/infra"

if [ ! -d "node_modules" ]; then
  echo "Installing CDK dependencies..."
  npm install
else
  echo "✓ CDK dependencies already installed"
fi
echo ""

# ========================================
# 2. CDK Deploy（role assume）
# ========================================
echo "[2/2] CDK Deploy (assuming deploy role)"

export AWS_REGION

# CRITICAL: SageMaker 制約 — Docker build に --network=sagemaker を強制
export CDK_DOCKER_BUILD_ARGS="--network=sagemaker"

# CDK deploy
npx cdk deploy \
  --require-approval never \
  --role-arn "$CDK_DEPLOY_ROLE" \
  --verbose

echo ""
echo "=========================================="
echo "デプロイ完了！"
echo "=========================================="
echo ""
echo "アプリ URL を確認:"
echo "  aws cloudformation describe-stacks --stack-name Team-MIH-MSYS-Kintai \\"
echo "    --query 'Stacks[0].Outputs' --output table"
echo ""
