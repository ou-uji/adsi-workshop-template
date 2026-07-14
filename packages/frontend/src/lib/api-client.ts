// API クライアント — SageMaker basePath 対応の fetch ラッパー。
// 規約（.claude/rules/typescript-frontend.md / sagemaker-preview.md）:
//   すべての fetch と location 遷移に withBasePath() を適用する。
//   fetch("/api/...") の絶対パスは basePath 未適用でゲートウェイ直下へ飛び API が全滅する。

// ビルド時に注入されるフル basePath（SageMaker 時のみ非空。ローカル/本番 static では空）。
const BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

/**
 * アプリ内の絶対パスに basePath を前置する。
 * basePath が空（ローカル/本番）ならパスをそのまま返す。
 */
export function withBasePath(path: string): string {
  if (!path.startsWith("/")) {
    throw new Error(`withBasePath はアプリ内絶対パス（先頭 "/"）のみ受け付けます: ${path}`);
  }
  return BASE_PATH ? `${BASE_PATH}${path}` : path;
}

/** API 呼び出しで投げるエラー（呼び出し側でユーザー向けメッセージに変換する）。 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * JSON API を叩く共通ラッパー。すべての API 呼び出しはこれを経由する。
 * - basePath を必ず適用（withBasePath）
 * - 非 2xx は ApiError に正規化（詳細メッセージは呼び出し側で表示）
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(withBasePath(path), {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  if (!res.ok) {
    throw new ApiError(res.status, `API エラー (${res.status})`);
  }

  // 204 等ボディなしに対応
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}
