# CLAUDE.md — プロジェクト入口（毎セッション読み込み）

> このファイルは新しいセッションでの再開用。今日のタスク配置・技術・コード構造・進捗を記録している。
> 詳細規約は `.claude/rules/`、進め方は `.claude/skills/`、確定要求は `docs/requirements/ou-attendance.md` を参照。

---

## 🎯 今日作るもの

**勤怠管理システム（小規模デモ）** — Day 2 のチーム共同開発で「1 から作る」。
スライド ⑥「チーム開発の考え方」を実践：**機能で分担 / つなぎ目(IF)を先に合意 / 共通基盤を先行**。

- **ブランチ**: `feature/ou-draft-idea`（`develop` から分岐・push 済み）
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
- [ ] **← 今ここ: main へマージ → 梶田さんに共有 → 分担スタート**
- [ ] `work-decomposition` → `docs/units/unit_a|b|c.md`（依存図・Phase）
- [ ] 午後 A → B を TDD → 結合 → `multi-agent-review`

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
- **共通基盤 設計書（SDD）**: [docs/design/common-foundation.md](docs/design/common-foundation.md) ★ゲート②レビュー対象
- 共通基盤 コード雛形: [packages/backend/src/main/java/com/example/attendance/common/](packages/backend/src/main/java/com/example/attendance/common/)
- 確定要求: [docs/requirements/ou-attendance.md](docs/requirements/ou-attendance.md)
- 実装方針: [docs/requirements/implementation-strategy.md](docs/requirements/implementation-strategy.md)
- メンバー案（比較対象・To-Be）: [docs/requirements/kajita-attendance-management.md](docs/requirements/kajita-attendance-management.md)
- 比較レポート: [docs/working/requirements/comparison-ou-vs-kajita.md](docs/working/requirements/comparison-ou-vs-kajita.md)
- Q&A トレイル: [docs/working/requirements/attendance-draft.md](docs/working/requirements/attendance-draft.md)

### ▶️ 次にやること（新セッション）
1. （任意）`multi-agent-review` で共通基盤コードをレビュー → main へマージ
2. 共通基盤コード＋設計書を梶田さんに共有し、**ゲート②（設計承認）**レビュー（つなぎ目=employeeテーブル/Enum/API規約/Flyway連番を固定）＋設計書 §4 の `[Answer]` を埋める
3. `work-decomposition` → `docs/units/unit_a|b|c.md` → 午後 A→B を TDD

> 起動: `npm run boot:workshop`（backend単体） / `npm run dev:sagemaker`（フル・プレビュー、PORTS 3000 地球儀→`ports`を`absports`置換）
