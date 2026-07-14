# 勤怠管理システム（ADSI ワークショップ / Day 2 チーム開発）

Java(Spring Boot 4) + Next.js(TypeScript) + H2 で作る小規模な勤怠管理デモ。
**共通基盤（Unit 0）** を代表 1 台で先行して作り切り、その上に Unit A/B/C/D を並列で載せる。

> プロジェクト方針・タスク配置・規約は [CLAUDE.md](./CLAUDE.md) と `.claude/rules/` を参照。

---

## 🚦 現在地（2026-07-14）

**Unit A（社員管理）+ Unit D（認証）結合完了** — ログイン → 社員 CRUD が画面で通る。

| Unit | 状態 | 内容 |
|------|------|------|
| 共通基盤 | ✅ 完了 | 認証基盤・Employee Entity・共通例外・Enum・Flyway V1+seed・Health API |
| D 認証 | ✅ 完了 | login/logout/me API・セッション認証・ログイン画面・AuthProvider |
| A 社員管理 | ✅ 完了 | 登録/一覧/取得/編集 API（ADMIN のみ）・社員管理画面 |
| B 勤怠打刻 | 🔲 未着手 | 出勤/退勤/履歴 |
| C 休暇申請 | 🔲 未着手 | 申請/承認（ADMIN のみ） |

**テスト**: backend 48 テスト全通過（Unit テスト + 結合テスト 7 本）

---

## 🖥️ 起動とブラウザプレビュー

### SageMaker Code Editor（本ワークショップの標準）

```bash
npm run dev:sagemaker        # frontend build + backend(8080/H2) + next(3001) + 復元proxy(3000) を一括起動
npm run dev:sagemaker:stop   # 3000/3001/8080 を停止
```

起動後、**ブラウザで開く手順**:

1. **PORTS タブ**でポート **3000** の行の**地球儀ボタン** を押す
2. 開いた URL のアドレスバーで **`ports` → `absports`** に置換して開く:
   ```
   https://<studio-domain>/codeeditor/default/absports/3000/
   ```
3. ログイン画面が表示される → `admin@example.com` / `password` でログイン
4. ダッシュボード → 「社員管理」カードをクリック → 一覧/新規登録

### ポート競合で起動失敗するとき

```bash
npm run dev:sagemaker:stop   # まずこれ
# 効かない場合:
pkill -f "bootRun\|next.*3001\|sagemaker-proxy"
sleep 2
npm run dev:sagemaker
```

### ローカル（参考・SageMaker 以外）

```bash
npm run boot:workshop        # backend 単体（H2, :8080）
npm run dev                  # frontend dev（:3000, basePath なし）
```

---

## 🔧 チェック（コミット前に実行）

```bash
npm run check:backend        # cd packages/backend && ./gradlew check（テスト含む）
npm run lint:frontend        # frontend ESLint
```

初回セットアップ:

```bash
npm run setup                # npm install + frontend build
```

---

## 🔑 デモ用ログイン情報（Flyway seed）

| メール | ロール | パスワード |
|--------|--------|-----------|
| admin@example.com | ADMIN | `password` |
| hanako@example.com | MEMBER | `password` |
| jiro@example.com | MEMBER | `password` |

- ADMIN: 社員管理（登録/一覧/編集）が使える
- MEMBER: ダッシュボードのみ（社員管理は 403）

---

## 🩺 トラブルシューティング

| 症状 | 原因 | 対処 |
|------|------|------|
| `Unsupported URL path` | URL が `ports` のまま | アドレスバーで `ports` → `absports` に置換 |
| バッジが赤「backend: 未接続」 | backend 未起動 / :8080 不通 | `npm run dev:sagemaker:stop` → 再起動 |
| 画面が 404 | `npm run dev` で起動している | `npm run dev:sagemaker` に切替（basePath が必要） |
| ポートが掴まれて起動失敗 | 前回のプロセス残留 | `pkill -f "bootRun\|next.*3001\|sagemaker-proxy"` → 再起動 |
| ログイン後 403 | MEMBER でログインして社員管理にアクセス | ADMIN（admin@example.com）でログインし直す |

---

## 📁 構成

```
packages/
├── backend/         Spring Boot 4（レイヤード + ライトDDD / H2・Flyway）
│   └── com.example.attendance/
│       ├── common/      共通基盤: SecurityConfig・例外ハンドラ・Enum・HealthController
│       ├── auth/        Unit D: AuthController・CustomUserDetailsService・DTO
│       ├── employee/    Unit A: EmployeeController/Service/Repository・DTO
│       ├── attendance/  Unit B（未実装）
│       └── leave/       Unit C（未実装）
├── frontend/        Next.js（App Router / features ベース / withBasePath）
│   ├── src/app/         画面: /, /login, /employees, /employees/new, /employees/[id]/edit
│   ├── src/features/    auth(認証Context), employee(一覧/フォーム/API), health(バッジ)
│   └── src/lib/         api-client（withBasePath fetch ラッパー）
└── infra/           （将来: CDK / AWS デプロイ）

docs/
├── IT/              結合テスト記録
├── design/          設計書（SDD）
├── requirements/    確定要求仕様
└── working/         作業用ドキュメント
```

---

## 📚 ドキュメント

- 確定要求: [docs/requirements/ou-attendance.md](./docs/requirements/ou-attendance.md)
- 実装方針: [docs/requirements/implementation-strategy.md](./docs/requirements/implementation-strategy.md)
- 共通基盤 設計書: [docs/design/common-foundation.md](./docs/design/common-foundation.md)
- Unit A 設計書: [docs/design/unit/a-employee-sdd-design.md](./docs/design/unit/a-employee-sdd-design.md)
- 結合テスト記録: [docs/IT/auth-employee-integration.md](./docs/IT/auth-employee-integration.md)
