# セキュリティ設定の積み残し（別機能で実装）

> 記録日: 2026-07-14 / 対象: 共通基盤(Unit 0) の `SecurityConfig`
> 発生源: 共通基盤コードの `multi-agent-review`（java-reviewer / security-reviewer が HIGH 指摘）
> 位置づけ: **現段階のバグではない**。`SecurityConfig` は雛形であることが明記されており
> （[SecurityConfig.java](../../packages/backend/src/main/java/com/example/attendance/common/config/SecurityConfig.java) の Javadoc §22-23 と TODO コメント §43-44）、
> ここに挙げる項目は **design フェーズ（ゲート②）で方針確定 → 別機能として実装**する。

## なぜ今やらないか

共通基盤のゴールは「起動して画面が出る・つなぎ目(employee/Enum/API規約/Flyway連番)が固定される」。
CSRF/CORS/認証方式は **Unit A のログイン実装**の前提であり、ログイン方式（フォーム/セッション/stateless）が
決まらないと正しく設定できない。雛形のまま先送りし、方針確定後に一括で入れるのが妥当。

## 積み残し項目

| # | 項目 | 位置 | 重大度(レビュー) | 決めること |
|---|------|------|:---:|-----------|
| 1 | CSRF 未設定 | [SecurityConfig.java:36](../../packages/backend/src/main/java/com/example/attendance/common/config/SecurityConfig.java#L36) | HIGH | セッション+CSRF トークン方式か、stateless(REST) で `.csrf().disable()` か。SB4 デフォルトは CSRF 有効 → 現状 POST/PUT/DELETE がトークンなしで 403 になりうる |
| 2 | CORS 未設定 | [SecurityConfig.java:43](../../packages/backend/src/main/java/com/example/attendance/common/config/SecurityConfig.java#L43) | HIGH | frontend↔backend が別ポート/ドメイン。許可オリジンを**明示指定**する（`allowedOrigins("*")` 禁止＝security.md）。SageMaker プレビュー経路の origin も考慮 |
| 3 | H2 console frameOptions | [SecurityConfig.java:40](../../packages/backend/src/main/java/com/example/attendance/common/config/SecurityConfig.java#L40) | MEDIUM | `/h2-console/**` を permitAll しているが、Security デフォルトの `X-Frame-Options: DENY` で iframe 描画されない。H2 UI を使うなら `frameOptions` 対応（同一オリジン許可）が必要。使わないなら permitAll ごと削除 |
| 4 | 認証エラーメッセージ形式 | ログイン API 実装時 | MEDIUM | 「メールまたはパスワードが正しくありません」= どちらが誤りか特定できない形（security.md）。Unit A のログインで実装＋テスト |

## 実装フェーズでの受け入れ基準（メモ）

- [ ] ログイン方式を design で確定（推奨: セッション or stateless を1つに決める）
- [ ] #1 CSRF を方式に合わせて設定（stateless なら disable + 根拠コメント）
- [ ] #2 CORS を許可オリジン明示で設定（`*` 禁止）
- [ ] #3 H2 console: 使う/使わないを決め、使うなら frameOptions 対応
- [ ] #4 認証失敗メッセージがユーザー名/パスワードのどちらか特定できない形をテストで検証

## 関連

- 共通基盤 設計書: [common-foundation.md](./common-foundation.md)
- セキュリティ規約: [../../.claude/rules/security.md](../../.claude/rules/security.md)
- 確定要求（認証仕様）: [../requirements/ou-attendance.md](../requirements/ou-attendance.md)
