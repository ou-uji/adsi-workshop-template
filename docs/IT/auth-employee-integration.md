# 結合テスト: Unit A（社員管理）× Unit D（認証）

## 概要

Unit A と Unit D を結合し、実際のログインセッションを通じて社員 CRUD API が正しく動作することを検証する。

## テスト対象

- **Unit D**: 認証（login / logout / me）— セッションベース
- **Unit A**: 社員管理（登録 / 一覧 / 取得 / 編集）— ADMIN のみ

## seed データ

| email | role | password |
|-------|------|----------|
| admin@example.com | ADMIN | password |
| hanako@example.com | MEMBER | password |
| jiro@example.com | MEMBER | password |

## テストケース

| # | シナリオ | 期待結果 |
|---|---------|---------|
| 1 | ADMIN ログイン → 社員登録 → 一覧反映 → 取得 → 編集 | 全操作成功（通しフロー） |
| 2 | MEMBER ログイン → 社員一覧 | 403 Forbidden |
| 3 | MEMBER ログイン → 社員登録 | 403 Forbidden |
| 4 | 未ログイン → 社員 API | 401 Unauthorized |
| 5 | ログアウト後 → 社員 API | 401 Unauthorized |
| 6 | 不正パスワードでログイン | 401 + エラーメッセージ |
| 7 | ADMIN が登録した社員で新規ログイン → /me 取得 | 新社員の情報が返る |

## テストファイル

- `packages/backend/src/test/java/com/example/attendance/integration/AuthEmployeeIntegrationTest.java`

## 実行方法

```bash
npm run check:backend
```

## 結果（2026-07-14）

- 全 7 テスト GREEN
- backend 全体: 48 テスト通過
- コンフリクト解消: SecurityConfig（imports 統合）, page.tsx（認証ガード + リンク統合）

## 技術メモ

- SB4 では `@AutoConfigureMockMvc` が `spring-boot-webmvc-test` パッケージに移動
- `ObjectMapper` Bean が `@SpringBootTest` + `@AutoConfigureMockMvc` で注入されないケースあり → 手動 `new ObjectMapper()` + `webAppContextSetup().apply(springSecurity()).build()` で解決
- 実ログインセッション（`MockHttpSession`）を取得し後続リクエストに渡す方式（`@WithMockUser` ではなく本物の認証フローを通す）
