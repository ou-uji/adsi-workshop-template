"use client";

import { useEffect, useState } from "react";
import { z } from "zod";
import { apiFetch } from "@/lib/api-client";

// API レスポンスはランタイムバリデーションする（.claude/rules/typescript-frontend.md）。
const healthSchema = z.object({ status: z.string() });

type Status = "loading" | "ok" | "error";

/**
 * backend の /api/health を叩いて疎通状態を表示する小さなバッジ。
 * fetch は必ず apiFetch 経由（= withBasePath 適用）で、SageMaker basePath でも動く。
 */
export function HealthBadge() {
  const [status, setStatus] = useState<Status>("loading");

  useEffect(() => {
    let active = true;
    apiFetch<unknown>("/api/health")
      .then((data) => {
        const parsed = healthSchema.safeParse(data);
        if (active) setStatus(parsed.success && parsed.data.status === "ok" ? "ok" : "error");
      })
      .catch(() => {
        if (active) setStatus("error");
      });
    return () => {
      active = false;
    };
  }, []);

  const label =
    status === "loading" ? "確認中…" : status === "ok" ? "backend: 正常" : "backend: 未接続";
  const color =
    status === "loading"
      ? "bg-slate-100 text-slate-500"
      : status === "ok"
        ? "bg-green-100 text-green-700"
        : "bg-red-100 text-red-700";

  return (
    <span className={`rounded-full px-3 py-1 text-xs font-medium ${color}`}>{label}</span>
  );
}
