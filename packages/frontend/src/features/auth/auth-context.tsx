"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import type { ReactNode } from "react";
import { apiFetch } from "@/lib/api-client";
import type { AuthUser } from "./types";
import { AuthResponseSchema } from "./types";

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch<unknown>("/api/auth/me")
      .then((data) => {
        const parsed = AuthResponseSchema.safeParse(data);
        if (parsed.success) {
          setUser(parsed.data);
        }
      })
      .catch(() => {
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const data = await apiFetch<unknown>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    const parsed = AuthResponseSchema.parse(data);
    setUser(parsed);
  }, []);

  const logout = useCallback(async () => {
    await apiFetch<unknown>("/api/auth/logout", { method: "POST" });
    setUser(null);
  }, []);

  return (
    <AuthContext value={{ user, loading, login, logout }}>
      {children}
    </AuthContext>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}
