# 勤怠管理システム（ADSI ワークショップ / Day 2 チーム開発）

Java(Spring Boot 4) + Next.js(TypeScript) + H2 で作る小規模な勤怠管理デモ。
**共通基盤（Unit 0）** を代表 1 台で先行して作り切り、その上に Unit A/B/C を並列で載せる。

> プロジェクト方針・タスク配置・規約は [CLAUDE.md](./CLAUDE.md) と `.claude/rules/` を参照。

---

## 🚦 現在地（2026-07-14）

**共通基盤フェーズ = 完了**（起動 + 画面枠 + テスト土台）。業務ロジックは各 Unit で実装。

| 層 | 状態 |
|----|------|
| Backend | `Employee` Entity / Repository・`/api/health`・Flyway V1(+seed)・Spring Security 最小・テスト5本緑 |
| Frontend | Next.js scaffold・ダッシュボード**枠**（メニューはプレースホルダ「未実装」）・`withBasePath()` fetch |
| 疎通 | ブラウザ → proxy(3000) → next(3001) → rewrites → backend(8080) `/api/health` = `{status:"ok"}` 確認済み |

⚠️ **今の画面は「枠」だけ**。ログイン認証・各機能の中身は次工程（下記「目標UI」）で実装する。

---

## 🎯 目標UI（To-Be・次工程で実装）

現状はトップに全機能をカード表示しているが、**最終的には下記のフローにする**:

```
1. 最初の画面 = ログイン画面（ユーザーID/メール + パスワード）
        │  認証成功（Spring Security セッション）
        ▼
2. アプリshell（ログイン後のみ表示）
   ┌─────────────┬─────────────────────────────┐
   │  サイドメニュー  │        メインエリア              │
   │  ・社員管理     │  ← メニュー選択で対応画面を     │
   │  ・勤怠打刻     │     ここに遷移 / レンダリング     │
   │  ・休暇申請     │                             │
   │ (ADMINは承認も) │                             │
   └─────────────┴─────────────────────────────┘
```

- **未ログイン時**は保護ルートにアクセスさせず、ログイン画面へリダイレクト。
- メニューは `role`（ADMIN / MEMBER）で出し分け（休暇の承認は ADMIN のみ）。
- 実装は Unit D（認証フロー本体 + shell）で行い、各機能は `features/{employee,attendance,leave}` に載せる。

> この目標UIは共通基盤フェーズのスコープ外（[Plan](./docs/design/common-foundation-implementation-plan.md) の「やらないこと」に明記）。
> 次セッションで `work-decomposition` → Unit D/A/B/C の設計・実装へ。

---

## 🖥️ 起動とブラウザプレビュー

### SageMaker Code Editor（本ワークショップの標準）

```bash
npm run dev:sagemaker        # frontend build + backend(8080/H2) + next(3001) + 復元proxy(3000) を一括起動
npm run dev:sagemaker:stop   # 3000/3001/8080 を停止
```

起動後、**ブラウザで開く手順**（この操作だけは手動）:

1. **PORTS タブ**でポート **3000** の行の**地球儀ボタン 🌐** を押す
2. 開いた URL は `.../codeeditor/default/ports/3000/` 形式（そのままだと SPA が壊れる）
3. アドレスバーで **`ports` → `absports`** に置換して開く:
   ```
   https://<studio-domain>/codeeditor/default/absports/3000/
   ```

**正常確認**: 画面が出て、右上のバッジが **緑「backend: 正常」** になっていれば疎通OK。

> なぜ `absports` か・トラブル対応は `.claude/skills/sagemaker-code-editor/SKILL.md` /
> `.claude/rules/sagemaker-preview.md` を参照。

### ローカル（参考・SageMaker 以外）

```bash
npm run boot:workshop        # backend 単体（H2, :8080）
npm run dev                  # frontend dev（:3000, basePath なし）
```

---

## 🔧 チェック（共有・コミット前）

```bash
npm run check:backend        # cd packages/backend && ./gradlew check（テスト含む）
npm run lint:frontend        # frontend ESLint
```

初回セットアップ:

```bash
npm run setup                # npm install + frontend build
```

---

## 🩺 トラブルシューティング

| 症状 | 原因 | 対処 |
|------|------|------|
| `Unsupported URL path` | URL が `ports` のまま | アドレスバーで `ports` → `absports` に置換 |
| バッジが赤「backend: 未接続」 | backend 未起動 / :8080 不通 | `/tmp/dev-sagemaker.log` を確認、`npm run dev:sagemaker` を再実行 |
| 画面が 404 | `npm run dev` で起動している | `npm run dev:sagemaker` に切替（basePath が必要） |
| ポートが掴まれて起動失敗 | 前回のプロセス残留 | `npm run dev:sagemaker:stop` → 再起動 |

---

## 📁 構成

```
packages/
├── backend/         Spring Boot 4（レイヤード + ライトDDD / H2・Flyway）
│   └── com.example.attendance/
│       ├── common/      共通基盤: 認証・例外・Enum・health
│       ├── employee/    Unit A（Entity/Repository は共通基盤で先行）
│       ├── attendance/  Unit B（package-info のみ）
│       └── leave/       Unit C（package-info のみ）
├── frontend/        Next.js（App Router / features ベース / withBasePath）
│   ├── src/app/         画面（現状はダッシュボード枠）
│   ├── src/features/    機能別（health バッジ実装済み）
│   └── src/lib/         api-client（withBasePath fetch ラッパー）
└── infra/           （将来: CDK / AWS デプロイ）

scripts/dev-sagemaker.sh          一括起動スクリプト
packages/frontend/scripts/sagemaker-proxy.mjs   復元プロキシ
```

### デモ用シードユーザー（Flyway `V1_1__seed_common.sql`）

| メール | ロール | パスワード |
|--------|--------|-----------|
| admin@example.com | ADMIN | `password` |
| hanako@example.com | MEMBER | `password` |
| jiro@example.com | MEMBER | `password` |

> パスワードは BCrypt ハッシュで保存（平文保存禁止）。ログインフローは Unit D で実装。

---

## 📚 ドキュメント

- 確定要求: [docs/requirements/ou-attendance.md](./docs/requirements/ou-attendance.md)
- 実装方針: [docs/requirements/implementation-strategy.md](./docs/requirements/implementation-strategy.md)
- 共通基盤 設計書（SDD）: [docs/design/common-foundation.md](./docs/design/common-foundation.md)
- 共通基盤 実装計画（Plan）: [docs/design/common-foundation-implementation-plan.md](./docs/design/common-foundation-implementation-plan.md)
```
