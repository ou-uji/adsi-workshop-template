# 基本設計書 — Unit A: ユーザー管理（社員 登録/一覧/編集）

> SDD 設計フェーズ（AI-DLC Construction 入口）の成果物。→ [.claude/skills/design/SKILL.md](../../../.claude/skills/design/SKILL.md)
> 作成: 2026-07-14 / 担当: メンバー1（王が設計を先行）/ ブランチ: `feature/unit-a-employee`
> ベース要求: [../../requirements/ou-attendance.md](../../requirements/ou-attendance.md) / 方針: [../../requirements/implementation-strategy.md](../../requirements/implementation-strategy.md)
> 土台: [../common-foundation.md](../common-foundation.md)（Employee テーブル・Enum・例外・SecurityConfig 雛形は実装済み）
>
> ステータス: **DRAFT（設計レビュー待ち）**。この後 `tdd-implementation` で Plan → 承認 → Red-Green-Refactor。

---

## 0. スコープと決定サマリ（Q&A の結論）

「1 Unit = 1 PR / 触るのは自分の Unit のフォルダだけ / 相手の Unit はモックで代用」（実装スライド）を前提に設計する。

| 論点 | 決定 | 出典 |
|------|------|------|
| Unit A の範囲 | **社員管理（登録 / 一覧 / 編集）のみ**。認証の実体（ログイン/ログアウト/`/me`）と SecurityConfig の CSRF/CORS 確定は **含めない**（別ユニット） | 王の指示 2026-07-14 |
| 認証方式（依存先の前提） | **独自 JSON ログイン + CSRF 無効**（セッション前提・JWT 不使用）。Unit A はこれに依存するが実装はしない | 王の指示 |
| 登録の認可 | **ADMIN のみ**（US-A1「管理者として登録」に忠実） | 王の指示 |
| 一覧の認可 | **ADMIN のみ** | 王の指示 |
| 編集（US-A3） | **含める**。`PUT /api/employees/{id}`（ADMIN のみ） | 王の指示 |

### 含む / 含まない

| 含む（Unit A で実装） | 含まない（別ユニット or YAGNI） |
|----------------------|--------------------------------|
| `employee/` パッケージ: Controller / Service(+Impl) / dto(record) | 認証の実体（`/api/auth/login`・`/logout`・`/me`）→ 別ユニット |
| 登録（初期パスワードを BCrypt ハッシュ化して保存） | SecurityConfig の CSRF/CORS/H2 frameOptions 確定 → 別ユニット |
| 一覧 / ID 取得 / 編集 | パスワード変更・リセット・MFA（YAGNI） |
| ADMIN のみの認可（`@PreAuthorize`） | 削除（論理/物理）（今日は不要 → YAGNI） |
| frontend `features/employee/`（一覧 + 登録/編集フォーム） | ページネーション・検索・ソート（デモは数件 → YAGNI・§5 に注記） |
| backend の TDD（Repository/Service/Controller/統合） | 月次集計・CSV 等 |

---

## 1. 依存とつなぎ目（並行実装の境界）★最重要

Unit A は **Employee（共有の核）と認証にのみ依存**し、B/C とは相互依存しない（フル並列可）。

### 1.1 Unit A が「所有」するもの（このフォルダだけ触る）

```
packages/backend/.../employee/          ← Unit A 所有（Entity/Repository は土台由来だが同フォルダ）
  ├── EmployeeController.java           （新規）
  ├── EmployeeService.java / Impl       （新規）
  ├── dto/ CreateEmployeeRequest 等      （新規・record）
  ├── Employee.java                     （土台実装済み。§2.1 の updateProfile を追加＝列は変えない）
  └── EmployeeRepository.java           （土台実装済み。findByEmail/existsByEmail をそのまま使う）
packages/frontend/src/features/employee/ ← Unit A 所有（新規）
packages/frontend/src/app/employees/     ← Unit A 所有（新規ルート）
```

### 1.2 Unit A が「依存」するもの（土台・共通。原則変更しない）

| 依存先 | 使い方 | 状態 |
|--------|--------|------|
| `employee.Employee`（Entity） | 生成 `new Employee(name,email,hash,role)` / §2.1 の更新メソッド | 実装済み |
| `employee.EmployeeRepository` | `save` / `findAll` / `findById` / `existsByEmail` | 実装済み |
| `common.enums.Role` | `ADMIN` / `MEMBER` | 実装済み |
| `common.exception.*` + `GlobalExceptionHandler` | `ResourceNotFoundException`(404) / `BusinessRuleViolationException`(409) を throw するだけ | 実装済み |
| `common.error.ErrorResponse` | エラー整形は共通ハンドラ任せ（Unit A は例外を投げるだけ） | 実装済み |
| `PasswordEncoder`（`SecurityConfig` の Bean） | 登録時に `encode(rawPassword)` | 実装済み |
| frontend `lib/api-client`（`apiFetch`/`withBasePath`/`ApiError`） | 全 fetch をこれ経由 | 実装済み |
| **認証コンテキスト（ログイン中の ADMIN）** | 認可の判定に使う | **別ユニット未実装 → テストは `@WithMockUser` でモック** |

### 1.3 チーム合意が要る 1 点（つなぎ目 = 変えるなら実装前に相談）

**認可の実現方式**: 共有の `SecurityConfig` にパスごとの `hasRole` を足す方式は「他人のフォルダを触る」ため採らない。
代わりに **メソッドセキュリティ**を採用する:

- Unit A のコントローラに `@PreAuthorize("hasRole('ADMIN')")` を付ける（**認可が守る対象と同じ場所に書ける**＝読みやすい）
- 有効化に必要なのは **`@EnableMethodSecurity` の 1 行だけ**。これは共通側（`SecurityConfig` か `common/` の小さな Config）に置く
- この型は **Unit C の「休暇承認は ADMIN のみ」でもそのまま再利用できる** → チーム共通の認可パターンにする

```
[Question] 認可はメソッドセキュリティ（@PreAuthorize + @EnableMethodSecurity を common に1行）で確定してよいか？
           （代替: SecurityConfig にパスごとの hasRole。ただし共有ファイルの取り合いが増える）
[Answer]   確定（2026-07-14・王）。メソッドセキュリティを採用する。
           - employee コントローラに @PreAuthorize("hasRole('ADMIN')") を付ける
           - 有効化の @EnableMethodSecurity は共通側（SecurityConfig）に1行だけ足す（唯一の共有変更）
           - 理由: 認可を守る対象と同じ場所に書けて読みやすく、Unit C の「休暇承認=ADMIN のみ」にも同じ型を再利用できる
```

> ステータス: **設計確定（2026-07-14）** — 上記 `[Answer]` の確定によりゲートを通過。`tdd-implementation` へ。

#### 実装で判明した追記（2026-07-14・SDD の齟齬フィードバック）

`@EnableMethodSecurity` を入れると、`@PreAuthorize` 拒否時に **`AuthorizationDeniedException`（`AccessDeniedException` のサブクラス）** が投げられる。
共通 `GlobalExceptionHandler` はこれを未ハンドルだったため `handleUnexpected` が **500** に変換していた（MEMBER アクセスが 403 でなく 500 になる）。
→ 共通ハンドラに **`AccessDeniedException → 403`** のマッピングを 1 つ追加した。これは `@EnableMethodSecurity` とセットで必要な共通処理であり、
**Unit C の「休暇承認=ADMIN のみ」でも同じ 403 変換が効く**。共通変更は結果的に「①`@EnableMethodSecurity` ②403 ハンドラ」の 2 点になった（いずれも共通基盤の穴埋めで、パスごとの認可は書いていない）。

> `@EnableMethodSecurity` を入れるまでは実 API での 403 強制はかからない。ただし **Unit A のテストは `@WithMockUser(roles=...)` で principal を注入して緑にできる**ため、認証ユニットの完成を待たずに TDD 可能（スライド「相手の Unit はモックで代用」）。

---

## 2. ドメインモデル（ライト DDD）

共有の核 `Employee` を再利用する。Unit A は **新しい Entity を作らない**（B/C と違い、A は Employee そのものが対象ドメイン）。

```
Employee (Entity, 土台実装済み)
 ├─ id: Long (IDENTITY 採番)
 ├─ name / email(unique=ログインID) / passwordHash(BCrypt) / role: Role
 ├─ version(@Version) / createdAt / updatedAt(@PrePersist/@PreUpdate)
 └─ + updateProfile(name, email, role)   ← §2.1 で追加（列・テーブルは変えない）
```

### 2.1 Employee への追加（列・つなぎ目は不変）

編集のため、更新用のドメインメソッドを **1 つ**追加する（setter は付けない＝イミュータビリティ規約）。

```java
/** プロフィール（氏名・email・role）を更新する。passwordHash は変更しない（パスワード変更は YAGNI）。 */
public void updateProfile(String name, String email, Role role) {
    this.name = name;
    this.email = email;
    this.role = role;
}
```

- **列・テーブル定義（V1）は変更しない** → B/C・認証との共有テーブルのつなぎ目は不変（安全）
- パスワード更新は含めない（スコープ外）。編集対象は name / email / role のみ

### 2.2 DTO（すべて `record` / Entity を直接返さない）

| DTO | 用途 | フィールド | バリデーション |
|-----|------|-----------|---------------|
| `CreateEmployeeRequest` | 登録 req | `name, email, password, role` | 下記 §3.3 |
| `UpdateEmployeeRequest` | 編集 req | `name, email, role`（password なし） | 下記 §3.3 |
| `EmployeeResponse` | 応答 | `id, name, email, role, createdAt, updatedAt` | — |

- **`EmployeeResponse` に `passwordHash` を絶対に含めない**（漏洩防止 / security.md）
- マッピングは手動（MapStruct 禁止）。`EmployeeResponse.from(Employee)` の静的ファクトリで集約

---

## 3. API 設計（OpenAPI）

### 3.1 エンドポイント一覧

| メソッド | パス | 用途 | 認可 | 成功 | 主なエラー |
|---------|------|------|:---:|:---:|-----------|
| POST | `/api/employees` | 社員登録 | ADMIN | 201 + `EmployeeResponse` | 400(検証) / 409(email 重複) |
| GET | `/api/employees` | 社員一覧 | ADMIN | 200 + `EmployeeResponse[]` | — |
| GET | `/api/employees/{id}` | 1件取得（編集フォーム初期表示） | ADMIN | 200 + `EmployeeResponse` | 404 |
| PUT | `/api/employees/{id}` | 社員編集 | ADMIN | 200 + `EmployeeResponse` | 400 / 404 / 409(他者と email 重複) |

- 未認証は **401**、ADMIN 以外（MEMBER）は **403**（共通の認可）
- パス命名・日時（ISO-8601）・エラー形式は共通規約（[common-foundation.md §1.3](../common-foundation.md)）に準拠

### 3.2 OpenAPI 定義（抜粋・Swagger UI 確認用）

```yaml
openapi: 3.0.3
info:
  title: 勤怠管理 — Unit A ユーザー管理 API
  version: 0.1.0
paths:
  /api/employees:
    post:
      summary: 社員を登録する（ADMIN のみ）
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CreateEmployeeRequest' }
      responses:
        '201': { description: 作成成功, content: { application/json: { schema: { $ref: '#/components/schemas/EmployeeResponse' } } } }
        '400': { description: バリデーションエラー（fieldErrors） }
        '401': { description: 未認証 }
        '403': { description: ADMIN 以外 }
        '409': { description: email 重複 }
    get:
      summary: 社員一覧（ADMIN のみ）
      responses:
        '200': { description: 一覧, content: { application/json: { schema: { type: array, items: { $ref: '#/components/schemas/EmployeeResponse' } } } } }
        '401': { description: 未認証 }
        '403': { description: ADMIN 以外 }
  /api/employees/{id}:
    get:
      summary: 社員1件取得（ADMIN のみ）
      parameters: [{ name: id, in: path, required: true, schema: { type: integer, format: int64 } }]
      responses:
        '200': { description: 取得成功, content: { application/json: { schema: { $ref: '#/components/schemas/EmployeeResponse' } } } }
        '404': { description: 未存在 }
    put:
      summary: 社員を編集する（ADMIN のみ）
      parameters: [{ name: id, in: path, required: true, schema: { type: integer, format: int64 } }]
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/UpdateEmployeeRequest' }
      responses:
        '200': { description: 更新成功, content: { application/json: { schema: { $ref: '#/components/schemas/EmployeeResponse' } } } }
        '400': { description: バリデーションエラー }
        '404': { description: 未存在 }
        '409': { description: 他社員と email 重複 }
components:
  schemas:
    CreateEmployeeRequest:
      type: object
      required: [name, email, password, role]
      properties:
        name:     { type: string, maxLength: 100 }
        email:    { type: string, format: email, maxLength: 255 }
        password: { type: string, minLength: 8, maxLength: 72 }  # BCrypt は 72 バイトまで
        role:     { type: string, enum: [ADMIN, MEMBER] }
    UpdateEmployeeRequest:
      type: object
      required: [name, email, role]
      properties:
        name:  { type: string, maxLength: 100 }
        email: { type: string, format: email, maxLength: 255 }
        role:  { type: string, enum: [ADMIN, MEMBER] }
    EmployeeResponse:
      type: object
      properties:
        id:        { type: integer, format: int64 }
        name:      { type: string }
        email:     { type: string }
        role:      { type: string, enum: [ADMIN, MEMBER] }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
      # passwordHash は返さない（漏洩防止）
```

### 3.3 バリデーション（Bean Validation / 400 は共通ハンドラが fieldErrors で返す）

| フィールド | 制約 | 備考 |
|-----------|------|------|
| name | `@NotBlank` `@Size(max=100)` | |
| email | `@NotBlank` `@Email` `@Size(max=255)` | 形式 + 長さ。重複(409)は Service で判定 |
| password（登録のみ） | `@NotBlank` `@Size(min=8, max=72)` | seed の "password"(8字) と整合。BCrypt 72 バイト上限 |
| role | `@NotNull` | Enum バインド失敗は 400 |

---

## 4. サービス設計（ロジックと例外）

`EmployeeService`(interface) + `EmployeeServiceImpl`。`@Transactional` は Impl に付与（コンストラクタインジェクションのみ）。

| メソッド | ロジック | 例外 → HTTP |
|---------|---------|-------------|
| `create(CreateEmployeeRequest)` | `existsByEmail` で重複判定 → `encode(password)` → `save` → `EmployeeResponse.from` | 重複: `BusinessRuleViolationException`(409) |
| `findAll()` | `repository.findAll()` を `EmployeeResponse` に写像 | — |
| `findById(Long)` | `findById().orElseThrow` | 未存在: `ResourceNotFoundException`(404) |
| `update(Long, UpdateEmployeeRequest)` | `findById().orElseThrow` → email 変更時のみ **他者との重複**を判定 → `updateProfile(...)` | 未存在:404 / 重複:409 |

**設計上の要点**
- **パスワードは平文で保存・ログ出力しない**。`create` で `passwordEncoder.encode` した結果のみ Entity に渡す
- 更新の email 重複判定は「**自分自身の email は除外**」する（未変更や自分の email 再指定で誤検知しない）
  - 実装案: `repository.findByEmail(newEmail)` が「別 id」で存在したら 409
- 楽観ロック: `update` は 1 トランザクション内で load → mutate → flush（`@Version` はトランザクション内衝突を守る）。**リクエスト DTO に version は載せない**（クロスリクエストの楽観ロックはデモ範囲外＝YAGNI）
- `@Transactional(readOnly = true)` を参照系（`findAll`/`findById`）に付与

---

## 5. DB 設計（Flyway V2）

- **V2 マイグレーションは原則不要**。`employee` テーブルは共通基盤 V1 で完成しており、登録/一覧/編集はすべて既存列で足りる。
- email は V1 で `UNIQUE`（`uq_employee_email`）→ 索引付き。`findByEmail` は索引が効く。
- **予約は維持**（V2=Unit A）。将来 Unit A で列や索引を足す必要が出たら `V2__*.sql` を使う（連番衝突回避）。
- 一覧は無制限クエリだが、デモは seed 3 件＋数件登録の規模のためページネーションは入れない（YAGNI）。将来必要になれば `Pageable` を追加。

---

## 6. 画面設計（frontend / features/employee）

フィーチャーベースで縦割り。全 fetch は `apiFetch`（= `withBasePath` 適用）経由。ADMIN のみが到達する画面。

### 6.1 ルート / ファイル

```
packages/frontend/src/
├── app/employees/
│   ├── page.tsx                 # 一覧（サーバーコンポーネント枠 + クライアント一覧）
│   ├── new/page.tsx             # 新規登録フォーム
│   └── [id]/edit/page.tsx       # 編集フォーム（{id} を初期表示）
└── features/employee/
    ├── api.ts                   # apiFetch 呼び出し + zod スキーマ（EmployeeResponse 等）
    ├── EmployeeList.tsx         # 一覧テーブル（"use client"）
    └── EmployeeForm.tsx         # 登録/編集共用フォーム（"use client", Zod で入力検証）
```

### 6.2 コンポーネント / データフロー

- `EmployeeList`: `GET /api/employees` → zod で配列を検証 → テーブル表示（id/氏名/email/role）。登録ボタン・各行に編集リンク。
- `EmployeeForm`: 氏名・email・role（登録時は password も）。送信で POST/PUT。
  - 409（email 重複）→「このメールアドレスは既に登録されています」
  - 400（fieldErrors）→ フィールド下にメッセージ表示
  - 403 → 「権限がありません（管理者のみ）」／401 → ログインへ誘導
  - エラーは `ApiError.status` で分岐し **ユーザー向け文言に変換**（生メッセージを出さない / typescript-frontend.md）
- ダッシュボード（[app/page.tsx](../../../packages/frontend/src/app/page.tsx)）の「社員管理」カードから `/employees` へ導線を張る（枠は実装済み）。

### 6.3 zod スキーマ（`any` 禁止・`unknown` + 検証）

```ts
export const employeeSchema = z.object({
  id: z.number(),
  name: z.string(),
  email: z.string(),
  role: z.enum(["ADMIN", "MEMBER"]),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export const employeeListSchema = z.array(employeeSchema);
```

---

## 7. テスト計画（テストが仕様書 / TDD の @DisplayName 粒度）

### 7.1 Backend（層ごと）

**Service（ユニット・Spring 起動なし。repository と passwordEncoder をモック）**
- `create_validRequest_hashesPasswordAndSaves` — password が encode され保存される
- `create_duplicateEmail_throwsBusinessRuleViolation` — 409
- `create_neverStoresPlaintextPassword` — 保存値 ≠ 平文（encode 経由を検証）
- `findAll_returnsResponsesWithoutPasswordHash` — 応答に hash を含まない
- `findById_existingId_returnsResponse` / `findById_nonExisting_throwsResourceNotFound`(404)
- `update_existingId_updatesNameEmailRole`
- `update_nonExisting_throwsResourceNotFound`(404)
- `update_emailTakenByAnother_throwsBusinessRuleViolation`(409)
- `update_sameEmailUnchanged_succeeds` — 自分の email 再指定で誤検知しない

**Controller（`@WebMvcTest(EmployeeController.class)` + `@MockitoBean EmployeeService` + `@Import(SecurityConfig)`）**
- `create_asAdmin_returns201` / `create_asMember_returns403` / `create_anonymous_returns401`
- `create_invalidBody_returns400WithFieldErrors`（blank name / bad email / short password / null role）
- `create_duplicateEmail_returns409`（service が例外）
- `list_asAdmin_returns200Array` / `list_asMember_returns403`
- `update_asAdmin_existing_returns200` / `update_asAdmin_missing_returns404` / `update_asMember_returns403`
- 認可モック: `@WithMockUser(roles = "ADMIN"|"MEMBER")`（spring-security-test）
- CSRF: 並行開発中は POST/PUT に `.with(csrf())` を付けて独立に緑化（認証ユニットが CSRF 無効化を入れたら不要）

**統合（`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`・実 H2+Flyway）**
- `registerThenList_asAdmin_roundtrip` — 登録 → 一覧に出る
- `password_storedAsBcryptHash` — repository で取り出した hash が平文 ≠ かつ `encoder.matches` が真
- `employeesApi_requiresAdmin` — MEMBER=403 / 未認証=401

> テストスライスの依存は SB4 版で確認済み（`spring-boot-webmvc-test` / `spring-boot-data-jpa-test`）。`@DataJpaTest` は `@AutoConfigureTestDatabase(replace = NONE)`。既存 [EmployeeRepositoryTest](../../../packages/backend/src/test/java/com/example/attendance/employee/EmployeeRepositoryTest.java) / [HealthControllerTest](../../../packages/backend/src/test/java/com/example/attendance/common/health/HealthControllerTest.java) が手本。

### 7.2 Frontend

- **現状テスト基盤が未導入**（package.json に vitest / testing-library なし）。方針: 本 Unit の主眼は backend の TDD に置き、frontend はブラウザ(absports)での手動通し確認をデモの受け入れとする。
- コンポーネントテストを入れる場合は `vitest` + `@testing-library/react` を `tdd-implementation` 冒頭で追加（Plan に明記）。**silent に省略しない** — 入れる/入れないを Plan で決める。

---

## 8. 完了条件（この設計のゲート）

- [ ] §1.3 の認可方式（メソッドセキュリティ）にチーム合意（`[Answer]` を埋める）
- [ ] API 契約（エンドポイント / DTO / ステータス / バリデーション）に合意
- [ ] `EmployeeResponse` が passwordHash を含まないこと（漏洩防止）を全員確認
- [ ] 「Employee の列・V1 テーブルは変えない（updateProfile の追加のみ）」に合意
- [ ] **設計レビュー承認（人間ゲート）** → `tdd-implementation` へ

## 9. 次のステップ

1. 本設計書をレビュー・承認（必要なら §1.3 の `[Answer]` を確定）
2. `tdd-implementation` スキルで **Plan（変更ファイル・テストケース・実装順序）** を提示 → 承認
3. Red-Green-Refactor（Service → Controller → 統合 → frontend）
4. `verify` → `multi-agent-review` → PR（1 Unit = 1 PR / `feature/unit-a-employee` → develop）

> 参照: [ou-attendance.md](../../requirements/ou-attendance.md) / [common-foundation.md](../common-foundation.md) / [security-hardening-todo.md](../security-hardening-todo.md)（CSRF/CORS は別ユニット）
