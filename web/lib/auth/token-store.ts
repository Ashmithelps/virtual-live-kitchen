import type { User } from "@/lib/api/types";

/**
 * One login session (customer app or kitchen app). The access token lives only in memory, never
 * in localStorage. The 30-day refresh token is an httpOnly cookie owned by the matching
 * route handlers under `basePath`, so page scripts can't read it.
 */
export type Session = { accessToken: string; accessTokenExpiresAt: string; user: User };

export type SessionStore = {
  /** e.g. "/api/session": POST = log in, DELETE = log out, POST /refresh, POST /register */
  readonly basePath: string;
  getSession(): Session | null;
  setSession(session: Session | null): void;
  onSessionChange(listener: (session: Session | null) => void): () => void;
  refreshSession(): Promise<Session | null>;
  freshAccessToken(): Promise<string | null>;
};

export function createSessionStore(basePath: string, lockName: string): SessionStore {
  let current: Session | null = null;
  let refreshing: Promise<Session | null> | null = null;
  const listeners = new Set<(session: Session | null) => void>();

  const setSession = (session: Session | null) => {
    current = session;
    listeners.forEach((listener) => listener(session));
  };

  /**
   * Refresh tokens are single-use, so two tabs refreshing with the same cookie would trip the
   * backend's reuse detection and log the user out everywhere. The Web Locks API serialises
   * refreshes across tabs; the second tab then sends the rotated cookie. Within a tab,
   * concurrent callers share one in-flight request.
   */
  const refreshSession = (): Promise<Session | null> => {
    if (!refreshing) {
      const run = async () => {
        const response = await fetch(`${basePath}/refresh`, { method: "POST" });
        const session = response.ok ? ((await response.json()) as Session) : null;
        setSession(session);
        return session;
      };
      const locked = async (): Promise<Session | null> =>
        typeof navigator !== "undefined" && "locks" in navigator ? await navigator.locks.request(lockName, run) : await run();
      refreshing = locked().finally(() => {
        refreshing = null;
      });
    }
    return refreshing;
  };

  return {
    basePath,
    getSession: () => current,
    setSession,
    onSessionChange(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    refreshSession,
    /** A usable access token, refreshed first if it expires within 30 seconds. */
    async freshAccessToken() {
      const session = current;
      if (!session) return null;
      if (new Date(session.accessTokenExpiresAt).getTime() - Date.now() > 30_000) return session.accessToken;
      return (await refreshSession())?.accessToken ?? null;
    },
  };
}

/** Customers on the ordering site. */
export const customerSession = createSessionStore("/api/session", "vk-session-refresh");

/** Chefs and owners on the kitchen display; a separate cookie, so both can be signed in on one device. */
export const kitchenSession = createSessionStore("/api/kitchen/session", "vk-kitchen-session-refresh");
