# 実装計画（Plan）— 共通基盤 Unit（TDD）

> SDD 実装フェーズ Phase 1（Plan）。→ `.claude/skills/tdd-implementation/SKILL.md`
> 作成: 2026-07-14 / **ステータス: 承認済み（王）— 別セッションで Phase 2 実装に入る**
> 設計: [common-foundation.md](./common-foundation.md) / 要求: [../requirements/ou-attendance.md](../requirements/ou-attendance.md)
>
> スライド【共通基盤】準拠: 「起動して画面が出る・テストが通る」まで作り切って main へ → その後に梶田さんへ共有・分担スタート。**焦って分担しない。**

---

## 🎯 ゴール（このステップの完了条件）

1. **アプリが起動し、画面の枠が表示できる**（frontend のシェル / SageMaker absports プレビュー）
2. **共有テーブル・共通 API の型が main にある**（employee テーブル + Repository + 共通 API）
3. **テストが 1 本通っている**（TDD の土台）

### スコープの線引き
「動く土台」まで。**認証フロー本体・A/B/C の業務ロジックは含めない**（次工程）。画面は"枠"のみ、業務ロジックなし。

---

## 🧰 実行環境（実測済み 2026-07-14）

| ツール | バージョン | 備考 |
|--------|-----------|------|
| Java | Corretto **21.0.11** | `JAVA_HOME=/opt/mise/installs/java/corretto-21`（SageMaker 起動スクリプトで指定） |
| Gradle | **8.14.5**（system） | Wrapper 未生成 → `gradle wrapper` で生成する |
| Node | **20.19.6** / npm 11.13.0 | frontend scaffold 用 |
| DB | H2（workshop, インメモリ） | Docker 不要 |

> ⚠️ Spring Boot 版は雛形で `4.0.0` 指定。**Phase 2 の最初にビルドで依存解決を実証**し、解決しなければ実在する最新 SB4 patch に合わせる（rules は SB4 前提なので方針は不変）。

---

## 📂 作成・変更するファイル

### Backend（起動 + テスト土台）
| ファイル | 役割 | 状態 |
|---|---|---|
| `packages/backend/gradlew` + `gradle/wrapper/*` | Gradle Wrapper（`gradle wrapper --gradle-version 8.14.5`） | 新規 |
| `build.gradle` | SB 版を実解決に確定・依存確認 | 改訂（雛形あり） |
| `employee/Employee.java` | 共有の核 Entity（id/name/email/passwordHash/role/version/created_at/updated_at） | 新規 |
| `employee/EmployeeRepository.java` | Spring Data JPA interface（共通 API の型・`findByEmail` 等） | 新規 |
| `common/health/HealthController.java` | 起動確認用の最小 API `GET /api/health` → `{status:"ok"}` | 新規 |
| `db/migration/V1__init_common.sql` | H2 で通る型か確認・微修正 | 改訂（雛形あり） |
| `db/migration/V1_1__seed_common.sql` | BCrypt 実ハッシュに差し替え（password="password" 等） | 改訂（雛形あり） |

### Backend テスト（TDD の1本 = 土台）
| テスト | 種別 | 検証 |
|---|---|---|
| `AttendanceApplicationTests` | `@SpringBootTest`(`@ActiveProfiles("test")`) | contextLoads + Flyway V1 適用が通る |
| `EmployeeRepositoryTest` | `@DataJpaTest` | 保存/取得・email 一意制約（Red→Green） |
| `HealthControllerTest` | `@WebMvcTest` | `GET /api/health` が 200（共通 API の型） |

> テスト規約: `@ActiveProfiles("test")`、モックは `@MockitoBean`、AAA パターン、`メソッド_シナリオ_期待結果`（`.claude/rules/testing.md`）。

### Frontend（画面の枠）
| ファイル | 役割 |
|---|---|
| `packages/frontend/` | `create-next-app`（TS + Tailwind + App Router）で scaffold |
| `next.config.ts` | `SAGEMAKER=1` 分岐・`basePath`・`/api/*`→:8080 rewrites・`turbopack.root` |
| `scripts/sagemaker-proxy.mjs` | 復元プロキシ（3000→3001、`/codeeditor/default` 前置） |
| `src/app/layout.tsx` + `src/app/page.tsx` | **画面の枠**（勤怠管理ダッシュボード shell・メニュー: ログイン/社員/打刻/休暇のプレースホルダ） |
| `src/lib/api-client.ts` | `withBasePath()` + fetch ラッパー（全 fetch に適用） |

### ルート
| ファイル | 役割 |
|---|---|
| `package.json` | scripts を実コマンドに実体化（`setup`/`boot:workshop`/`dev:sagemaker`/`check:backend`/`lint:frontend`） |
| `scripts/dev-sagemaker.sh` | 起動スクリプト（sagemaker-code-editor スキル準拠: SAGEMAKER=1・NEXT_PUBLIC_BASE_PATH・next build→bootRun→next start→proxy） |

---

## 🔄 実装順序（Backend → Frontend、各 Red → Green → Refactor）

1. **Gradle Wrapper 生成** → `AttendanceApplicationTests`（Red: まだ Entity/依存不整合の可能性）→ ビルド依存の実証
2. **`Employee` Entity + `EmployeeRepository`** → `EmployeeRepositoryTest` を Green（保存・email 一意）
3. **`HealthController`** → `HealthControllerTest` を Green → `npm run boot:workshop` で起動確認（`GET /api/health` 200）
4. **Frontend scaffold → 画面枠 → api-client** → `npm run dev:sagemaker` で **absports プレビュー確認**（画面の枠が出る）
5. **Refactor** + `verify` スキル（`check:backend` / `lint:frontend` / テスト緑）

---

## ⚠️ リスク・確認事項

- **Spring Boot 4.0.0 依存解決**: Phase 2 冒頭でビルド実証。ダメなら実在最新 SB4 patch へ。
- **frontend scaffold**: `create-next-app` はネットワークあり npm 実行（SageMaker の `--network` 制約は Docker の話で npm には非該当）。
- **時間のかかる工程**: Gradle 初回 DL・`next build` はバックグラウンド実行推奨。
- **SageMaker プレビュー**: `ports`→`absports` 置換必須・全 fetch に `withBasePath()`（`.claude/rules/sagemaker-preview.md` / `sagemaker-code-editor` スキル）。
- **環境で詰まったら**: `dev-environment` スキルで復旧（スライドの指示）。

## 🚫 このステップでやらないこと
認証フロー本体・ログイン画面の中身・A/B/C の CRUD/ロジック・E2E テスト。画面は"枠"のみ。

---

## ▶️ 別セッションでの再開方法

1. このファイルと [common-foundation.md](./common-foundation.md) を読む（`CLAUDE.md` も自動ロードされる）
2. `tdd-implementation` スキルは承認済み → **Phase 2 から開始**
3. 上記「実装順序」の 1 から Red→Green→Refactor で進める
4. ゴール到達（起動 + 画面枠 + テスト1本）後 → `verify` → main へ → 梶田さんに共有 → 分担スタート

> 起動コマンド: `npm run boot:workshop`（backend単体） / `npm run dev:sagemaker`（フル・プレビュー）
