import type { NextConfig } from "next";
import path from "node:path";

// SageMaker Code Editor プレビュー対応（.claude/skills/sagemaker-code-editor/SKILL.md）。
// SAGEMAKER=1 のときだけ basePath / rewrites を有効化し、通常のローカル開発には影響させない。
const isSagemaker = process.env.SAGEMAKER === "1";

// AWS デプロイ時は standalone モード（DEPLOY=1）
const isDeploy = process.env.DEPLOY === "1";

// フル basePath（例: /codeeditor/default/absports/3000）。短いパスのみは禁止（リダイレクトで欠落する）。
const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

// backend（workshop, H2）は 8080。/api/* をここへ転送する。
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // monorepo でのルート誤認を防ぐ。workspaces のホイスト先（リポジトリルートの
  // node_modules）も解決できるよう turbopack.root はリポジトリルートに固定する。
  turbopack: {
    root: path.resolve(__dirname, "../.."),
  },

  // AWS デプロイ時は standalone（Docker で ECS に載せる）
  ...(isDeploy ? { output: "standalone" } : {}),

  ...(isSagemaker
    ? {
        basePath,
        assetPrefix: basePath,
        // SageMaker のプロキシ構成では末尾スラッシュの自動リダイレクトが二重化の原因になる。
        skipTrailingSlashRedirect: true,
      }
    : {}),

  // /api/* は常に backend へ転送（SageMaker 時も同一オリジンで rewrites 経由）。
  // 注: 静的エクスポート時は rewrites は無視される（CloudFront で /api/* を ALB へルーティング）
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
