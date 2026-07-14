"use client";

import { useRouter } from "next/navigation";
import { EmployeeForm } from "@/features/employee/EmployeeForm";

// 社員 新規登録ページ（ADMIN のみ）。
export default function NewEmployeePage() {
  const router = useRouter();
  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-8">
      <h1 className="mb-6 text-lg font-semibold">社員を登録</h1>
      <EmployeeForm onSuccess={() => router.push("/employees")} />
    </div>
  );
}
