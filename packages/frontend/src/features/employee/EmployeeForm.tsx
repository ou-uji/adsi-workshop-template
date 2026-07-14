"use client";

import { useState } from "react";
import { z } from "zod";
import { ApiError } from "@/lib/api-client";
import {
  createEmployee,
  updateEmployee,
  type Employee,
  type Role,
} from "./api";

// 入力のクライアント側バリデーション（送信前の一次チェック。最終検証は backend）。
const baseFields = {
  name: z.string().min(1, "氏名を入力してください").max(100),
  email: z.string().email("メールアドレスの形式が正しくありません").max(255),
  role: z.enum(["ADMIN", "MEMBER"]),
};
const createFormSchema = z.object({
  ...baseFields,
  password: z.string().min(8, "パスワードは8文字以上で入力してください").max(72),
});
const editFormSchema = z.object(baseFields);

interface EmployeeFormProps {
  // 指定時は編集モード（初期値を埋める）。未指定は新規登録モード。
  employee?: Employee;
  onSuccess: () => void;
}

type FieldErrors = Partial<Record<"name" | "email" | "password" | "role", string>>;

/** 社員の登録／編集を兼ねるフォーム。ADMIN のみが到達する画面。 */
export function EmployeeForm({ employee, onSuccess }: EmployeeFormProps) {
  const isEdit = employee !== undefined;
  const [name, setName] = useState(employee?.name ?? "");
  const [email, setEmail] = useState(employee?.email ?? "");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>(employee?.role ?? "MEMBER");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    setFormError(null);

    const parsed = isEdit
      ? editFormSchema.safeParse({ name, email, role })
      : createFormSchema.safeParse({ name, email, password, role });
    if (!parsed.success) {
      const errors: FieldErrors = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0];
        if (key === "name" || key === "email" || key === "password" || key === "role") {
          errors[key] = issue.message;
        }
      }
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    try {
      if (isEdit) {
        await updateEmployee(employee.id, { name, email, role });
      } else {
        await createEmployee({ name, email, password, role });
      }
      onSuccess();
    } catch (error) {
      setFormError(toUserMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex max-w-md flex-col gap-4">
      {formError && (
        <p role="alert" className="rounded bg-red-100 px-3 py-2 text-sm text-red-700">
          {formError}
        </p>
      )}

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">氏名</span>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="rounded border border-slate-300 px-3 py-2"
        />
        {fieldErrors.name && <span className="text-xs text-red-600">{fieldErrors.name}</span>}
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">メールアドレス</span>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="rounded border border-slate-300 px-3 py-2"
        />
        {fieldErrors.email && <span className="text-xs text-red-600">{fieldErrors.email}</span>}
      </label>

      {!isEdit && (
        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium">初期パスワード</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2"
          />
          {fieldErrors.password && (
            <span className="text-xs text-red-600">{fieldErrors.password}</span>
          )}
        </label>
      )}

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">ロール</span>
        <select
          value={role}
          onChange={(e) => setRole(e.target.value as Role)}
          className="rounded border border-slate-300 px-3 py-2"
        >
          <option value="MEMBER">MEMBER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
      </label>

      <button
        type="submit"
        disabled={submitting}
        className="rounded bg-slate-800 px-4 py-2 text-white disabled:opacity-50"
      >
        {submitting ? "送信中…" : isEdit ? "更新する" : "登録する"}
      </button>
    </form>
  );
}

/** API エラーをユーザー向けメッセージに変換する（生のエラーは出さない）。 */
function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 409:
        return "このメールアドレスは既に登録されています";
      case 400:
        return "入力内容に誤りがあります";
      case 403:
        return "この操作を行う権限がありません（管理者のみ）";
      case 401:
        return "ログインが必要です";
      case 404:
        return "対象の社員が見つかりません";
      default:
        return "処理に失敗しました。時間をおいて再度お試しください";
    }
  }
  return "予期しないエラーが発生しました";
}
