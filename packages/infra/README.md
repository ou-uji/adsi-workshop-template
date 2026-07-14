# AWS CDK Infrastructure

勤怠管理システムの AWS インフラストラクチャ定義（CDK）。

## 構成

- **Backend**: ECS Fargate (Spring Boot JAR)
- **Frontend**: S3 + CloudFront (Next.js static export)
- **Network**: Default VPC (workshop simplicity)

## デプロイ

```bash
# リポジトリルートから実行
./scripts/deploy.sh
```

または手動で:

```bash
# 1. Frontend ビルド
cd ../frontend
DEPLOY=1 npm run build

# 2. CDK deploy
cd ../infra
npx cdk deploy --require-approval never
```

## 開発コマンド

```bash
# TypeScript ビルド
npm run build

# CDK 合成（テスト）
# 注: frontend/out ディレクトリが存在しない場合はエラーになります
npm run synth

# スタック削除
npm run destroy
```

## 制約事項

- **SageMaker 環境**: Docker build には `--network=sagemaker` が必要（CDK が自動処理）
- **Frontend ディレクトリ**: `cdk synth` 実行前に `packages/frontend/out/` が必要
- **初回デプロイ**: 10-15分程度かかります（Docker イメージのビルド+push）

## リソース名

全リソースに `Team-MIH-MSYS-Kintai` プレフィックスを使用。
詳細は `/docs/design/IaC/aws-deploy-design.md` を参照。

## トラブルシューティング

### `ROLLBACK_COMPLETE` エラー

スタックを削除してから再デプロイ:

```bash
aws cloudformation delete-stack --stack-name Team-MIH-MSYS-Kintai
aws cloudformation wait stack-delete-complete --stack-name Team-MIH-MSYS-Kintai
npm run deploy
```

### Docker build Forbidden

CDK が `--network=sagemaker` を自動適用するため、手動で Docker コマンドを実行しないでください。
