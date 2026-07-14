# 実装検討方針 — 勤怠管理システム（Day 2）

> 作成: 2026-07-14 / 確定者: 王（代表）
> ベース要求: [ou-attendance.md](./ou-attendance.md)（確定版）
> 比較検討の経緯: [../working/requirements/comparison-ou-vs-kajita.md](../working/requirements/comparison-ou-vs-kajita.md)

---

## 0. 決定サマリ

- **ベース案は ou 要件仕様書（`ou-attendance.md`）で確定**。梶田案は To-Be 要求として別途参照（今日は実装しない）。
- **最小限の機能から実現する**。スコープは 4 つ（D 認証 / A ユーザー管理 / B 勤怠打刻 / C 休暇+承認）。
- **王（代表）がまず共通基盤（`common/`）を作り**、午後に **A → B を順に実装**する。

---

## 1. スコープ（最小構成）

| # | ドメイン | 内容 | 位置づけ |
|---|---------|------|---------|
| **D** | 認証（最小） | email+password ログイン / ログアウト / role 判定（BCrypt） | **共通基盤に含める（先行）** |
| **A** | ユーザー管理 | 社員 登録 / 一覧（role + passwordHash） | 午後 実装 |
| **B** | 勤怠打刻 | 出勤 / 退勤 / 履歴 / 当日状態 | 午後 実装 |
| **C** | 休暇申請 + 承認 | 申請 / 一覧 + ADMIN の承認/却下（状態遷移） | 後続（余力・分担次第） |

> 「最小限をまず実現」= D+A+B が動けばデモの背骨（ログイン→打刻→履歴）が通る。C は状態遷移で最重量のため後段。

---

## 2. 王（代表）の役割と順序

### ステップ 1 — 共通基盤（`common/`）を作る【最優先】

全機能が乗る土台。A/B/C の分担・並列実装より**先に**、代表 1 台で main に用意する。

```
com.example.attendance
└── common/       共通基盤
    ├── @RestControllerAdvice（共通例外ハンドリング）
    ├── エラーレスポンス形式（フィールド名 + メッセージ、内部詳細は返さない）
    └── Enum(Role / LeaveType / LeaveStatus)
```

**共通基盤に含める作業（要求書 §7.1 準拠）**
- [ ] Spring Boot + Next.js 雛形（`npm run setup` が通る）
- [ ] `Employee` Entity / テーブル（Flyway **V1**、role + passwordHash カラム含む）
- [ ] 共有 Enum（Role / LeaveType / LeaveStatus）
- [ ] **認証（D）**: Spring Security 最小構成、ログイン/ログアウト、BCrypt、role 判定、保護ルート
- [ ] **DB/Flyway 土台**: H2 workshop 設定、`db/migration/` 連番ルール、seed（ADMIN 1・MEMBER 数名）
- [ ] `@RestControllerAdvice` の共通例外ハンドリング + エラーレスポンス形式
- [ ] API ベース規約（パス命名、日付/時刻フォーマット、ログインユーザーの取得方法）
- [ ] 起動・H2・テスト（`npm run boot:workshop` / `npm run check:backend`）が回る

→ ここまでで「動く土台」が main にある状態（アプリ起動・インメモリDB・テスト実行が回る）。

### ステップ 2 — 午後: A → B を順に実装

共通基盤の完成後、**A（ユーザー管理）→ B（勤怠打刻）の順**で TDD 実装する。

- **A: ユーザー管理**（Flyway V2）: 社員 登録 / 一覧。role + passwordHash を扱う。B/C が参照する Employee の CRUD がここで揃う。
- **B: 勤怠打刻**（Flyway V3）: 出勤 / 退勤 / 履歴 / 当日状態。A の Employee に依存。

> A を先にするのは、B/C が Employee(employeeId) に依存するため。土台の次に「参照される側」を固める順序。

---

## 3. 進め方（SDD / スライド ⑥）

```
③ 共通基盤（王・1台で main へ）
      ↓  ← 王はここを最初に作る
④ 実装（午後）: A → B を順に TDD
      ↓
⑤ 結合 → 通し確認 → multi-agent-review
```

- 各機能は `Controller → Service(interface) → Repository(interface) → Entity` のレイヤードで実装。
- **DTO = record 使う / DAO 使わない**（Spring Data JPA Repository に統一）。
- **Flyway 連番の予約**: V1=共通基盤 / V2=A / V3=B / V4=C（連番衝突の回避）。
- 各 Unit は `tdd-implementation` スキルで Plan → 承認 → Red-Green-Refactor。

---

## 4. 今日やらないこと（YAGNI 堅持）

梶田案の深い業務ルールは To-Be 側に残し、今日は入れない:
休憩自動控除 / フレックス / 有給付与・繰越・時効 / 月次集計 / CSV / 通知 / カレンダー / 打刻修正申請 / 監査ログの実装。

（監査ログの"思想"＝状態変更に who/when を残せる設計余地だけは意識する）

---

## 5. 次のステップ

1. 本方針で合意（← 済）
2. 新しいセッションで `design` スキル → 共通基盤 + A/B のドメイン/DB(Flyway)/API(OpenAPI)/画面を設計
3. 共通基盤を実装（王・1台）→ 午後 A → B を TDD → 結合

> 参照: [ou-attendance.md](./ou-attendance.md) / `.claude/skills/design` / `.claude/skills/tdd-implementation`
