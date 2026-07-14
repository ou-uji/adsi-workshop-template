# 基本設計書 — 共通基盤（Unit 0）

> SDD 設計フェーズ（AI-DLC Construction 入口）の成果物。→ `.claude/skills/design/SKILL.md`
> 作成: 2026-07-14 / ベース要求: [../requirements/ou-attendance.md](../requirements/ou-attendance.md) / 方針: [../requirements/implementation-strategy.md](../requirements/implementation-strategy.md)
>
> **位置づけ**: スライド【設計】の実践。共通基盤を **「最初の Unit（Unit 0）」として切り出した**設計書。
> これは **ゲート②（設計承認）** でチーム全員がレビューし、**つなぎ目（API・共有テーブル・共通 UI）を先に固定**する対象。
>
> ステータス: **DRAFT（設計承認レビュー待ち）** — 対応コード雛形は `packages/backend/.../common/` に配置済み。

---

## 0. この設計書のスコープ

共通基盤 = A/B/C の全 Unit が乗る土台。**分担して並行実装する前に、代表 1 台で先に作り切る**部分。

| 含む（共通基盤 = Unit 0） | 含まない（各 Unit で実装） |
|---------------------------|----------------------------|
| Employee Entity / テーブル（共有の核・Flyway V1） | Employee の CRUD API 本体（Unit A / V2） |
| 共有 Enum（Role / LeaveType / LeaveStatus） | 打刻ロジック（Unit B / V3） |
| 共通例外ハンドリング（`@RestControllerAdvice`）+ エラー形式 | 休暇・承認ロジック（Unit C / V4） |
| 認証（D）: Spring Security 最小・BCrypt・保護ルート | 各ドメインの画面（features/*） |
| DB/Flyway 土台（H2 workshop・連番ルール・seed） | |
| API ベース規約（パス・日時・エラー・操作者取得） | |

> **なぜ共通基盤を最初の Unit にするか**（スライド）: 「テストまで実行できる共通基盤は最初の Unit として切り出す」。ここが動く土台になり、以降の Unit が並列で乗る。

---

## 1. 固定するつなぎ目（ゲート② の合意対象）★最重要

スライド「つなぎ目（API・共有テーブル・共通 UI）を先に固定」に対応。**ここを全員で合意してから分かれる。**

### 1.1 共有テーブル: `employee`

A/B/C 全てが `employee.id`（= employeeId）を参照する共有の核。**この形を先に固定する。**

| カラム | 型 | 制約 | 備考 |
|--------|----|------|------|
| id | BIGINT | PK, IDENTITY | employeeId。自動採番（Long） |
| name | VARCHAR(100) | NOT NULL | 氏名 |
| email | VARCHAR(255) | NOT NULL, UNIQUE | ログイン ID 兼用。重複は 409 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt ハッシュ（平文保存禁止） |
| role | VARCHAR(20) | NOT NULL | ADMIN / MEMBER（Enum: Role） |
| version | BIGINT | NOT NULL DEFAULT 0 | 楽観ロック（`@Version`） |
| created_at / updated_at | TIMESTAMP | NOT NULL | 監査の素地 |

→ 実体: [`V1__init_common.sql`](../../packages/backend/src/main/resources/db/migration/V1__init_common.sql)

### 1.2 共有 Enum（型の定義）

| Enum | 値 | 使う場所 |
|------|----|---------| 
| `Role` | ADMIN / MEMBER | 認証・承認可否 |
| `LeaveType` | PAID / ABSENCE / SPECIAL | 休暇申請（C） |
| `LeaveStatus` | PENDING / APPROVED / REJECTED | 休暇の状態遷移（C） |

→ 実体: `packages/backend/.../common/enums/`

### 1.3 API の形状（全 Unit 共通規約）

- **パス命名**: `/api/{domain}/...`（例: `/api/employees`, `/api/attendance/clock-in`, `/api/leaves`）
- **認証**: `/api/auth/login`, `/api/auth/logout`。ログインユーザーは認証コンテキストから取得（各 API で employeeId を body に手渡ししない）
- **リクエスト/レスポンス**: すべて `record` DTO。Entity を直接返さない
- **日時フォーマット**: ISO-8601（`OffsetDateTime` / `LocalDate`）
- **エラー**: 下記 §3 の共通形式。フィールドエラーは `field` + `message`
- **HTTP ステータス**: 200/201（成功）, 400（バリデーション）, 401（未認証）, 403（権限外）, 404（未存在）, 409（業務ルール違反）

### 1.4 共通 UI（frontend のつなぎ目）

- `lib/api-client.ts` の **`withBasePath()`** を全 fetch に適用（SageMaker basePath 対応）
- ログイン状態・操作者（role）を保持する共通の仕組み（各 feature が参照）
- エラーレスポンスをユーザー向けメッセージに変換する共通ハンドラ

### 1.5 Flyway 連番の予約（衝突回避の合意）

| 番号 | 担当 | 内容 |
|------|------|------|
| V1 / V1.1 | 共通基盤 | employee テーブル + seed |
| V2 | Unit A | （ユーザー管理で追加分があれば） |
| V3 | Unit B | attendance_record |
| V4 | Unit C | leave_request |

> 「他人の Unit は触らない」= 自分の番号以外の migration を足さない。これを合意してから分かれる。

---

## 2. ドメインモデル（ライト DDD）

共通基盤が持つのは **Employee** と **共有 Enum** のみ（B/C の Entity は各 Unit）。

```
Employee (Entity, 共有の核)
 ├─ id: Long (employeeId)
 ├─ name / email(unique) / passwordHash
 └─ role: Role(ADMIN|MEMBER)

Role / LeaveType / LeaveStatus (共有 Enum — common/enums)
```

- **依存方向**: `Controller → Service(interface) → Repository(interface) → Entity`
- **DTO = record 使う / DAO 使わない**（Spring Data JPA Repository に統一）
- Entity は Lombok 可、DTO は record、DDL は Flyway（`ddl-auto` 禁止）

---

## 3. 共通例外ハンドリング / エラー形式

`@RestControllerAdvice` に集約（→ `common/exception/GlobalExceptionHandler.java`）。

| 例外 | HTTP | 用途 |
|------|------|------|
| `ResourceNotFoundException` | 404 | 存在しない社員 ID 等 |
| `BusinessRuleViolationException` | 409 | 重複打刻・メール重複・不正な状態遷移 |
| `MethodArgumentNotValidException` | 400 | Bean Validation（フィールド単位） |
| その他 | 500 | 詳細はログのみ、クライアントには汎用メッセージ |

**エラーレスポンス形式**（`ErrorResponse` record）:
```json
{
  "timestamp": "2026-07-14T10:00:00+09:00",
  "status": 409,
  "error": "Conflict",
  "message": "本日は既に出勤打刻済みです",
  "path": "/api/attendance/clock-in",
  "fieldErrors": []
}
```
- 内部エラー詳細（スタックトレース・SQL）は返さない（`security.md`）
- バリデーションは `fieldErrors: [{ "field": "email", "message": "..." }]`

---

## 4. 認証設計（最小構成 = Unit D）

→ 実体: `common/config/SecurityConfig.java`（雛形）

- **方式**: Spring Security（`SecurityFilterChain` Bean、SB4 互換）
- **パスワード**: `BCryptPasswordEncoder` でハッシュ（平文保存・ログ出力禁止）
- **保護ルート**: デフォルト拒否 + ホワイトリスト（ログイン API・H2 コンソール・静的資産のみ許可）
- **操作者 = ログイン中の社員**。role で承認可否・画面を出し分け（ADMIN のみ休暇承認）
- **認証エラー**: 「メールまたはパスワードが正しくありません」（どちらの誤りか特定させない）

### [Question] / [Answer]（設計承認前に決める）

```
[Q1] ログイン方式はフォームログイン（セッション）で確定してよいか？（JWT は使わない前提）
[Answer]

[Q2] H2 コンソールは workshop のみ有効・保護対象外で確定か？（本番想定では無効）
[Answer]

[Q3] 操作者(role)の frontend への渡し方は「ログイン時に /api/auth/me で取得」で良いか？
[Answer]

[Q4] CORS は同一オリジン（Next rewrites 経由）前提で、追加設定なしで良いか？
[Answer]
```

> 未回答の `[Answer]` があるうちは、それに依存する実装に進まない（SDD）。

---

## 5. DB / 起動土台

- **workshop = H2 インメモリ**（Docker 不要、`--spring.profiles.active=workshop`）→ `application-workshop.yml`
- スキーマは **Flyway**（`db/migration/`）。`ddl-auto: none`
- **seed**: ADMIN 1・MEMBER 2（`V1_1__seed_common.sql`。BCrypt 実ハッシュは実装時に差し替え）
- H2 特有: 停止でデータ消滅前提・PG 移行を見据え標準寄りの型・Console は開発時のみ

---

## 6. 対応コード（雛形）— レビュー時に併読

```
packages/backend/src/main/java/com/example/attendance/
├── AttendanceApplication.java          # エントリポイント
└── common/
    ├── config/SecurityConfig.java      # 認証（最小・雛形）
    ├── enums/{Role,LeaveType,LeaveStatus}.java
    ├── error/ErrorResponse.java        # エラー形式（record）
    └── exception/
        ├── GlobalExceptionHandler.java # @RestControllerAdvice
        ├── ResourceNotFoundException.java     # 404
        └── BusinessRuleViolationException.java # 409
packages/backend/src/main/resources/
├── application.yml / application-workshop.yml
└── db/migration/V1__init_common.sql / V1_1__seed_common.sql
```

各ドメインパッケージ（employee/attendance/leave）は `package-info.java` で担当・配置・Flyway 番号を明示。

---

## 7. 完了条件（ゲート②）

- [ ] つなぎ目（§1: employee テーブル / Enum / API 規約 / 共通 UI / Flyway 連番）に全員合意
- [ ] §4 の `[Answer]` がすべて埋まっている
- [ ] エラー形式・例外→HTTP のマッピングに合意
- [ ] 「他人の Unit は触らない」を合意
- [ ] **設計承認（人間のゲート）** → この後 `work-decomposition` で A/B/C を Unit 化

## 8. 次のステップ
1. 本設計書 + common コードを梶田さんに共有し、ゲート②レビュー
2. `[Answer]` を埋めて設計確定
3. `work-decomposition` → `docs/units/unit_a|b|c.md`（依存図・Phase）
4. 共通基盤を「テストまで通る」状態に仕上げ → 各 Unit を並列 TDD
