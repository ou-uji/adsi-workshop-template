"use client";

import { useAuth } from "@/features/auth/auth-context";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { HealthBadge } from "@/features/health/HealthBadge";

const UNITS = [
  { key: "auth", title: "ログイン", desc: "認証（Unit D）", owner: "共通基盤", done: true },
  { key: "employee", title: "社員管理", desc: "登録・一覧（Unit A）", owner: "メンバー1", done: false },
  { key: "attendance", title: "勤怠打刻", desc: "出勤・退勤・履歴（Unit B）", owner: "メンバー2", done: false },
  { key: "leave", title: "休暇申請", desc: "申請・承認（Unit C）", owner: "経験者/ペア", done: false },
] as const;

export default function DashboardPage() {
  const { user, loading, logout } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.push("/login");
    }
  }, [loading, user, router]);

  if (loading) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <p className="text-slate-500">読み込み中...</p>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <div className="flex min-h-full flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <div>
            <h1 className="text-lg font-semibold">勤怠管理システム</h1>
            <p className="text-sm text-slate-500">ADSI ワークショップ</p>
          </div>
          <div className="flex items-center gap-4">
            <HealthBadge />
            <div className="text-right">
              <p className="text-sm font-medium">{user.name}</p>
              <p className="text-xs text-slate-500">{user.role}</p>
            </div>
            <button
              onClick={async () => {
                await logout();
                router.push("/login");
              }}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
            >
              ログアウト
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-8">
        <h2 className="mb-4 text-sm font-medium uppercase tracking-wide text-slate-500">
          機能メニュー
        </h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {UNITS.map((unit) => (
            <section
              key={unit.key}
              className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
            >
              <h3 className="font-semibold">{unit.title}</h3>
              <p className="mt-1 text-sm text-slate-600">{unit.desc}</p>
              <p className="mt-3 text-xs text-slate-400">担当: {unit.owner}</p>
              <p className={`mt-2 inline-block rounded px-2 py-0.5 text-xs ${
                unit.done
                  ? "bg-green-100 text-green-700"
                  : "bg-slate-100 text-slate-500"
              }`}>
                {unit.done ? "実装済み" : "未実装"}
              </p>
            </section>
          ))}
        </div>
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-5xl px-6 py-3 text-xs text-slate-400">
          ログイン中: {user.email} ({user.role})
        </div>
      </footer>
    </div>
  );
}
