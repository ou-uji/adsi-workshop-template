# AWS デプロイ設計 — CDK IaC

> **ワークショップ範囲**: デモ用最小構成（H2・Default VPC・単一スタック）
> **SageMaker 制約**: `.claude/rules/sagemaker-deploy.md` / `.claude/skills/sagemaker-aws-deploy/SKILL.md` を参照

---

## 🎯 目的

勤怠管理システム（Java Spring Boot + Next.js）を AWS にデプロイする IaC を作成する。

- **Backend**: ECS Fargate で Spring Boot JAR を実行（H2 インメモリ）
- **Frontend**: S3 + CloudFront で Next.js 静的エクスポートを配信
- **CDK**: TypeScript で記述、単一スタック構成

---

## 🏛️ アーキテクチャ概要

```
[ユーザー]
    ↓
[CloudFront]
    ├─ /* → S3 (Next.js static files)
    └─ /api/* → [ALB] → [ECS Fargate Task]
                              ↓
                         [Spring Boot JAR]
                              ↓
                         [H2 in-memory DB]
```

### コンポーネント

| リソース | 用途 | 備考 |
|---------|------|------|
| **ECS Fargate** | Backend (Spring Boot) | port 8080、H2 workshop プロファイル |
| **ALB** | ECS タスクへのロードバランシング | ヘルスチェック `/api/health` |
| **S3 Bucket** | Frontend 静的ファイル | `BucketDeployment` で CFN 経由アップロード |
| **CloudFront** | CDN + API プロキシ | Origin: S3 (default) + ALB (/api/*) |
| **Default VPC** | ネットワーク | ワークショップ簡略化（カスタム VPC 不要） |

---

## 📛 リソース命名規則

**全リソースに `Team-MIH-MSYS-Kintai` プレフィックスを付与する。**

| リソース | 名前 | 備考 |
|---------|------|------|
| **CDK スタック** | `Team-MIH-MSYS-Kintai` | CloudFormation スタック名 |
| **S3 バケット** | `team-mih-msys-kintai-frontend-442797924420` | 小文字（S3 制約）+ account suffix |
| **ECS クラスター** | `Team-MIH-MSYS-Kintai-Cluster` | |
| **ECS サービス** | `Team-MIH-MSYS-Kintai-Service` | |
| **ALB** | `Team-MIH-MSYS-Kintai-ALB` | |
| **CloudFront Distribution** | （自動生成 ID） | Tag に `Name: Team-MIH-MSYS-Kintai-CDN` |

---

## 🚧 SageMaker 環境固有の制約（Critical）

> SageMaker Studio Code Editor の実行ロールには制限がある。
> 通常の Docker / S3 / CloudFormation コマンドは **禁止** → CDK に統合して回避。

| # | 制約 | 理由 | 回避策 |
|---|------|------|--------|
| 1 | `docker build` が Forbidden | ネットワーク指定なし不可 | `--network=sagemaker` を CDK で指定（`NetworkMode.custom('sagemaker')`） |
| 2 | S3 直接アップロード不可 | SageMaker Role にアプリバケット権限なし | `BucketDeployment` で CFN 経由アップロード |
| 3 | CloudFormation 直接操作不可 | SageMaker Role に CFN 権限なし | CDK deploy role を assume してから `cdk deploy` |
| 4 | ECR 空で ECS 起動は失敗 | タスクがクラッシュループ | `fromAsset()` で Docker ビルド+push を CDK 内で一体化 |
| 5 | `ROLLBACK_COMPLETE` | スタック再デプロイ不可 | 手動で delete してから recreate |

詳細: `.claude/skills/sagemaker-aws-deploy/SKILL.md`

---

## 📁 CDK プロジェクト構成

```
packages/infra/
├── bin/
│   └── app.ts               # CDK App エントリポイント
├── lib/
│   └── attendance-stack.ts  # スタック定義（backend + frontend）
├── cdk.json                 # CDK 設定
├── tsconfig.json            # TypeScript 設定
└── package.json             # 依存関係
```

### スタック構成（単一スタック）

`AttendanceStack` に backend (ECS) と frontend (S3/CloudFront) を統合。

```
AttendanceStack
├── Backend
│   ├── ECS Cluster
│   ├── ECS Task Definition (Fargate)
│   │   └── Docker image (fromAsset with network=sagemaker)
│   ├── ECS Service (Fargate)
│   └── ALB
└── Frontend
    ├── S3 Bucket
    ├── BucketDeployment (from packages/frontend/out/)
    └── CloudFront Distribution
        ├── Origin: S3 (default behavior)
        └── Origin: ALB (path pattern /api/*)
```

---

## 🐳 Dockerfile（Backend）

**場所**: `packages/backend/Dockerfile`

### 要件

- **Multi-stage ビルド**: Gradle ビルド → JRE ランタイム
- **`--network=sagemaker` 対応**: CDK 側で `NetworkMode.custom('sagemaker')` 指定
- **Profile**: `workshop` （H2 インメモリ）
- **Port**: 8080

### 構造

```dockerfile
# Stage 1: Build
FROM gradle:8.14.5-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=workshop
CMD ["java", "-jar", "app.jar"]
```

---

## 🎨 Frontend 静的エクスポート

### 変更点

**`packages/frontend/package.json`** に静的ビルドスクリプトを追加:

```json
"scripts": {
  "build:static": "next build",
  "export": "npm run build:static"
}
```

**`packages/frontend/next.config.ts`** に条件分岐を追加:

```typescript
const nextConfig: NextConfig = {
  // デプロイ時は静的エクスポート
  ...(process.env.DEPLOY === "1" ? { output: "export" } : {}),
  
  // 既存の SageMaker 設定は維持
  // ...
};
```

### 出力先

`packages/frontend/out/` （`BucketDeployment` のソースディレクトリ）

---

## 🚀 デプロイ手順

### 前提条件

- AWS CLI 設定済み（profile: `default` or `--profile` 指定）
- Node.js 18+ / npm インストール済み
- Docker インストール済み（ローカル検証用。CDK deploy 時は不要）

### コマンド

```bash
# 1. 依存インストール（初回のみ）
cd packages/infra
npm install

# 2. CDK 合成（テスト）
npx cdk synth

# 3. フルデプロイ（frontend build + cdk deploy）
npm run deploy

# or 手動実行
cd ../../
./scripts/deploy.sh
```

### デプロイスクリプト（`scripts/deploy.sh`）

1. Frontend を静的ビルド（`DEPLOY=1 npm run build:static`）
2. CDK deploy role を assume
3. `cdk deploy --require-approval never`

---

## 🔧 環境変数

| 変数名 | 用途 | 設定場所 |
|--------|------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring Boot プロファイル | Dockerfile (default: `workshop`) |
| `DEPLOY` | Next.js 静的エクスポートフラグ | deploy スクリプト |
| `AWS_REGION` | AWS リージョン | CDK deploy 時（default: `ap-northeast-1`） |

---

## 📊 デプロイ後の確認

### 1. CloudFormation スタック

```bash
aws cloudformation describe-stacks \
  --stack-name Team-MIH-MSYS-Kintai \
  --query 'Stacks[0].StackStatus'
```

期待値: `CREATE_COMPLETE` or `UPDATE_COMPLETE`

### 2. ECS タスク起動確認

```bash
aws ecs list-tasks \
  --cluster Team-MIH-MSYS-Kintai-Cluster \
  --service-name Team-MIH-MSYS-Kintai-Service
```

### 3. CloudFront URL

CDK 出力から CloudFront Distribution URL を取得:

```bash
aws cloudformation describe-stacks \
  --stack-name Team-MIH-MSYS-Kintai \
  --query 'Stacks[0].Outputs[?OutputKey==`CloudFrontURL`].OutputValue' \
  --output text
```

ブラウザで開いて `/api/health` を確認:

```bash
curl https://<distribution-id>.cloudfront.net/api/health
```

期待値: `{"status":"ok"}`

---

## 🗑️ スタック削除

```bash
cd packages/infra
npx cdk destroy
```

**注意**: S3 バケットに残存ファイルがある場合は手動削除が必要（`--force` 不可）。

---

## 📝 既知の問題と回避策

### 1. `ROLLBACK_COMPLETE` でスタックが残る

**原因**: 初回デプロイ失敗（ECR 空 / IAM 権限不足等）

**回避策**: スタックを削除してから再デプロイ

```bash
aws cloudformation delete-stack --stack-name Team-MIH-MSYS-Kintai
aws cloudformation wait stack-delete-complete --stack-name Team-MIH-MSYS-Kintai
npx cdk deploy
```

### 2. Docker build が Forbidden

**原因**: SageMaker 環境でネットワーク指定なし

**回避策**: CDK の `fromAsset()` に `networkMode: NetworkMode.custom('sagemaker')` を指定済み

### 3. S3 アップロードが AccessDenied

**原因**: SageMaker Role にアプリバケット権限なし

**回避策**: `BucketDeployment` で CFN 経由アップロード（CDK が自動処理）

---

## 📚 参考資料

- [CDK TypeScript リファレンス](https://docs.aws.amazon.com/cdk/api/v2/docs/aws-construct-library.html)
- [ECS Fargate デプロイパターン](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [CloudFront + ALB オリジン構成](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/DownloadDistS3AndCustomOrigins.html)
- プロジェクト内: `.claude/skills/sagemaker-aws-deploy/SKILL.md`

---

## ✅ チェックリスト

- [x] 設計書作成
- [x] CDK プロジェクト初期化（`packages/infra/`）
- [x] `AttendanceStack` 実装（backend + frontend）
- [x] Dockerfile 作成（multi-stage build with SageMaker network support）
- [x] Frontend 静的ビルド対応（`DEPLOY=1` 環境変数で `output: 'export'`）
- [x] デプロイスクリプト作成（`scripts/deploy.sh` with CDK deploy role assume）
- [x] CLAUDE.md に命名規則を追記
- [ ] 初回デプロイ実行（requires frontend/out directory）
- [ ] 動作確認（`/api/health` + フロントエンド表示）
