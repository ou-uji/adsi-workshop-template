# CLAUDE.md — プロジェクト入口（毎セッション読み込み）

> このファイルは新しいセッションでの再開用。今日のタスク配置・技術・コード構造・進捗を記録している。
> 詳細規約は `.claude/rules/`、進め方は `.claude/skills/`、確定要求は `docs/requirements/ou-attendance.md` を参照。

---

## 🎯 今日作るもの

**勤怠管理システム（小規模デモ）** — Day 2 のチーム共同開発で「1 から作る」。
スライド ⑥「チーム開発の考え方」を実践：**機能で分担 / つなぎ目(IF)を先に合意 / 共通基盤を先行**。

- **ブランチ**: `develop` に Unit A + D 結合済み。現在 = **`feature/IT_A_and_D`** → develop マージ完了
- **実行環境**: AWS SageMaker Code Editor / `ml.t3.2xlarge`（8vCPU/32GiB）/ Image `code-editor-java-claude v3`
- **チーム**: 経験者は代表（あなた）。他メンバーは Java 本格開発の経験が浅い → 「迷子にならない」を最優先
- **デモゴール**: ログイン → 打刻 → 履歴 → 休暇1件申請 をブラウザ(absports)で通し実演（承認実演は余力があれば）

---

## 🧱 技術スタック（確定）

| 層 | 技術 | 要点 |
|----|------|------|
| Backend | **Java / Spring Boot 4.x** | レイヤード + ライト DDD。SB4 互換必須（`WebSecurityConfigurerAdapter`/`@MockBean` 禁止 等） |
| 認証 | **Spring Security（最小構成）** | `SecurityFilterChain` Bean、`BCryptPasswordEncoder`、セッション |
| Frontend | **TypeScript / Next.js** | フィーチャーベース、Zod バリデーション、`any` 禁止、`withBasePath()` |
| DB | **H2（インメモリ・workshop プロファイル）** | Docker 不要。スキーマは **Flyway** 管理（`ddl-auto` 禁止） |

**起動**: `npm run dev:sagemaker`（ビルド+backend:8080+frontend+プロキシ一括） → PORTS の地球儀 → URL の `ports` を **`absports`** に置換。
チェック: `npm run check:backend` / `npm run lint:frontend`。

---

## 🏛️ コード構造（確定）

**レイヤード + ライト DDD。依存は一方向。**
```
Controller → Service(interface) → Repository(interface) → Entity
```

| 判断 | 結論 |
|------|------|
| **DTO** | ✅ 使う。リクエスト/レスポンスは **`record`**。Entity と分離。手動マッピング（**MapStruct 禁止**） |
| **DAO** | ❌ 使わない。データアクセスは **Spring Data JPA Repository(interface)** に統一 |
| Service | interface + impl。`@Transactional` はここ。コンストラクタインジェクションのみ |
| Entity | `@Entity`、`@Version` 楽観ロック標準。Lombok 可。DTO は record（Lombok 不使用） |
| DDL | **Flyway**（`src/main/resources/db/migration/` に連番） |

**パッケージ（ドメイン縦割り = 機能で分担）**
```
com.example.attendance
├── common/       共通基盤: 認証, @RestControllerAdvice, エラー形式, Enum(Role/LeaveType/LeaveStatus)
├── employee/     Unit A
├── attendance/   Unit B
└── leave/        Unit C（承認ユースケース含む）
```

---

## 👥 タスク配置（Unit 分担）

| Unit | ドメイン | 担当 | Flyway | 重さ |
|------|---------|------|--------|------|
| **共通基盤（D 認証含む）** | 認証・Employee土台・共通例外・API規約・H2/Flyway土台 | **代表 1 台で先行** | V1 | — |
| A | ユーザー管理（社員 登録/一覧、role+passwordHash） | メンバー1 | V2 | 軽 |
| B | 勤怠打刻（出勤/退勤/履歴/当日状態） | メンバー2 | V3 | 中 |
| C | 休暇申請 + 承認（状態遷移・ADMIN のみ承認） | **経験者 or ペア** | V4 | **重** |

**進め方（スライド ⑥）**: ①要求 → ②設計 → ③共通基盤（全員・代表1台で main へ）→ ④各自ブランチで並列 TDD → ⑤結合 → `multi-agent-review`。
A/B/C は **Employee(+認証) にのみ依存し相互依存なし → フル並列可**。
⚠️ **Flyway 連番はチームで衝突しやすい** → V2=A/V3=B/V4=C と予約済み。

---

## 📌 確定した主要な仕様決定

- **認証**: email+password の最小ログイン。パスワードは **BCrypt ハッシュ**（平文保存/ログ出力禁止）。操作者 = ログイン中の社員。認証エラーは「メールまたはパスワードが正しくありません」
- **ロール**: `Employee.role = ADMIN | MEMBER`。**休暇の承認/却下は ADMIN のみ**
- **employeeId**: Long の DB 自動採番。email は一意（ログイン ID 兼用）
- **打刻**: サーバー現在時刻を記録（手入力なし）
- **休暇**: 状態遷移 `PENDING → APPROVED/REJECTED`
- **AWS リソース名**: 全リソースに **`Team-MIH-MSYS-Kintai`** プレフィックスを付与（S3 は小文字 `team-mih-msys-kintai-`）
- **スコープ外(YAGNI)**: 月次集計/CSV/PDF、勤怠計算、通知、監査ログ、パスワードリセット/MFA/OAuth、本格 RBAC

---

## ✅ 実装方針（確定 2026-07-14）

- **ベース案 = ou 要件仕様書**（`ou-attendance.md`）で確定。梶田案は To-Be 要求として別途参照（今日は実装しない）。
- **最小機能から実現**: D 認証 → A ユーザー管理 → B 勤怠打刻 →（後続）C 休暇+承認。
- **王（代表）の役割**:
  1. **まず共通基盤（`common/`）を作る** — 認証(D)・Employee土台・共通例外/エラー形式・Enum・H2/Flyway土台
  2. **午後: A → B を順に実装**（A を先＝ B/C が Employee に依存するため「参照される側」を先に固める）
- 詳細: [docs/requirements/implementation-strategy.md](docs/requirements/implementation-strategy.md)

## 🚦 進捗状況（現在地）

- [x] ブランチ作成・push（`feature/ou-draft-idea`）
- [x] **要求仕様 確定**（Q&A 解消済み）→ `docs/requirements/ou-attendance.md`
- [x] メンバー案（梶田）と比較分析 → `docs/working/requirements/comparison-ou-vs-kajita.md`
- [x] **実装方針 確定**（ou 案ベース / 最小機能 / 王=共通基盤→午後A→B）→ `docs/requirements/implementation-strategy.md`
- [x] docs を develop へマージ（梶田さんに共有済み）
- [x] **共通基盤の雛形＋構造を作成**（`packages/` monorepo scaffold・common/ 実体）
- [x] **共通基盤 SDD 設計書を作成**（ゲート②レビュー対象）→ `docs/design/common-foundation.md`
- [x] **共通基盤 TDD の実装計画（Plan）を作成・承認**（tdd-implementation Phase 1）→ `docs/design/common-foundation-implementation-plan.md`
- [x] **共通基盤 Phase 2（TDD 実装）完了**（起動 + 画面枠 + テスト5本緑・verify 通過）
- [x] **README.md 作成**（目標UI To-Be・起動/プレビュー手順・トラブルシュート）
- [x] **develop へマージ・push 完了**（`feature/ou-draft-idea` → `develop` を fast-forward、commit `77f7ca7`）
- [x] **共通基盤コードを `multi-agent-review`（java/ts/security/test）→ Approve**（実在ブロッカーなし・2026-07-14）
- [x] **セキュリティ積み残しを別機能に切り出し**（CSRF/CORS/H2=雛形TODO）→ `docs/design/security-hardening-todo.md`（commit `6f709b7`・develop push 済み）
- [x] **梶田さんへ共有メッセージ送付**（受け取り手順・つなぎ目・ゲート②論点）
- [x] **Unit A ブランチ作成・push**（`feature/unit-a-employee`、develop 最新から分岐）
- [x] **Unit A（ユーザー管理）を SDD `design` → TDD `tdd-implementation` で実装完了**（2026-07-14）
  - 設計(SDD)→ [docs/design/unit/a-employee-sdd-design.md](docs/design/unit/a-employee-sdd-design.md)（認可=メソッドセキュリティで確定）/ 実装計画(TDD)→ [docs/design/unit/a-employee-tdd-plan.md](docs/design/unit/a-employee-tdd-plan.md)
  - スコープ: 社員 登録/一覧/取得/編集（すべて **ADMIN のみ** = `@PreAuthorize`）。認証実体・CSRF/CORS は別 Unit
  - backend 29 tests 緑（Service10/Controller11/統合3 + 既存5）・frontend lint/build 通過
  - **共通領域の変更は 2 点のみ**: `SecurityConfig` に `@EnableMethodSecurity` / `GlobalExceptionHandler` に `AccessDeniedException→403`
- [x] **`multi-agent-review`（java/ts/security/test）→ Approve**（MEDIUM 1件=passwordHash JSON検証を取込・再テスト緑）→ [docs/design/unit/a-employee-review.md](docs/design/unit/a-employee-review.md)
- [x] **Unit A を push**（`feature/unit-a-employee`、commit `eaebe72`）
- [x] **Unit D 認証（別メンバー実装）をマージ → 結合＝統合テスト完了**（ブランチ `feature/IT_A_and_D`・2026-07-14）
  - Unit D 実体: `auth/`（AuthController・CustomUserDetailsService・login/logout/me）+ frontend `features/auth`・`/login` 画面。`SecurityConfig` は本実装（`/api/auth/login` 許可・employees=ADMIN・CSRF disable・401/403 JSON ハンドラ・H2 frameOptions sameOrigin）
  - **結合テスト** [docs/IT/auth-employee-integration.md](docs/IT/auth-employee-integration.md) / `integration/AuthEmployeeIntegrationTest`（commit `b752cd2`）: 実ログインセッションで社員 CRUD を通す **7 ケース全緑**（ADMIN 通し / MEMBER 403 / 未認証 401 / ログアウト後 401 / 不正ログイン 401 / 新社員ログイン）
  - **backend 全 48 tests 緑**（`--rerun-tasks` で裏取り済み）・frontend lint 通過
  - → 単体では 403 だった Unit A 社員 API が、認証（UserDetails=email+BCrypt + セッション）で通ることを実証
- [x] **`feature/IT_A_and_D` を push → develop マージ完了**（2026-07-14 15:30 発表前）
  - ブラウザ通し確認済み: ログイン → ダッシュボード → 社員管理（一覧/登録/編集）
  - CSRF/CORS/H2 は Unit D で確定済み（CSRF disable・H2 frameOptions sameOrigin・401/403 JSON）
- [x] **IaC（AWS CDK）実装完了** — ECS Fargate (backend + frontend) + ALB + VPC
  - CDK スタック: `packages/infra/lib/attendance-stack.ts`
  - Backend Dockerfile: `packages/backend/Dockerfile`（multi-stage, `--network=sagemaker`）
  - Frontend Dockerfile: `packages/frontend/Dockerfile`（Next.js standalone）
  - 命名: `Team-MIH-MSYS-Kintai` プレフィックス
- [x] **Unit B（勤怠打刻）** メンバー実装 → main マージ済み（2026-07-14）
- [x] **AWS デプロイ成功**（2026-07-15・ブランチ `feature/fix-frontend-dockerfile-standalone`）
  - **AppURL**: http://MIH-MSYS-Kintai-ALB-116959375.ap-northeast-1.elb.amazonaws.com
  - 疎通確認済み: `GET /`=200 / `GET /api/health`={"status":"ok"} / `GET /login`=200
  - **原因①（真因）**: monorepo で Next.js standalone が `.next/standalone/app/server.js` に出力される
    のに Dockerfile が親をコピー → `/app/app/server.js` になり `CMD node server.js` が即クラッシュ
    → Frontend が起動せず ECS Service が stabilize せず ROLLBACK。**Dockerfile のコピー元を修正**
  - **原因②**: `healthCheckGracePeriod` 未設定 + ECS container health check の二重判定で
    Ready 前に unhealthy → kill → 無限リサイクル。**grace 180s / container HC 撤去 / ALB TG 寛容化(200-399)**
  - ローカル docker で起動・`/`=200・static=200 を実証してから修正（ECS/Logs は IAM ロックで観測不能）
  - 詳細: [deploy/AWSへのデプロイ試行錯誤ノウハウ.md](deploy/AWSへのデプロイ試行錯誤ノウハウ.md) #8〜#10
  - ブラウザで ALB URL のログイン画面を表示確認済み（2026-07-15）
- [x] **デプロイ修正を develop/main へマージ・push 完了**（2026-07-15・`b2c2873`）
  - `feature/fix-frontend-dockerfile-standalone` の3コミット（Dockerfile 修正 / health check 緩和 / docs）を
    main・develop 両方へ ff マージ → origin へ push 済み（main `778d96c..b2c2873` / develop `f9c0611..b2c2873`）

### 🏁 研修終了時点の状態（2026-07-15）
- **研修は終了**。翌日（2026-07-16 予定）に本 SageMaker インスタンスが削除される見込み。
- コードは origin（GitHub `ou-uji/adsi-workshop-template`）の main/develop に全て push 済み → インスタンス削除後も残る。
- ⚠️ **AWS スタック `Team-MIH-MSYS-Kintai` は稼働したまま**（ECS Fargate ×2 + ALB + VPC）。放置すると課金継続。
  - インスタンス削除後は deploy role を assume する踏み台が失われる可能性 → **停止するなら本インスタンス生存中に delete 推奨**。
  - delete 手順: deploy role を assume → `aws cloudformation delete-stack --stack-name Team-MIH-MSYS-Kintai`（ノウハウ #5 参照）。
- **未着手**: Unit C（休暇+承認）結合、全体 `multi-agent-review`（研修範囲としては打ち切り）。

### 🧱 共通基盤の作成状況（Phase 2 完了・動く土台）
- ✅ monorepo 構造: `packages/{backend,frontend,infra}` + ルート `package.json`（scripts 実体化済み）
- ✅ common/ 実体: Enum(Role/LeaveType/LeaveStatus)・ErrorResponse(record)・GlobalExceptionHandler(@RestControllerAdvice)・例外2種(404/409)・SecurityConfig(@EnableWebSecurity・/api/health 許可)
- ✅ Flyway: `V1__init_common.sql`（employee）+ `V1_1__seed_common.sql`（**BCrypt 実ハッシュ**・全員 pw="password"）
- ✅ Employee Entity + EmployeeRepository（findByEmail/existsByEmail）
- ✅ HealthController（`GET /api/health` → `{status:"ok"}`・起動確認用の共通 API）
- ✅ テスト5本緑: contextLoads / EmployeeRepository×3(保存・email一意・空) / Health×1
- ✅ frontend: Next.js scaffold + ダッシュボード枠 + `lib/api-client.ts`(withBasePath) + HealthBadge(zod)
- ✅ SageMaker: `next.config.ts`(SAGEMAKER分岐/basePath/rewrites) + `scripts/sagemaker-proxy.mjs` + `scripts/dev-sagemaker.sh`
- ✅ 起動実証: backend(8080/H2) 起動→Flyway V1+V1.1 適用→`/api/health` 200・保護ルート403 / 全スタック(proxy3000→next3001→backend8080)で `/api/health` 疎通確認

### 🔑 SB4 で判明した重要な依存の差分（次 Unit も同様に必要）
- テストスライス分離: `@DataJpaTest`→`spring-boot-data-jpa-test`（pkg `org.springframework.boot.data.jpa.test.autoconfigure`）/ `@WebMvcTest`→`spring-boot-webmvc-test`（pkg `org.springframework.boot.webmvc.test.autoconfigure`）
- Flyway 自動設定は `spring-boot-starter-flyway`（`flyway-core` 単体では起動しない）
- `@DataJpaTest` は `@AutoConfigureTestDatabase(replace=NONE)` を付け Flyway 管理スキーマを使う（組込DB置換を無効化）
- 実測版: SB 4.0.0 / Spring Framework 7.0.1 / Security 7.0.0 / Flyway 11.14.1 / H2 2.4.240 / Java 21.0.11 / Gradle 8.14.5 / Next 16.2.10

### 📁 成果物の場所
- **README（起動手順・目標UI To-Be）**: [README.md](README.md)
- **共通基盤 設計書（SDD）**: [docs/design/common-foundation.md](docs/design/common-foundation.md) ★ゲート②レビュー対象
- **セキュリティ積み残し（別機能で実装）**: [docs/design/security-hardening-todo.md](docs/design/security-hardening-todo.md) ← SecurityConfig の CSRF/CORS/H2 は雛形 TODO。ゲート②で方針確定 → Unit A ログインで実装
- 共通基盤 コード雛形: [packages/backend/src/main/java/com/example/attendance/common/](packages/backend/src/main/java/com/example/attendance/common/)
- 確定要求: [docs/requirements/ou-attendance.md](docs/requirements/ou-attendance.md)
- 実装方針: [docs/requirements/implementation-strategy.md](docs/requirements/implementation-strategy.md)
- メンバー案（比較対象・To-Be）: [docs/requirements/kajita-attendance-management.md](docs/requirements/kajita-attendance-management.md)
- 比較レポート: [docs/working/requirements/comparison-ou-vs-kajita.md](docs/working/requirements/comparison-ou-vs-kajita.md)
- Q&A トレイル: [docs/working/requirements/attendance-draft.md](docs/working/requirements/attendance-draft.md)

### ▶️ 次にやること（研修終了・別環境で再開する場合）
> Unit A + D + B 結合済み・main マージ済み。IaC CDK 実装済み。**AWS デプロイ成功**（2026-07-15）。
> 研修は終了、本インスタンスは 2026-07-16 に削除見込み。コードは origin に push 済み。
> AppURL（スタック生存中のみ）: http://MIH-MSYS-Kintai-ALB-116959375.ap-northeast-1.elb.amazonaws.com
1. **⚠️ AWS スタック `Team-MIH-MSYS-Kintai` の後始末** — 放置すると課金継続。
   停止する場合は deploy role assume → `delete-stack`（ノウハウ #5）。インスタンス削除前に実施推奨。
2. （余力があれば）Unit C（休暇+承認）結合 → 全体 `multi-agent-review`
3. 別環境で再開する場合: `git clone` → `npm ci` → `npm run dev:sagemaker`（プレビュー）。
   デプロイは SageMaker 2 段階方式（下記）。observability 制約はノウハウ参照。

> **AWS デプロイの正しい手順（SageMaker 2 段階方式）**:
> 1. `npx cdk deploy --role-arn <deploy-role>` → PassRole エラーで止まるが **ECR push は完了**
> 2. deploy role を assume → `aws cloudformation create-stack --template-body file://cdk.out/... --role-arn <cfn-exec-role>`
> 3. `aws cloudformation wait stack-create-complete`
> 詳細: [deploy/AWSへのデプロイ試行錯誤ノウハウ.md](deploy/AWSへのデプロイ試行錯誤ノウハウ.md)

> 起動: `npm run boot:workshop`（backend単体） / `npm run dev:sagemaker`（フル・プレビュー、PORTS 3000 地球儀→`ports`を`absports`置換）
> ⚠️ 認証テストの罠（Unit A/D で判明・次 Unit も同様）: SB4 の `@WebMvcTest`/`@SpringBootTest` は `@WithMockUser` を自動適用しない →
> `webAppContextSetup(context).apply(springSecurity()).build()` で MockMvc を明示構築する（結合テストは実ログインセッション方式・詳細はメモリ `sb4-mockmvc-security-test`）。
