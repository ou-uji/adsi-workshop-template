import Link from "next/link";
import { EmployeeList } from "@/features/employee/EmployeeList";

// 社員一覧ページ（ADMIN のみ）。一覧の取得はクライアント側（EmployeeList）で行う。
export default function EmployeesPage() {
  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold">社員管理</h1>
          <p className="text-sm text-slate-500">登録・一覧・編集（管理者のみ）</p>
        </div>
        <Link
          href="/employees/new"
          className="rounded bg-slate-800 px-4 py-2 text-sm text-white"
        >
          新規登録
        </Link>
      </div>
      <EmployeeList />
    </div>
  );
}
