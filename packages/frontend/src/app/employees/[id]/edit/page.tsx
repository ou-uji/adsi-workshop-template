"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/lib/api-client";
import { EmployeeForm } from "@/features/employee/EmployeeForm";
import { fetchEmployee, type Employee } from "@/features/employee/api";

type LoadState =
  | { status: "loading" }
  | { status: "loaded"; employee: Employee }
  | { status: "error"; message: string };

// 社員 編集ページ（ADMIN のみ）。{id} の現在値を取得してフォームに初期表示する。
export default function EditEmployeePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const numericId = Number(id);
  const isValidId = Number.isInteger(numericId);
  const [state, setState] = useState<LoadState>(
    isValidId ? { status: "loading" } : { status: "error", message: "不正な社員 ID です" },
  );

  useEffect(() => {
    if (!isValidId) return;
    let active = true;
    fetchEmployee(numericId)
      .then((employee) => {
        if (active) setState({ status: "loaded", employee });
      })
      .catch((error: unknown) => {
        if (active) setState({ status: "error", message: toUserMessage(error) });
      });
    return () => {
      active = false;
    };
  }, [numericId, isValidId]);

  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-8">
      <h1 className="mb-6 text-lg font-semibold">社員を編集</h1>
      {state.status === "loading" && <p className="text-sm text-slate-500">読み込み中…</p>}
      {state.status === "error" && (
        <p role="alert" className="rounded bg-red-100 px-3 py-2 text-sm text-red-700">
          {state.message}
        </p>
      )}
      {state.status === "loaded" && (
        <EmployeeForm employee={state.employee} onSuccess={() => router.push("/employees")} />
      )}
    </div>
  );
}

function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404) return "対象の社員が見つかりません";
    if (error.status === 403) return "この操作を行う権限がありません（管理者のみ）";
    if (error.status === 401) return "ログインが必要です";
  }
  return "社員情報の取得に失敗しました";
}
