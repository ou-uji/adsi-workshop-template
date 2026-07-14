# 実装計画（Plan）— Unit A: ユーザー管理

> SDD `tdd-implementation` Phase 1（Plan → 人間承認 → Phase 2 TDD）の成果物。
> 作成: 2026-07-14 / ブランチ: `feature/unit-a-employee`
> 設計: [a-employee-sdd-design.md](./a-employee-sdd-design.md)（設計確定済み・認可=メソッドセキュリティ）
> ステータス: **承認済み（王・2026-07-14「承認前提で実装まで進める」）** → Phase 2 実行

---

## 0. スコープ（設計の再掲）

社員管理の **登録 / 一覧 / 1件取得 / 編集**（すべて ADMIN のみ）。認証の実体・CSRF/CORS 確定は別ユニット。
土台（Employee Entity・Repository・共通例外・`PasswordEncoder` Bean）は実装済みを再利用する。

---

## 1. 作成・変更するファイル一覧

### Backend（`packages/backend/src/main/java/com/example/attendance/`）

| 区分 | パス | 役割 |
|------|------|------|
| **変更** | `employee/Employee.java` | `updateProfile(name,email,role)` を追加（列・テーブルは変えない） |
| **変更** | `common/config/SecurityConfig.java` | `@EnableMethodSecurity` を 1 行追加（唯一の共有変更） |
| 新規 | `employee/dto/CreateEmployeeRequest.java` | 登録 req（record + Bean Validation） |
| 新規 | `employee/dto/UpdateEmployeeRequest.java` | 編集 req（record、password なし） |
| 新規 | `employee/dto/EmployeeResponse.java` | 応答（record、`from(Employee)`。**passwordHash 非含**） |
| 新規 | `employee/EmployeeService.java` | interface（create/findAll/findById/update） |
| 新規 | `employee/EmployeeServiceImpl.java` | impl（`@Transactional`・コンストラクタ DI） |
| 新規 | `employee/EmployeeController.java` | `@RestController`・`@PreAuthorize("hasRole('ADMIN')")` |

### Backend テスト（`packages/backend/src/test/java/com/example/attendance/employee/`）

| パス | 種別 |
|------|------|
| `EmployeeServiceImplTest.java` | ユニット（Mockito・Spring 起動なし） |
| `EmployeeControllerTest.java` | `@WebMvcTest` + `@MockitoBean` + `@Import(SecurityConfig)` |
| `EmployeeApiIntegrationTest.java` | `@SpringBootTest` + `@AutoConfigureMockMvc`（実 H2+Flyway seed） |

> 既存 `EmployeeRepositoryTest`（保存/一意/検索）は土台で緑。Unit A では拡張不要。

### Frontend（`packages/frontend/src/`）

| パス | 役割 |
|------|------|
| `features/employee/api.ts` | `apiFetch` 呼び出し + zod スキーマ |
| `features/employee/EmployeeList.tsx` | 一覧テーブル（"use client"） |
| `features/employee/EmployeeForm.tsx` | 登録/編集共用フォーム（"use client"・Zod 検証） |
| `app/employees/page.tsx` | 一覧ルート |
| `app/employees/new/page.tsx` | 登録ルート |
| `app/employees/[id]/edit/page.tsx` | 編集ルート |

> frontend はテストランナー未導入 → 本 Unit では backend TDD を主眼、frontend はブラウザ手動確認をデモ受け入れとする（設計 §7.2）。

### Flyway

- **V2 は原則作らない**（employee テーブルは V1 で完成、既存列で足りる）。予約番号 V2 は維持。

---

## 2. テストケース一覧（テストが仕様書）

### Service ユニット（`EmployeeServiceImplTest`）
1. `create_validRequest_hashesPasswordAndSaves` — password を encode して保存（captor で hash 検証）
2. `create_neverStoresPlaintextPassword` — 保存 hash ≠ 平文
3. `create_duplicateEmail_throwsBusinessRuleViolation` — 409
4. `findAll_mapsToResponses` — 全件を Response に写像
5. `findById_existingId_returnsResponse`
6. `findById_nonExisting_throwsResourceNotFound` — 404
7. `update_existingId_updatesNameEmailRole`
8. `update_nonExisting_throwsResourceNotFound` — 404
9. `update_emailTakenByAnother_throwsBusinessRuleViolation` — 409
10. `update_sameEmailUnchanged_succeeds` — 自分の email 再指定で誤検知しない

### Controller（`EmployeeControllerTest`・`@MockitoBean EmployeeService`）
11. `create_asAdmin_returns201`
12. `create_invalidBody_returns400WithFieldErrors` — blank name / 不正 email / 短 password / null role
13. `create_duplicateEmail_returns409`（service が例外）
14. `list_asAdmin_returns200Array`
15. `getById_asAdmin_returns200` / `getById_missing_returns404`
16. `update_asAdmin_returns200` / `update_missing_returns404`
17. `create_asMember_returns403` — 認可（`@PreAuthorize`・`@WithMockUser(roles="MEMBER")`）
18. `list_asMember_returns403`
19. `list_anonymous_isDenied` — 未認証は拒否（4xx。**正確な 401/403 は認証ユニットの entry point 次第 → 範囲アサート**）

### 統合（`EmployeeApiIntegrationTest`・`@Transactional` でロールバック）
20. `registerThenList_asAdmin_roundtrip` — 登録 → 一覧に出る（seed 3 → 4）
21. `password_storedAsBcryptHash` — repository の hash が平文 ≠ かつ `encoder.matches` 真
22. `employeesApi_asMember_returns403` — 認可の実 API 強制

---

## 3. 実装順序（Red → Green → Refactor）

1. **Employee.updateProfile** 追加（既存テスト緑のまま）
2. **DTO 3 種**（record + validation）
3. **EmployeeService interface**
4. **EmployeeServiceImplTest（Red）→ EmployeeServiceImpl（Green）**
5. **SecurityConfig に `@EnableMethodSecurity`**（認可の前提）
6. **EmployeeControllerTest（Red）→ EmployeeController（Green）**
7. **EmployeeApiIntegrationTest（Red）→ Green**
8. `npm run check:backend` で全緑を確認 → Refactor
9. Frontend `features/employee/` + ルート実装（apiFetch/zod・手動確認）

---

## 4. 依存・リスク・つなぎ目

- **認証ユニット未完成でも並行可**: 認可テストは `@WithMockUser(roles=…)` + `csrf()` でモック（実装スライド「相手の Unit はモックで代用」）。
- **CSRF**: 現状 SecurityConfig は CSRF 有効（デフォルト）。テストの POST/PUT は `.with(csrf())` を付ける。認証ユニットが CSRF 無効化を入れたら不要になる。
- **未認証の HTTP コード**: entry point 未設定のため現状は 403。正確な 401 は認証ユニットが決める → Unit A は「拒否される（4xx）」だけを検証し結合破壊を避ける。
- **共有 `SecurityConfig` への変更は `@EnableMethodSecurity` の 1 行のみ**。パスごとの `hasRole` は書かない（他ユニットとの取り合い回避）。
- **`EmployeeResponse` に passwordHash を含めない**（型で保証 + テストで確認）。

---

## 5. 完了条件

- [x] 全テスト緑（`npm run check:backend` → 29 tests, 0 failures）
- [x] 新規コードが `.claude/rules/`（Java/SB4・security・testing）準拠
- [x] `EmployeeResponse` が passwordHash を含まない（型 + テストで確認）
- [x] Employee の列・V1 は不変（updateProfile 追加のみ）
- [x] frontend lint 通過（`npm run lint:frontend`）+ production build 成功（3 ルート認識）
- [ ] → `verify` → `multi-agent-review` → PR（1 Unit = 1 PR）

## 6. 実装実績（2026-07-14）

- **backend テスト 29 本緑**: Service 10 / Controller 11 / 統合 3 / 既存(Repository 3・Health 1・context 1)
- **TDD 中に判明した 2 つの技術的障害と解決**:
  1. **`@WithMockUser` が MockMvc に伝播せず全リクエストが匿名 → 403**。SB4 の `@WebMvcTest`/`@SpringBootTest` 単独ではセキュリティのテスト統合が自動適用されない。
     → `webAppContextSetup(context).apply(springSecurity()).build()` で MockMvc を明示構築して解決（Controller/統合テスト共通）。**次 Unit（B/C）で認可テストを書くときも同じ構築が必要**。
  2. **`@PreAuthorize` 拒否が 500 になる**（設計書の追記参照）→ 共通ハンドラに 403 マッピング追加で解決。
- **共通領域の変更は 2 点のみ**: `SecurityConfig` に `@EnableMethodSecurity`、`GlobalExceptionHandler` に `AccessDeniedException→403`。パスごとの認可は書いていない（他ユニットと競合しない）。
- **Flyway V2 は作成せず**（既存列で足りる。予約番号は維持）。
