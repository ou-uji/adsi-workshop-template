import { z } from "zod/v4";

export const AuthResponseSchema = z.object({
  id: z.number(),
  name: z.string(),
  email: z.string(),
  role: z.enum(["ADMIN", "MEMBER"]),
});

export type AuthUser = z.infer<typeof AuthResponseSchema>;
