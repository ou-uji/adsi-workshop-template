// Unit A: ユーザー管理の API 層。
// すべて apiFetch（= withBasePath 適用）経由。レスポンスは zod でランタイム検証する（any 禁止）。

import { z } from "zod";
import { apiFetch } from "@/lib/api-client";

export const roleSchema = z.enum(["ADMIN", "MEMBER"]);
export type Role = z.infer<typeof roleSchema>;

// backend EmployeeResponse に対応（passwordHash は返さない）。
export const employeeSchema = z.object({
  id: z.number(),
  name: z.string(),
  email: z.string(),
  role: roleSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Employee = z.infer<typeof employeeSchema>;

export const employeeListSchema = z.array(employeeSchema);

export interface CreateEmployeeInput {
  name: string;
  email: string;
  password: string;
  role: Role;
}

export interface UpdateEmployeeInput {
  name: string;
  email: string;
  role: Role;
}

const EMPLOYEES_PATH = "/api/employees";

/** 社員一覧を取得する（ADMIN のみ）。 */
export async function fetchEmployees(): Promise<Employee[]> {
  const data = await apiFetch<unknown>(EMPLOYEES_PATH);
  return employeeListSchema.parse(data);
}

/** 社員 1 件を取得する（編集フォーム初期表示）。 */
export async function fetchEmployee(id: number): Promise<Employee> {
  const data = await apiFetch<unknown>(`${EMPLOYEES_PATH}/${id}`);
  return employeeSchema.parse(data);
}

/** 社員を登録する。 */
export async function createEmployee(input: CreateEmployeeInput): Promise<Employee> {
  const data = await apiFetch<unknown>(EMPLOYEES_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
  return employeeSchema.parse(data);
}

/** 社員を編集する。 */
export async function updateEmployee(id: number, input: UpdateEmployeeInput): Promise<Employee> {
  const data = await apiFetch<unknown>(`${EMPLOYEES_PATH}/${id}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
  return employeeSchema.parse(data);
}
