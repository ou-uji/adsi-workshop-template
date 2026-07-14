# AWS へのデプロイ試行錯誤ノウハウ（2026-07-14）

> SageMaker Code Editor から CDK で ECS Fargate にデプロイする際に踏んだ地雷と解決策。
> 環境: Account `442797924420` / Region `ap-northeast-1` / CDK v2.1130

---

## 環境の前提

- **SageMaker Execution Role** は直接的な AWS リソース操作権限がほぼない
- 唯一できること: **CDK bootstrap ロール群を assume** する
- CDK bootstrap ロール群:
  - `cdk-hnb659fds-deploy-role-*` — CFN 操作用
  - `cdk-hnb659fds-cfn-exec-role-*` — CFN が実際にリソースを作る際に使う
  - `cdk-hnb659fds-file-publishing-role-*` — S3 assets アップロード用
  - `cdk-hnb659fds-image-publishing-role-*` — ECR イメージ push 用
  - `cdk-hnb659fds-lookup-role-*` — VPC 等の context lookup 用

---

## つまずき一覧と解決策

### 1. Docker build が Forbidden

**症状**: `Error response from daemon: Forbidden. Reason: [ImageBuild] 'sagemaker' is the only user allowed network input`

**解決**: `--network=sagemaker` を必ず指定。CDK では:
```ts
import * as ecr_assets from "aws-cdk-lib/aws-ecr-assets";

image: ecs.ContainerImage.fromAsset("path", {
  networkMode: ecr_assets.NetworkMode.custom("sagemaker"),
})
```

---

### 2. Default VPC が存在しない

**症状**: `Could not find any VPCs matching {"isDefault":"true"}`

**解決**: `Vpc.fromLookup()` をやめて `new ec2.Vpc()` で新規作成:
```ts
const vpc = new ec2.Vpc(this, "VPC", {
  maxAzs: 2,
  natGateways: 0,
  subnetConfiguration: [
    { name: "Public", subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
  ],
});
```

---

### 3. `cdk deploy --role-arn` で PassRole エラー

**症状**: `iam:PassRole on resource: .../cdk-hnb659fds-deploy-role-... because no identity-based policy allows the iam:PassRole action`

**原因**: CDK が `--role-arn` で指定された deploy role を **CFN の changeset にも RoleARN として渡す**。deploy role 自身が自分を PassRole する権限がない。

**解決**: `cdk deploy --role-arn` は使わない。代わりに:
1. SageMaker role から `--role-arn` 付きで Docker build + ECR push だけ成功させる（CDK が assets を publish するところまでは動く）
2. その後 **deploy role を手動 assume** して `aws cloudformation create-stack` を直接実行

```bash
# Step 1: イメージを publish（--role-arn 付きで CDK 実行 → PassRole で失敗するが image は push 済み）
npx cdk deploy --require-approval never \
  --role-arn "arn:aws:iam::${ACCOUNT}:role/cdk-hnb659fds-deploy-role-${ACCOUNT}-${REGION}"
# → "iam:PassRole" エラーで止まるが、Docker image の ECR push は完了している

# Step 2: deploy role を assume して CFN を直接叩く
CREDS_JSON=$(aws sts assume-role --role-arn "arn:aws:iam::${ACCOUNT}:role/cdk-hnb659fds-deploy-role-${ACCOUNT}-${REGION}" --role-session-name deploy)
export AWS_ACCESS_KEY_ID=$(echo "$CREDS_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['Credentials']['AccessKeyId'])")
export AWS_SECRET_ACCESS_KEY=$(echo "$CREDS_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['Credentials']['SecretAccessKey'])")
export AWS_SESSION_TOKEN=$(echo "$CREDS_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['Credentials']['SessionToken'])")

aws cloudformation create-stack \
  --stack-name Team-MIH-MSYS-Kintai \
  --template-body file://cdk.out/Team-MIH-MSYS-Kintai.template.json \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
  --role-arn "arn:aws:iam::${ACCOUNT}:role/cdk-hnb659fds-cfn-exec-role-${ACCOUNT}-${REGION}" \
  --parameters ParameterKey=BootstrapVersion,ParameterValue=/cdk-bootstrap/hnb659fds/version
```

---

### 4. assume 済み deploy role で `cdk deploy`（`--role-arn` なし）→ ECR 権限不足

**症状**: `ecr:DescribeRepositories ... not authorized`

**原因**: deploy role は CFN 操作専用。ECR への read/write は image-publishing role の仕事だが、deploy role からは assume できない。

**教訓**: assume 済み role で `cdk deploy` を実行しても、CDK が内部で publishing role を再 assume しようとして失敗する。**CDK 単体での一発デプロイは SageMaker 環境では不可能**。2 段階方式が必須。

---

### 5. ROLLBACK_COMPLETE → delete → recreate

**症状**: スタックが `ROLLBACK_COMPLETE` で固まり再デプロイ不可

**解決**:
```bash
# deploy role を assume してから:
aws cloudformation delete-stack --stack-name Team-MIH-MSYS-Kintai
aws cloudformation wait stack-delete-complete --stack-name Team-MIH-MSYS-Kintai
# その後 create-stack で再作成
```

---

### 6. Next.js static export と動的ルート `[id]`

**症状**: `Page "/employees/[id]/edit" is missing "generateStaticParams()"`

**原因**: `output: 'export'` は全ページを事前生成するため、動的パラメータが必要。社員 ID は事前に不明。

**解決**: `output: 'export'`（S3 配信）をやめて `output: 'standalone'`（ECS で Node.js サーバー）に変更。Frontend も ECS Fargate コンテナとしてデプロイ。ALB で path-based routing（`/api/*` → backend, それ以外 → frontend）。

---

### 7. ECS Frontend Service の health check が通らない

**症状**: Frontend Service が `CREATE_IN_PROGRESS` のまま長時間停滞 → タイムアウト → ROLLBACK

**原因**: `wget` が node:alpine イメージに入っていない / startPeriod が短すぎ

**解決**:
```ts
healthCheck: {
  command: ["CMD-SHELL", "node -e \"fetch('http://localhost:3000/').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))\""],
  interval: cdk.Duration.seconds(30),
  timeout: cdk.Duration.seconds(10),
  retries: 5,
  startPeriod: cdk.Duration.seconds(60),
}
```

---

## 最終的に動いたデプロイ手順（2 段階方式）

```bash
cd packages/infra

# === Step 1: Docker image を ECR に push ===
# SageMaker role のまま --role-arn 付きで実行。
# PassRole エラーで「失敗」するが、Docker build + ECR push は完了する。
npx cdk deploy --require-approval never \
  --role-arn "arn:aws:iam::442797924420:role/cdk-hnb659fds-deploy-role-442797924420-ap-northeast-1"
# ↑ 最後に PassRole エラーが出るが、image は push 済み。OK。

# === Step 2: CFN スタック作成 ===
# deploy role を assume → create-stack を直接叩く
CREDS_JSON=$(aws sts assume-role \
  --role-arn "arn:aws:iam::442797924420:role/cdk-hnb659fds-deploy-role-442797924420-ap-northeast-1" \
  --role-session-name cfn-deploy)
export AWS_ACCESS_KEY_ID=$(echo "$CREDS_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['Credentials']['AccessKeyId'])")
export AWS_SECRET_ACCESS_KEY=$(echo "$CREDS_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['Credentials']['SecretAccessKey'])")
export AWS_SESSION_TOKEN=$(echo "$CREDS_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['Credentials']['SessionToken'])")

aws cloudformation create-stack \
  --stack-name Team-MIH-MSYS-Kintai \
  --template-body file://cdk.out/Team-MIH-MSYS-Kintai.template.json \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
  --role-arn "arn:aws:iam::442797924420:role/cdk-hnb659fds-cfn-exec-role-442797924420-ap-northeast-1" \
  --parameters ParameterKey=BootstrapVersion,ParameterValue=/cdk-bootstrap/hnb659fds/version

# === Step 3: 待機 ===
aws cloudformation wait stack-create-complete --stack-name Team-MIH-MSYS-Kintai

# === Step 4: URL 確認 ===
aws cloudformation describe-stacks --stack-name Team-MIH-MSYS-Kintai \
  --query "Stacks[0].Outputs" --output table
```

---

## アーキテクチャ（最終構成）

```
[ブラウザ]
    ↓
[ALB (MIH-MSYS-Kintai-ALB)]
    ├─ /api/* → Backend TG → ECS Fargate (Spring Boot :8080)
    └─ /* (default) → Frontend TG → ECS Fargate (Next.js standalone :3000)

[VPC: Team-MIH-MSYS-Kintai-VPC]
    └─ 2 AZ × Public Subnet（NAT Gateway なし）
```

---

## リソース命名

| リソース | 名前 |
|---------|------|
| Stack | `Team-MIH-MSYS-Kintai` |
| VPC | `Team-MIH-MSYS-Kintai-VPC` |
| ECS Cluster | `Team-MIH-MSYS-Kintai-Cluster` |
| Backend Service | `Team-MIH-MSYS-Kintai-Backend` |
| Frontend Service | `Team-MIH-MSYS-Kintai-Frontend` |
| ALB | `MIH-MSYS-Kintai-ALB` |

---

## 注意事項

- STS assume-role のセッションは **1 時間で expire** する。長時間の wait 中に切れることがある
- `cdk.out/` のテンプレートは `npx cdk synth` で再生成可能（`--role-arn` なしでも synth だけなら動く場合あり）
- テンプレートサイズが 51200 bytes を超えたら `--template-body` が使えない → S3 経由が必要だが file-publishing role で bootstrap bucket にアップロードする必要がある
- スタック更新時は `create-stack` ではなく `update-stack` or `create-change-set` + `execute-change-set` を使う
