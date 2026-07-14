# AWS CDK Infrastructure Implementation Summary

## 実装完了日

2026-07-14 (ブランチ: `feature/iac-aws-deploy`)

---

## 作成ファイル一覧

### 1. 設計ドキュメント

- `/docs/design/IaC/aws-deploy-design.md` — AWS デプロイ設計書（アーキテクチャ・命名規則・制約）

### 2. CDK Infrastructure (`packages/infra/`)

```
packages/infra/
├── bin/
│   └── app.ts                    # CDK App エントリポイント
├── lib/
│   └── attendance-stack.ts       # AttendanceStack 実装（backend + frontend）
├── cdk.json                      # CDK 設定
├── cdk.context.json              # Docker network mode 設定
├── tsconfig.json                 # TypeScript 設定
├── package.json                  # 依存関係 (aws-cdk-lib, constructs)
├── .gitignore                    # CDK 出力を除外
└── README.md                     # インフラ README
```

**主要リソース**:
- ECS Fargate (Spring Boot backend, port 8080)
- ALB (ヘルスチェック `/api/health`)
- S3 Bucket (frontend static files)
- CloudFront Distribution (S3 origin + ALB /api/* origin)
- Default VPC (workshop simplicity)

**命名規則**: 全リソースに `Team-MIH-MSYS-Kintai` プレフィックス

### 3. Backend Dockerfile

- `/packages/backend/Dockerfile` — Multi-stage build (Gradle build → JRE runtime)
  - SageMaker `--network=sagemaker` 対応
  - Profile: `workshop` (H2 in-memory)
  - Port: 8080

### 4. Frontend 静的エクスポート対応

**変更ファイル**:
- `/packages/frontend/package.json` — `build:static` スクリプト追加
- `/packages/frontend/next.config.ts` — `DEPLOY=1` 環境変数で `output: 'export'`

### 5. デプロイスクリプト

- `/scripts/deploy.sh` — フルデプロイフロー
  1. Frontend 静的ビルド (`DEPLOY=1`)
  2. CDK deploy role assume
  3. `cdk deploy` 実行 (Docker network: sagemaker)

- `/scripts/verify-deploy-ready.sh` — デプロイ前チェック
  - Backend build
  - Frontend static export
  - CDK TypeScript compile
  - AWS credentials
  - Docker availability

### 6. ルート package.json 更新

新規スクリプト追加:
```json
"cdk:build": "npm --workspace @attendance/infra run build",
"cdk:synth": "npm --workspace @attendance/infra run synth",
"deploy:verify": "bash scripts/verify-deploy-ready.sh",
"deploy": "bash scripts/deploy.sh",
"deploy:destroy": "npm --workspace @attendance/infra run destroy"
```

### 7. CLAUDE.md 更新

AWS リソース命名規則を追記:
```
- **AWS リソース名**: 全リソースに **`Team-MIH-MSYS-Kintai`** プレフィックスを付与（S3 は小文字 `team-mih-msys-kintai-`）
```

---

## SageMaker 制約対応

| # | 制約 | 対応方法 |
|---|------|---------|
| 1 | Docker build Forbidden | `CDK_DOCKER="docker --network=sagemaker"` 環境変数でラップ（`deploy.sh`） |
| 2 | ECR 空で ECS 失敗 | `fromAsset()` で Docker ビルド+push を一体化 |
| 3 | S3 直接アップロード不可 | `BucketDeployment` で CFN 経由アップロード |
| 4 | CloudFormation 直接操作不可 | CDK deploy role (`arn:aws:iam::442797924420:role/cdk-hnb659fds-deploy-role-...`) を assume |
| 5 | ROLLBACK_COMPLETE | 設計書にトラブルシューティング手順を記載（delete → recreate） |

詳細: `.claude/skills/sagemaker-aws-deploy/SKILL.md`

---

## デプロイフロー

### 初回デプロイ

```bash
# 1. デプロイ準備確認
npm run deploy:verify

# 2. デプロイ実行（10-15分）
npm run deploy
```

### デプロイ内容

1. **Frontend 静的ビルド**: `packages/frontend/out/` に Next.js 静的ファイルを生成
2. **Backend Docker イメージ**: ECR にプッシュ（`fromAsset` が自動処理）
3. **CloudFormation スタック**: `Team-MIH-MSYS-Kintai` スタック作成
   - ECS Cluster / Task Definition / Service
   - ALB / Target Group / Listener
   - S3 Bucket / CloudFront Distribution
   - BucketDeployment (frontend → S3)

### 動作確認

```bash
# CloudFront URL 取得
aws cloudformation describe-stacks \
  --stack-name Team-MIH-MSYS-Kintai \
  --query 'Stacks[0].Outputs[?OutputKey==`CloudFrontURL`].OutputValue' \
  --output text

# ヘルスチェック
curl https://<distribution-id>.cloudfront.net/api/health
# 期待値: {"status":"ok"}
```

---

## 未実施項目（次のステップ）

- [ ] 初回デプロイ実行（frontend/out ディレクトリが必要）
- [ ] 動作確認（/api/health + フロントエンド表示）
- [ ] カスタムドメイン設定（ACM 証明書 + Route 53）
- [ ] HTTPS のみに制限（ALB リスナールール）
- [ ] CloudWatch Logs の長期保持設定（現在: 7日）
- [ ] コスト最適化（ECS タスク数・Fargate スペック調整）

---

## 参考資料

- [設計書](./aws-deploy-design.md) — アーキテクチャ詳細・命名規則・制約
- [インフラ README](../../packages/infra/README.md) — CDK プロジェクト概要
- [SageMaker デプロイ制約](.claude/rules/sagemaker-deploy.md) — 制約詳細
- [SageMaker デプロイスキル](.claude/skills/sagemaker-aws-deploy/SKILL.md) — つまずき事例

---

## 技術スタック

- **CDK**: v2.170.0 (TypeScript)
- **AWS リージョン**: ap-northeast-1
- **AWS アカウント**: 442797924420
- **Backend**: Java 21 / Spring Boot 4.0.0 / Gradle 8.14.5
- **Frontend**: Next.js 16.2.10 / TypeScript 5
- **Container**: Docker (Gradle multi-stage build)
- **Network**: Default VPC (workshop simplicity)

---

## 実装時の課題と解決

### 1. TypeScript コンパイルエラー

**問題**: `@types/node` が見つからない → `process`, `__dirname`, `path` が未定義

**解決**: 
- `packages/infra/package.json` に `@types/node`, `ts-node`, `source-map-support` を追加
- `tsconfig.json` の `typeRoots` にワークスペースルートを含める

### 2. CDK synth エラー (frontend/out not found)

**問題**: `cdk synth` 時に `packages/frontend/out` ディレクトリが存在しない

**解決**: 
- `BucketDeployment` は deploy 時に必要（synth 時は警告のみ）
- `deploy.sh` スクリプトで frontend ビルド → CDK deploy の順序を保証

### 3. Docker network mode の指定方法

**問題**: CDK の `fromAsset()` に `networkMode` プロパティがない

**解決**: 
- `CDK_DOCKER` 環境変数で docker コマンドをラップ
- `deploy.sh` で `export CDK_DOCKER="docker --network=sagemaker"` を設定

---

## コミット推奨メッセージ

```
feat: AWS CDK インフラ実装 (ECS Fargate + S3/CloudFront)

- CDK プロジェクト作成 (packages/infra/)
- AttendanceStack: backend (ECS) + frontend (S3/CloudFront)
- Dockerfile: multi-stage build with SageMaker network support
- Frontend: 静的エクスポート対応 (DEPLOY=1)
- デプロイスクリプト: deploy.sh + verify-deploy-ready.sh
- 命名規則: Team-MIH-MSYS-Kintai プレフィックス統一
- SageMaker 制約対応: Docker network / BucketDeployment / deploy role assume

設計: docs/design/IaC/aws-deploy-design.md
```

---

## 完了チェック

- [x] 設計書作成
- [x] CDK プロジェクト初期化
- [x] スタック実装 (backend + frontend)
- [x] Dockerfile 作成
- [x] Frontend 静的ビルド対応
- [x] デプロイスクリプト作成
- [x] 検証スクリプト作成
- [x] CLAUDE.md 更新 (命名規則)
- [ ] 初回デプロイ実行
- [ ] 動作確認
