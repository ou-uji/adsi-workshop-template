import { z } from "zod/v4";

export const AttendanceStatus = z.enum(["NOT_CLOCKED", "CLOCKED_IN", "CLOCKED_OUT"]);

export const AttendanceResponseSchema = z.object({
  id: z.number().nullable(),
  employeeId: z.number(),
  workDate: z.string(),
  clockInAt: z.string().nullable(),
  clockOutAt: z.string().nullable(),
  status: AttendanceStatus,
});

export type AttendanceRecord = z.infer<typeof AttendanceResponseSchema>;
export type AttendanceStatusType = z.infer<typeof AttendanceStatus>;
