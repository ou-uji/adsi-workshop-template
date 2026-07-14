# frontend（Next.js / TypeScript）— 雛形

> 実体は design/実装フェーズで scaffold する（`create-next-app` 等）。ここは構造の置き場を示すマーカー。

## 想定構成（フィーチャーベース）

```
packages/frontend/
├── src/
│   ├── app/                 # Next.js App Router（ページ）
│   ├── features/
│   │   ├── auth/            # ログイン（Unit D）
│   │   ├── employee/        # ユーザー管理（Unit A）
│   │   ├── attendance/      # 勤怠打刻（Unit B）
│   │   └── leave/           # 休暇申請+承認（Unit C）
│   └── lib/
│       └── api-client.ts    # fetch ラッパー + withBasePath()（SageMaker basePath 対応）
├── scripts/
│   └── sagemaker-proxy.mjs  # 復元プロキシ（3000→3001）
└── next.config.ts           # SAGEMAKER=1 分岐 / basePath / /api rewrites
```

## 規約（`.claude/rules/typescript-frontend.md`）

- `any` 禁止（`unknown` + 型ガード）。API レスポンスは Zod でバリデーション
- 全 fetch に `withBasePath()` を適用（SageMaker で API が全滅しないため）
- サーバーステートは SWR / TanStack Query。イミュータブルに扱う
