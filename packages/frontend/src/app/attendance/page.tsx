"use client";

import { useAuth } from "@/features/auth/auth-context";
import {
  clockIn,
  clockOut,
  getTodayStatus,
} from "@/features/attendance/attendance-api";
import type { AttendanceRecord } from "@/features/attendance/types";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Link from "next/link";

export default function AttendancePage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const [todayStatus, setTodayStatus] = useState<AttendanceRecord | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!loading && !user) {
      router.push("/login");
    }
  }, [loading, user, router]);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    getTodayStatus()
      .then((status) => {
        if (!cancelled) {
          setTodayStatus(status);
          setError(null);
        }
      })
      .catch(() => {
        if (!cancelled) setError("状態の取得に失敗しました");
      });
    return () => { cancelled = true; };
  }, [user]);

  const handleClockIn = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const result = await clockIn();
      setTodayStatus(result);
    } catch {
      setError("出勤打刻に失敗しました");
    } finally {
      setSubmitting(false);
    }
  };

  const handleClockOut = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const result = await clockOut();
      setTodayStatus(result);
    } catch {
      setError("退勤打刻に失敗しました");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading || !user) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <p className="text-slate-500">読み込み中...</p>
      </div>
    );
  }

  const statusLabel = todayStatus
    ? { NOT_CLOCKED: "未打刻", CLOCKED_IN: "出勤中", CLOCKED_OUT: "退勤済" }[
        todayStatus.status
      ]
    : "取得中...";

  const statusColor = todayStatus
    ? {
        NOT_CLOCKED: "bg-slate-100 text-slate-700",
        CLOCKED_IN: "bg-blue-100 text-blue-700",
        CLOCKED_OUT: "bg-green-100 text-green-700",
      }[todayStatus.status]
    : "bg-slate-100 text-slate-500";

  return (
    <div className="mx-auto max-w-2xl px-6 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">勤怠打刻</h1>
        <Link
          href="/"
          className="text-sm text-slate-500 hover:text-slate-700"
        >
          ← ダッシュボード
        </Link>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-6 text-center">
          <p className="text-sm text-slate-500">本日の状態</p>
          <span
            className={`mt-2 inline-block rounded-full px-4 py-1.5 text-sm font-medium ${statusColor}`}
          >
            {statusLabel}
          </span>
          {todayStatus?.clockInAt && (
            <p className="mt-2 text-sm text-slate-600">
              出勤: {new Date(todayStatus.clockInAt).toLocaleTimeString("ja-JP")}
            </p>
          )}
          {todayStatus?.clockOutAt && (
            <p className="text-sm text-slate-600">
              退勤: {new Date(todayStatus.clockOutAt).toLocaleTimeString("ja-JP")}
            </p>
          )}
        </div>

        {error && (
          <p className="mb-4 rounded bg-red-50 p-3 text-center text-sm text-red-600">
            {error}
          </p>
        )}

        <div className="flex gap-4">
          <button
            onClick={handleClockIn}
            disabled={
              submitting || todayStatus?.status !== "NOT_CLOCKED"
            }
            className="flex-1 rounded-md bg-blue-600 px-4 py-3 font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            出勤
          </button>
          <button
            onClick={handleClockOut}
            disabled={
              submitting || todayStatus?.status !== "CLOCKED_IN"
            }
            className="flex-1 rounded-md bg-green-600 px-4 py-3 font-medium text-white hover:bg-green-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            退勤
          </button>
        </div>
      </div>

      <div className="mt-6 text-center">
        <Link
          href="/attendance/history"
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          打刻履歴を見る →
        </Link>
      </div>
    </div>
  );
}
