# Multi-Agent Review 結果 — Unit A: ユーザー管理

> 実施: 2026-07-14 / ブランチ: `feature/unit-a-employee`
> スキル: `multi-agent-review`（subagent: java / typescript / security / test・model=sonnet・readonly）
> 対象: employee パッケージ（Controller/Service/DTO）+ 共通変更（SecurityConfig・GlobalExceptionHandler）+ frontend features/employee とルート
> 事前チェック: `npm run check:backend`（29 tests green）/ `npm run lint:frontend`（pass）/ frontend build（pass）

---

## 総合判定: **Approve**（CRITICAL / HIGH なし）

| Reviewer | 結果 | 指摘 |
|----------|------|------|
| java-reviewer | Approve | 問題なし（SB4 互換・レイヤード・DTO=record・@Transactional 位置すべて準拠） |
| typescript-reviewer | Approve | 問題なし（any 不使用・Zod 検証・apiFetch/withBasePath 徹底・300 行以内） |
| security-reviewer | Approve | 問題なし（認可・BCrypt・passwordHash 非漏洩・入力検証・SQLi なし） |
| test-reviewer | Approve（軽微指摘） | MEDIUM 1 / LOW 2（CRITICAL/HIGH なし） |

`.claude/rules/common/code-review.md` の承認基準「CRITICAL / HIGH なし → Approve」に該当。

---

## 指摘と対応

### MEDIUM（対応済み）

| # | 指摘 | 対応 |
|---|------|------|
| M-1 | 統合テストで passwordHash 非漏洩を **JSON レベル**で確認していない（型保証はあるが実 API シリアライズ結果は未検証） | **対応済み**: `EmployeeApiIntegrationTest.registerThenList_asAdmin_roundtrip` に `jsonPath("$[*].passwordHash").doesNotExist()` を追加。再実行で緑を確認 |

> Unit A の最重要セキュリティ要件（passwordHash を返さない）を実 API で二重に担保するため、コストほぼゼロの M-1 はその場で取り込んだ。

### LOW（今回は見送り・記録のみ）

| # | 指摘 | 判断 |
|---|------|------|
| L-1 | Service テストで id をリフレクション設定 | **許容**。Entity に setter を付けない方針の裏返しで、テスト専用の生成パターンとして妥当。現状問題なし |
| L-2 | ArchUnit のレイヤー違反テストが未実装 | **見送り**（優先度低）。testing.md は推奨するが、**プロジェクト全体で 1 回**書けば足りる横断的テスト。Unit A 単体の責務ではないため、結合フェーズ or 共通基盤の追補で対応するのが適切。→ 積み残しとして下記に記録 |

---

## 積み残し（別タイミングで対応）

- [ ] **ArchUnit テストの追加**（プロジェクト全体で 1 回）: Controller→Service（Repository 直接禁止）/ Service→Repository / Entity は他レイヤー非依存。結合フェーズか共通基盤の追補で。

---

## レビューで確認された良い点（維持したい）

- SB4 互換規約の完全遵守（`@MockitoBean` / `SecurityFilterChain` Bean / `requestMatchers`・旧 API 不使用）
- passwordHash を型（`EmployeeResponse` に非含）+ テスト（Service・統合の JSON）で二重に非漏洩保証
- 認証ユニット未完成でも `@WithMockUser` + `springSecurity()` で独立して認可テスト可能
- 共通領域への変更を 2 点（`@EnableMethodSecurity` / `AccessDeniedException→403`）に限定し、他ユニットと競合しない

## 次のステップ

1. 動作確認（`npm run dev:sagemaker` → absports でダッシュボード → 社員管理）
2. コミット → PR（1 Unit = 1 PR / `feature/unit-a-employee` → develop）
