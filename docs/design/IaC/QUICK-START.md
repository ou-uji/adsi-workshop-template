# AWS デプロイ クイックスタート

## 前提条件

- AWS CLI 設定済み（アカウント: 442797924420 / リージョン: ap-northeast-1）
- Docker 実行可能
- Node.js 18+ / npm インストール済み

## デプロイ手順（3ステップ）

### 1. デプロイ準備確認

```bash
npm run deploy:verify
```

このコマンドで以下を自動チェック:
- Backend ビルド
- Frontend 静的ビルド
- CDK TypeScript コンパイル
- AWS 認証情報
- Docker 起動状態

### 2. デプロイ実行

```bash
npm run deploy
```

処理内容（10-15分）:
1. Frontend を静的エクスポート (`packages/frontend/out/`)
2. Backend Docker イメージを ECR にプッシュ
3. CloudFormation スタック作成
   - ECS Fargate (backend)
   - S3 + CloudFront (frontend)
   - ALB (API ルーティング)

### 3. 動作確認

```bash
# CloudFront URL 取得
aws cloudformation describe-stacks \
  --stack-name Team-MIH-MSYS-Kintai \
  --query 'Stacks[0].Outputs[?OutputKey==`CloudFrontURL`].OutputValue' \
  --output text

# ヘルスチェック
curl https://<distribution-id>.cloudfront.net/api/health
```

期待値: `{"status":"ok"}`

## デプロイ後のアクセス

- **Frontend**: `https://<distribution-id>.cloudfront.net/`
- **Backend API**: `https://<distribution-id>.cloudfront.net/api/*`

CloudFront が ALB に `/api/*` をプロキシする構成。

## スタック削除

```bash
npm run deploy:destroy
```

⚠️ S3 バケットにファイルが残っている場合は手動削除が必要。

## トラブルシューティング

### `ROLLBACK_COMPLETE` エラー

初回デプロイ失敗でスタックが残った場合:

```bash
aws cloudformation delete-stack --stack-name Team-MIH-MSYS-Kintai
aws cloudformation wait stack-delete-complete --stack-name Team-MIH-MSYS-Kintai
npm run deploy
```

### Docker build Forbidden

SageMaker 環境特有のエラー。`deploy.sh` スクリプトが自動対応するため、
**直接 `docker build` コマンドを実行しないこと**。

### ECS タスクが起動しない

ALB のヘルスチェック確認:

```bash
aws ecs describe-services \
  --cluster Team-MIH-MSYS-Kintai-Cluster \
  --services Team-MIH-MSYS-Kintai-Service \
  --query 'services[0].events[0:5]'
```

タスクログ確認:

```bash
# CloudWatch Logs で確認
aws logs tail /ecs/Team-MIH-MSYS-Kintai-backend --follow
```

## 参考

- [設計書](./aws-deploy-design.md) — アーキテクチャ詳細
- [実装サマリ](./IMPLEMENTATION-SUMMARY.md) — 作成ファイル一覧
- [インフラ README](../../packages/infra/README.md) — CDK プロジェクト詳細

## コマンドまとめ

| コマンド | 用途 |
|---------|------|
| `npm run deploy:verify` | デプロイ前チェック |
| `npm run deploy` | フルデプロイ |
| `npm run deploy:destroy` | スタック削除 |
| `npm run cdk:build` | CDK TypeScript ビルドのみ |
| `npm run cdk:synth` | CDK 合成（テスト）|

## リソース一覧

デプロイ後に作成されるリソース:

| リソース | 名前 | 備考 |
|---------|------|------|
| CloudFormation Stack | `Team-MIH-MSYS-Kintai` | |
| ECS Cluster | `Team-MIH-MSYS-Kintai-Cluster` | |
| ECS Service | `Team-MIH-MSYS-Kintai-Service` | Desired: 1 |
| ALB | `Team-MIH-MSYS-Kintai-ALB` | |
| S3 Bucket | `team-mih-msys-kintai-frontend-442797924420` | 小文字 |
| CloudFront Distribution | （自動生成 ID） | Tag: `Team-MIH-MSYS-Kintai-CDN` |
| ECR Repository | `cdk-hnb659fds-container-assets-...` | CDK 管理 |

すべてのリソースに `Team-MIH-MSYS-Kintai` プレフィックスが付与されます。
