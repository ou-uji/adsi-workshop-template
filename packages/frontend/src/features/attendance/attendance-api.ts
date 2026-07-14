import { apiFetch } from "@/lib/api-client";
import { AttendanceResponseSchema, type AttendanceRecord } from "./types";
import { z } from "zod/v4";

const AttendanceListSchema = z.array(AttendanceResponseSchema);

export async function clockIn(): Promise<AttendanceRecord> {
  const data = await apiFetch<unknown>("/api/attendance/clock-in", {
    method: "POST",
  });
  return AttendanceResponseSchema.parse(data);
}

export async function clockOut(): Promise<AttendanceRecord> {
  const data = await apiFetch<unknown>("/api/attendance/clock-out", {
    method: "POST",
  });
  return AttendanceResponseSchema.parse(data);
}

export async function getTodayStatus(): Promise<AttendanceRecord> {
  const data = await apiFetch<unknown>("/api/attendance/today");
  return AttendanceResponseSchema.parse(data);
}

export async function getHistory(): Promise<AttendanceRecord[]> {
  const data = await apiFetch<unknown>("/api/attendance");
  return AttendanceListSchema.parse(data);
}
