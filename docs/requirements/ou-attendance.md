# 勤怠管理システム — 要求仕様書（確定版 / Draft レビュー待ち）

> ステータス: **要求確定・ゲート①レビュー待ち** — 2026-07-14
> 壁打ちトレイル: [../working/requirements/attendance-draft.md](../working/requirements/attendance-draft.md)
> SDD Inception 工程の成果物（→ `.claude/skills/requirements/SKILL.md`）。次工程 → `design`。

---

## 1. 概要

本日のチーム共同開発（Day 2）で **1 から作る勤怠管理システム**（小規模デモ）の要求仕様。
Day 2 スライド ⑥「チーム開発の考え方」を実践に落とす：**機能で分担 / つなぎ目(IF)を先に合意 / 共通基盤を先行**。

### 技術スタック（確定）

| 層 | 技術 | 備考 |
|----|------|------|
| **Backend** | **Java / Spring Boot 4.x** | レイヤード + ライト DDD。SB4 互換規約は `.claude/rules/java-spring-boot.md` |
| **認証** | **Spring Security（最小構成）** | `SecurityFilterChain` Bean 方式、`BCryptPasswordEncoder`、セッション。詳細 §2.1 |
| **Frontend** | **TypeScript / Next.js** | `.claude/rules/typescript-frontend.md` |
| **DB** | **H2（workshop モード, インメモリ, Docker 不要）** | スキーマは **Flyway** 管理。詳細 §1.1 |
| **実行環境** | AWS SageMaker Code Editor `ml.t3.2xlarge` / Image `code-editor-java-claude v3` | プレビューは `absports`（`sagemaker-code-editor` スキル） |

**環境方針**: 32 GiB あり軽量化不要。省メモリより「初心者メンバーが環境・デバッグで迷子にならない」を最優先 → Spring Boot（情報量・Claude 相性・レビュー容易性）。

### 1.1 DB 方針 — 何を使う / どう管理するか

> 準拠元: [.claude/skills/dev-environment/SKILL.md](../../.claude/skills/dev-environment/SKILL.md)（SageMaker は H2 workshop） / [.claude/rules/java-spring-boot.md](../../.claude/rules/java-spring-boot.md)（`ddl-auto` 禁止・Flyway）

**採用: H2（インメモリ）を workshop プロファイルで使う。** 理由 —
- SageMaker Code Editor 上では **PostgreSQL コンテナを立てない**（Docker/ネットワーク制約と初心者の環境構築コストを回避）
- インメモリ = セットアップ不要・起動が速い・`npm run boot:workshop` だけで動く → **本日デモを最優先**
- 本番相当（RDS/PostgreSQL）は本ワークショップのスコープ外。ただし**移行できる形**にしておく（下記）

**スキーマ管理（軽量だが最低限のルール）**
- **Flyway で管理**。`spring.jpa.hibernate.ddl-auto` は使わない（規約で禁止）
- マイグレーションは `src/main/resources/db/migration/` に `V1__init.sql`, `V2__...` と連番配置
- **共通基盤フェーズで `V1`（Employee + role + passwordHash）を代表 1 台が作る**。A/B/C は各自 `V2__attendance.sql` / `V3__leave.sql` を追加
- ⚠️ **Flyway の連番はチームで衝突しやすい**（同時に V2 を切ると結合で壊れる）→ §7.2「IF 先行合意」で採番ルールを先に決める（例: Unit A=V2, B=V3, C=V4 と予約）

**H2 特有の軽量運用の注意（最低限）**
- インメモリは**アプリ停止でデータ消滅**。デモは「起動 → シナリオ実演」で完結させる（永続前提にしない）
- 初期デモデータは Flyway の seed（`V*__seed.sql`）か起動時投入で用意（ADMIN 1・MEMBER 数名）
- H2 と PostgreSQL の**方言差に注意**（`SERIAL`/`IDENTITY`、大文字小文字、`BOOLEAN` 等）。将来 PG 移行を見据え、Flyway SQL は**標準寄りの型**で書く
- H2 Console は開発時のみ有効化（本番想定では無効）

**将来の PostgreSQL 移行（スコープ外・方針だけ）**
- 切り替えは **プロファイル + `application-*.yml` の datasource 差し替え**で完結する構成にする（コード非依存）
- Entity / Repository / Service は DB 非依存（JPA）なので、移行時に触るのは設定と一部 SQL 方言のみ

### アーキテクチャ / コード構造方針

> 要求レベルで**採用パターンを確定**しておく（初心者チーム＋並列分担のため、先に型を共有する）。全 API シグネチャ・ER 図・OpenAPI などの詳細は `design` 工程で正式化する。
> 準拠元: [.claude/rules/java-spring-boot.md](../../.claude/rules/java-spring-boot.md) / [.claude/rules/common/patterns.md](../../.claude/rules/common/patterns.md)

**採用: レイヤード + ライト DDD**。依存は一方向。

```
Controller → Service(interface) → Repository(interface) → Entity
```

| 層 | 使うもの | 規約 |
|----|---------|------|
| Controller | `@RestController` | HTTP 境界のみ。Repository 直接呼び出し禁止。`@Transactional` を付けない |
| Service | **interface + impl**（`EmployeeService` / `EmployeeServiceImpl`） | ドメインロジック。`@Transactional` はここ。コンストラクタインジェクションのみ |
| Repository | **Spring Data JPA `interface extends JpaRepository`** | 永続化はここに集約。複雑クエリは `@Query`(JPQL) + パラメータバインド |
| Entity | JPA `@Entity` | DB テーブル対応。`@Version` 楽観ロック標準。他レイヤーに依存しない |

**DTO / DAO の方針（← 明示）**

- **DTO は使う**: リクエスト/レスポンスは **`record`** で定義（例: `CreateEmployeeRequest`, `EmployeeResponse`）。**Entity と DTO は必ず分離**（Entity を API レスポンスに直接返さない）。マッピングは record コンストラクタで**手動**（MapStruct 禁止）。
- **DAO は使わない**: 古典的 DAO（手書き JDBC/SQL の DataAccessObject）は採用しない。データアクセスは **Spring Data JPA Repository(interface)** に統一する。← 「DAO を使うか」への回答。
- **Lombok**: Entity のみ `@Data/@Builder/@NoArgsConstructor/@AllArgsConstructor` 可。**DTO は record**（Lombok `@Data` 不使用）。
- **DDL は Flyway 管理**（`ddl-auto` 禁止）。

**パッケージ構造（ドメインで縦割り = 機能で分担）**

```
com.example.attendance
├── common/       共通基盤: @RestControllerAdvice, エラーレスポンス, Enum(Role/LeaveType/LeaveStatus)
├── employee/     Unit A: Controller / Service(+Impl) / Repository / Employee(Entity) / dto(record)
├── attendance/   Unit B: 同上（AttendanceRecord）
└── leave/        Unit C: 同上（LeaveRequest）＋ 承認ユースケース
```

> 縦割りが、スライド ⑥「機能で分担・作業範囲を重ねない」の実体。各 Unit 内に Controller〜Entity が一気通貫で収まる。

**Frontend（TypeScript / Next.js）**

- フィーチャー（ドメイン）ベースでディレクトリを分ける（`employee` / `attendance` / `leave`）
- API レスポンスは **Zod でランタイムバリデーション**、`any` 禁止（`unknown` + 型ガード）
- fetch はラッパー経由（ベース URL・エラー整形・**SageMaker basePath 対応 `withBasePath()`**）
- サーバーステートは SWR / TanStack Query。破壊的変更を避けイミュータブルに

**この構造がチーム開発に効く理由**

- ドメイン縦割り → 担当が重ならない（§7.1）
- Service / Repository の **interface が「IF 先行合意」の実体**（§7.2）。impl を各自が並列で埋める
- record DTO のイミュータビリティ → 初心者でも副作用バグを踏みにくい

---

## 2. スコープ（MVP・3 ドメイン）

| # | ドメイン | 内容 | 重さ | 想定担当 |
|---|---------|------|------|---------|
| D | 認証（最小） | email+password ログイン / ログアウト / role 判定 | 中 | **共通基盤に含める**（代表 1 台で先行） |
| A | ユーザー管理 | 社員の登録・一覧（role + passwordHash 付き） | 軽 | メンバー1 |
| B | 勤怠打刻 | 出勤・退勤打刻、当日状態、履歴一覧 | 中 | メンバー2 |
| C | 休暇申請 + 承認 | 申請・一覧 + ADMIN の承認/却下（状態遷移） | **重** | 経験者 or ペア |

> **D（認証）は独立 Unit にせず共通基盤に入れる**。全機能がログイン前提で動くため、A/B/C 分担の前に代表 1 台で main に用意する（§7.1）。Employee に `passwordHash` が乗るので A と密結合 → 土台側で確定させるのが安全。

**スコープ外（YAGNI・後回し）**: 月次集計/CSV/PDF、勤怠計算（丸め・残業・深夜）、通知、監査ログ、多言語、本格 RBAC、パスワードリセット/MFA/OAuth。

---

## 3. 確定した仕様上の決定（Q&A の結論）

| 論点 | 決定 |
|------|------|
| 認証・ログイン | **最小デモを作る**（email + password ログイン）。操作者 = ログイン中の社員。詳細は §2.1 |
| パスワード | **BCrypt でハッシュ化**（平文保存禁止）。登録時にハッシュ、ログイン時に照合 |
| ロール | **最小導入**: `Employee.role = ADMIN | MEMBER`。ログイン中ユーザーの role で承認可否・画面を出し分け |
| employeeId | **Long の DB 自動採番**。表示用社員コードは持たない |
| メール | **一意制約あり**（ログイン ID を兼ねる。重複はエラー） |
| 打刻時刻 | **サーバー現在時刻**を記録（手入力補正なし） |
| 休暇承認フロー | **スコープ内**: `PENDING → APPROVED/REJECTED`、遷移は **ADMIN のみ**（ログイン role で判定） |
| 画面範囲 | 各ドメイン「一覧 + 入力フォーム」。ログイン画面 + 休暇の ADMIN 承認画面を追加 |
| デモゴール | ログイン → 打刻 → 履歴 → 休暇1件申請 をブラウザ(absports)で通し実演。承認実演は余力があれば |

### 2.1 認証（最小デモ）— 決定詳細

> ⚠️ **設計変更点**: 以前の「操作者をドロップダウンで選択」は廃止。**ログイン中の社員が操作者**になる。承認フロー（ADMIN のみ）とも自然に整合する。

**作るもの（最小）**
- email + password でログインする画面 / API
- パスワードは **BCrypt ハッシュ**で保存（`Employee.passwordHash`）。平文保存・ログ出力は禁止（`.claude/rules/security.md`）
- ログイン成功でセッション確立。ログアウトあり
- ログイン中ユーザーの `role` で画面・操作を出し分け（MEMBER は承認ボタン非表示、承認 API は ADMIN のみ）

**割り切り（スコープ外 = YAGNI）**
- パスワードリセット / メール確認 / 「パスワードを忘れた」 / 多要素認証
- OAuth・外部 IdP・JWT の作り込み（セッションで十分）
- 細かな権限マトリクス（RBAC は ADMIN/MEMBER の2値のみ）
- アカウントロック・ログイン試行回数制限

**実装方針**
- **Spring Security は最小構成**（`SecurityFilterChain` Bean 方式。`WebSecurityConfigurerAdapter` 禁止 = SB4 互換）
- パスワードエンコーダは `BCryptPasswordEncoder`
- 認証エラーメッセージは「メールまたはパスワードが正しくありません」（どちらが誤りか特定させない / `.claude/rules/security.md`）
- 保護ルートは**デフォルト拒否 + ホワイトリスト**（ログイン API と静的資産のみ許可）

> ※ この認証は「共通基盤」に含める（§7.1）。A/B/C の各機能より先に、代表 1 台で main に用意する。

---

## 4. ユーザーストーリー

### D: 認証（最小）
- **US-D1** 社員として、自分として操作するため、email + password で**ログイン**したい。
- **US-D2** 社員として、作業を終えるため、**ログアウト**したい。
- **US-D3** システムとして、承認など権限操作を守るため、**ログイン中ユーザーの role で操作可否を判定**したい（ADMIN のみ承認可）。

### A: ユーザー管理
- **US-A1** 管理者として、勤怠記録対象を用意するため、社員を登録したい（氏名・メール・**初期パスワード**・role）。
- **US-A2** 管理者として、対象確認のため、社員一覧を表示したい。
- **US-A3**（任意）誤登録修正のため、社員情報を編集したい。

### B: 勤怠打刻
- **US-B1** 社員として、勤務開始記録のため、出勤打刻したい（当日・未打刻時のみ）。
- **US-B2** 社員として、勤務終了記録のため、退勤打刻したい（当日・出勤済時のみ）。
- **US-B3** 社員として、勤務状況確認のため、自分の打刻履歴を見たい。
- **US-B4**（任意）当日状態（未/出勤中/退勤済）を見たい。

### C: 休暇申請 + 承認
- **US-C1** 社員として、休暇取得のため、休暇を申請したい（日付・種別・理由）。
- **US-C2** 社員として、状況確認のため、自分の申請一覧を見たい。
- **US-C3** 管理者(ADMIN)として、休暇を認める/断るため、申請を承認/却下したい。

---

## 5. 受け入れ条件（代表例 — TDD の @DisplayName 粒度）

**US-D1 ログイン**
- [ ] 正しい email + password でログインでき、以降その社員が操作者になる
- [ ] 誤った password では失敗し、「メールまたはパスワードが正しくありません」を返す（どちらが誤りか特定させない）
- [ ] パスワードは DB に BCrypt ハッシュで保存され、平文・ハッシュともログに出力されない
- [ ] 未ログインで保護 API を叩くと 401/リダイレクト（デフォルト拒否）

**US-B1 出勤打刻**
- [ ] 当日未打刻の社員が打刻すると出勤時刻が記録される
- [ ] 同一社員が当日 2 回目の出勤打刻をするとエラー（409 相当）
- [ ] 存在しない社員 ID で打刻すると 404

**US-B2 退勤打刻**
- [ ] 出勤済みの社員が退勤打刻すると退勤時刻が記録される
- [ ] 出勤していない社員が退勤打刻するとエラー

**US-A1 社員登録**
- [ ] 氏名・メール必須、未入力はフィールド単位のバリデーションエラー
- [ ] メール形式不正はエラー / メール重複は 409

**US-C1 休暇申請**
- [ ] 日付・種別必須。申請後、一覧に PENDING で表示される

**US-C3 承認/却下（ADMIN のみ）**
- [ ] ADMIN が PENDING を承認すると APPROVED になる
- [ ] ADMIN が PENDING を却下すると REJECTED になる
- [ ] APPROVED/REJECTED を再遷移しようとするとエラー（不正な状態遷移）
- [ ] MEMBER は承認/却下できない（403 相当 / 画面で不可）

> エラーはフィールド名 + メッセージで返す。内部エラー詳細はクライアントに返さない（`.claude/rules/security.md`）。

---

## 6. ドメインモデル素案（設計への橋渡し）

| Entity | 主な属性 | ドメイン |
|--------|---------|---------|
| `Employee` | id(Long), name, email(一意=ログインID), **passwordHash**, **role** | A / D（共有の土台） |
| `AttendanceRecord` | id, employeeId, workDate, clockInAt, clockOutAt | B |
| `LeaveRequest` | id, employeeId, type, startDate, endDate, reason, **status** | C |

**Enum**: `Role`(ADMIN/MEMBER) / `LeaveType`(有給/欠勤/特別…) / `LeaveStatus`(PENDING/APPROVED/REJECTED)
**共有の核** = `Employee`（employeeId）。B/C とも参照する（スライドの「共有テーブル」）。

---

## 7. チーム開発のつなぎ目（スライド ⑥ の実践）

### 7.1 共通基盤（全員・代表 1 台で先に作り切る）
- [ ] Spring Boot + Next.js 雛形（`npm run setup` が通る）
- [ ] `Employee` Entity / テーブル（Flyway **V1**、role + **passwordHash** カラム含む）
- [ ] 共有 Enum（Role / LeaveType / LeaveStatus）
- [ ] **認証（ドメイン D）**: Spring Security 最小構成、ログイン/ログアウト、BCrypt、role 判定、保護ルート
- [ ] **DB/Flyway 土台**: H2 workshop 設定、`db/migration/` と連番ルール、seed（ADMIN 1・MEMBER 数名）
- [ ] `@RestControllerAdvice` の共通例外ハンドリング + エラーレスポンス形式
- [ ] API ベース規約（パス命名、日付/時刻フォーマット、認証/ログインユーザーの取得方法）
- [ ] 起動・H2・テスト（`npm run boot:workshop` / `npm run check:backend`）が回る

### 7.2 事前合意する IF（固めすぎない・変えたら即共有）
- [ ] 共有データモデル: Employee（id=Long, email 一意=ログインID, passwordHash, role）、共有 Enum
- [ ] **Flyway 連番の予約**: V1=共通基盤(Employee)、**V2=Unit A / V3=Unit B / V4=Unit C** と割当（連番衝突の回避）
- [ ] API 形状: パス、DTO(record)、エラーの返し方、ISO-8601、**ログインユーザーの取得方法**（認証コンテキスト）
- [ ] 仕様の範囲: 当日重複打刻 / 不正な状態遷移 / 権限外の承認操作（ADMIN 以外）
- [ ] 状態遷移: 打刻（未→出勤中→退勤済）、休暇（PENDING→APPROVED/REJECTED、ADMIN のみ）

### 7.3 Unit 依存と Phase（→ work-decomposition で正式化）
```
Phase A（共通基盤・全員1台）: 認証(D) + Employee + Role + passwordHash + 共通例外 + API規約 + H2/Flyway土台
        │  ログインが全機能の前提。A/B/C は Employee(+認証) にのみ依存し、相互依存なし → フル並列可
        ├─ Unit A: ユーザー管理   （メンバー1）  Flyway V2
        ├─ Unit B: 勤怠打刻       （メンバー2）  Flyway V3
        └─ Unit C: 休暇申請+承認   （経験者/ペア）Flyway V4
最後（全員1台）: main に集約 → 結合 → 通し確認 → multi-agent-review
```

---

## 8. ゲート①チェックリスト

- [x] ユーザーストーリー確定
- [x] 仕様上の決定（Q&A）解消
- [x] スコープ（3 ドメイン MVP）合意
- [x] 共通基盤・IF の骨格を列挙
- [ ] **人間レビュー承認**（あなた）← ここ待ち
- [ ] GitHub Issue に要約コメント

## 9. 次のステップ
1. 本書をレビュー・承認（ゲート①）
2. GitHub Issue に要約 + 本書へのリンクをコメント（`.claude/rules/common/issue-workflow.md`）
3. `design` → ドメイン / DB(Flyway) / API(OpenAPI) / 画面
4. `work-decomposition` → `docs/units/unit_a|b|c.md` + README（依存図・Phase。認証 D は共通基盤側）
5. 共通基盤を代表 1 台で構築 → 各自ブランチで並列 TDD → 結合 → `multi-agent-review`
