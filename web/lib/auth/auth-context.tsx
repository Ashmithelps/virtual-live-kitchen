"use client";

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/errors";
import type { User } from "@/lib/api/types";
import { session as defaultStore, type Session, type SessionStore } from "./token-store";

type AuthState =
  | { status: "loading"; user: null }
  | { status: "authenticated"; user: User }
  | { status: "anonymous"; user: null };

export type Registration = {
  name: string;
  phone: string;
  email?: string;
  password: string;
  role?: "CUSTOMER" | "RESTAURANT_OWNER";
};

type AuthContextValue = AuthState & {
  login(phone: string, password: string): Promise<User>;
  register(input: Registration): Promise<User>;
  /** Re-reads the account (e.g. after an admin approved this owner). */
  reload(): Promise<void>;
  /** Signs in with a session obtained elsewhere (the dev role switcher). */
  adopt(session: Session): User;
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

/** Login state for the whole app (one session for every role). */
export function AuthProvider({ store = defaultStore, children }: { store?: SessionStore; children: ReactNode }) {
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

  const value = useMemo<AuthContextValue>(() => {
    const start = (session: Session) => {
      // Another account may have been signed in: drop its cached data.
      queryClient.clear();
      store.setSession(session);
      return session.user;
    };
    return {
      ...state,
      async login(phone, password) {
        return start(await sessionRequest(store.basePath, { phone, password }));
      },
      async register(input) {
        return start(await sessionRequest(`${store.basePath}/register`, input));
      },
      adopt: start,
      async reload() {
        await store.refreshSession();
      },
      async logout() {
        await fetch(store.basePath, { method: "DELETE" }).catch(() => undefined);
        store.setSession(null);
        queryClient.clear();
      },
    };
  }, [state, queryClient, store]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside <AuthProvider>");
  return value;
}
