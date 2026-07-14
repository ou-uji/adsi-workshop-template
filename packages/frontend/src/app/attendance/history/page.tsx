"use client";

import { useAuth } from "@/features/auth/auth-context";
import { getHistory } from "@/features/attendance/attendance-api";
import type { AttendanceRecord } from "@/features/attendance/types";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Link from "next/link";

export default function AttendanceHistoryPage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [fetching, setFetching] = useState(true);

  useEffect(() => {
    if (!loading && !user) {
      router.push("/login");
    }
  }, [loading, user, router]);

  useEffect(() => {
    if (user) {
      getHistory()
        .then((data) => {
          setRecords(data);
          setError(null);
        })
        .catch(() => {
          setError("履歴の取得に失敗しました");
        })
        .finally(() => setFetching(false));
    }
  }, [user]);

  if (loading || !user) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <p className="text-slate-500">読み込み中...</p>
      </div>
    );
  }

  const statusLabel = (status: string) =>
    ({ NOT_CLOCKED: "未打刻", CLOCKED_IN: "出勤中", CLOCKED_OUT: "退勤済" })[
      status
    ] ?? status;

  const formatTime = (dateTime: string | null) => {
    if (!dateTime) return "—";
    return new Date(dateTime).toLocaleTimeString("ja-JP");
  };

  return (
    <div className="mx-auto max-w-3xl px-6 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">打刻履歴</h1>
        <div className="flex gap-4">
          <Link
            href="/attendance"
            className="text-sm text-blue-600 hover:text-blue-800"
          >
            ← 打刻画面
          </Link>
          <Link
            href="/"
            className="text-sm text-slate-500 hover:text-slate-700"
          >
            ダッシュボード
          </Link>
        </div>
      </div>

      {error && (
        <p className="mb-4 rounded bg-red-50 p-3 text-center text-sm text-red-600">
          {error}
        </p>
      )}

      {fetching ? (
        <p className="text-center text-slate-500">読み込み中...</p>
      ) : records.length === 0 ? (
        <p className="text-center text-slate-500">打刻履歴がありません</p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                <th className="px-4 py-3 font-medium text-slate-700">日付</th>
                <th className="px-4 py-3 font-medium text-slate-700">出勤</th>
                <th className="px-4 py-3 font-medium text-slate-700">退勤</th>
                <th className="px-4 py-3 font-medium text-slate-700">状態</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {records.map((record) => (
                <tr key={record.id ?? record.workDate}>
                  <td className="px-4 py-3">{record.workDate}</td>
                  <td className="px-4 py-3">{formatTime(record.clockInAt)}</td>
                  <td className="px-4 py-3">{formatTime(record.clockOutAt)}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${
                        record.status === "CLOCKED_OUT"
                          ? "bg-green-100 text-green-700"
                          : record.status === "CLOCKED_IN"
                            ? "bg-blue-100 text-blue-700"
                            : "bg-slate-100 text-slate-600"
                      }`}
                    >
                      {statusLabel(record.status)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
