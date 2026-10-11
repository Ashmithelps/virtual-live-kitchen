"use client";

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/errors";
import type { User } from "@/lib/api/types";
import type { Session, SessionStore } from "./token-store";

type AuthState =
  | { status: "loading"; user: null }
  | { status: "authenticated"; user: User }
  | { status: "anonymous"; user: null };

type AuthContextValue = AuthState & {
  login(phone: string, password: string): Promise<void>;
  /** Customer app only. */
  register(input: { name: string; phone: string; email?: string; password: string }): Promise<void>;
  logout(): Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

async function sessionRequest(url: string, body: unknown): Promise<Session> {
  const response = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  const json = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status, json?.message ?? `Request failed (${response.status})`);
  return json as Session;
}

const toState = (session: Session | null): AuthState =>
  session ? { status: "authenticated", user: session.user } : { status: "anonymous", user: null };

/** Provides login state for one session store (customer site or kitchen display). */
export function AuthProvider({ store, children }: { store: SessionStore; children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<AuthState>(() => (store.getSession() ? toState(store.getSession()) : { status: "loading", user: null }));

  useEffect(() => {
    const unsubscribe = store.onSessionChange((session) => setState(toState(session)));
    // Restore the session from the refresh cookie on page load.
    if (!store.getSession()) {
      store.refreshSession().catch(() => store.setSession(null));
    }
    return unsubscribe;
  }, [store]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      async login(phone, password) {
        store.setSession(await sessionRequest(store.basePath, { phone, password }));
        queryClient.invalidateQueries();
      },
      async register(input) {
        store.setSession(await sessionRequest(`${store.basePath}/register`, input));
        queryClient.invalidateQueries();
      },
      async logout() {
        await fetch(store.basePath, { method: "DELETE" }).catch(() => undefined);
        store.setSession(null);
        queryClient.clear();
      },
    }),
    [state, queryClient, store],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside <AuthProvider>");
  return value;
}
