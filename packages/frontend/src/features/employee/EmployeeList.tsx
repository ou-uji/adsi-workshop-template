"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "@/lib/api-client";
import { fetchEmployees, type Employee } from "./api";

type LoadState =
  | { status: "loading" }
  | { status: "loaded"; employees: Employee[] }
  | { status: "error"; message: string };

/** 社員一覧テーブル（ADMIN のみ到達）。/api/employees を取得して表示する。 */
export function EmployeeList() {
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let active = true;
    fetchEmployees()
      .then((employees) => {
        if (active) setState({ status: "loaded", employees });
      })
      .catch((error: unknown) => {
        if (active) setState({ status: "error", message: toUserMessage(error) });
      });
    return () => {
      active = false;
    };
  }, []);

  if (state.status === "loading") {
    return <p className="text-sm text-slate-500">読み込み中…</p>;
  }
  if (state.status === "error") {
    return (
      <p role="alert" className="rounded bg-red-100 px-3 py-2 text-sm text-red-700">
        {state.message}
      </p>
    );
  }

  return (
    <table className="w-full border-collapse text-sm">
      <thead>
        <tr className="border-b border-slate-200 text-left text-slate-500">
          <th className="py-2 pr-4">ID</th>
          <th className="py-2 pr-4">氏名</th>
          <th className="py-2 pr-4">メール</th>
          <th className="py-2 pr-4">ロール</th>
          <th className="py-2">操作</th>
        </tr>
      </thead>
      <tbody>
        {state.employees.map((employee) => (
          <tr key={employee.id} className="border-b border-slate-100">
            <td className="py-2 pr-4">{employee.id}</td>
            <td className="py-2 pr-4">{employee.name}</td>
            <td className="py-2 pr-4">{employee.email}</td>
            <td className="py-2 pr-4">{employee.role}</td>
            <td className="py-2">
              <Link
                href={`/employees/${employee.id}/edit`}
                className="text-blue-600 hover:underline"
              >
                編集
              </Link>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return "この操作を行う権限がありません（管理者のみ）";
    if (error.status === 401) return "ログインが必要です";
  }
  return "社員一覧の取得に失敗しました";
}
