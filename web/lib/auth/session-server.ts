import "server-only";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import type { components } from "@/lib/api/schema";

type AuthResponse = components["schemas"]["AuthResponse"];

/**
 * Server half of the session: the refresh token lives in an httpOnly cookie scoped to
 * /api/session, so browser JavaScript can never read it. Only the short-lived access token is
 * handed to the page.
 */
const BACKEND = process.env.API_URL ?? process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type Role = AuthResponse["user"]["role"];

/** Which accounts a session accepts and where its refresh cookie lives. */
export type SessionPolicy = { cookie: string; path: string; roles: readonly Role[]; refusal: string };

/** One session for every role; pages decide what each role may see. */
export const SESSION_POLICY: SessionPolicy = {
  cookie: "vk_rt",
  path: "/api/session",
  roles: ["CUSTOMER", "RESTAURANT_OWNER", "CHEF", "ADMIN"],
  refusal: "This account can't sign in here.",
};

export async function callBackend(path: string, body: unknown): Promise<Response> {
  return fetch(`${BACKEND}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
  });
}

/** Passes a backend error through unchanged (same status, same ErrorResponse body). */
export async function forwardError(response: Response): Promise<NextResponse> {
  const body = await response.json().catch(() => ({ message: `Request failed (${response.status})` }));
  return NextResponse.json(body, { status: response.status });
}

/** Sets the refresh cookie and returns only what the page needs, if the account suits this app. */
export async function startSession(policy: SessionPolicy, auth: AuthResponse, status = 200): Promise<NextResponse> {
  if (!policy.roles.includes(auth.user.role)) {
    // Don't keep a session this app can't use; revoke the refresh token we just got.
    await callBackend("/api/auth/logout", { refreshToken: auth.refreshToken }).catch(() => undefined);
    return NextResponse.json({ status: 403, error: "Forbidden", message: policy.refusal }, { status: 403 });
  }
  (await cookies()).set(policy.cookie, auth.refreshToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: policy.path,
    expires: new Date(auth.refreshTokenExpiresAt),
  });
  return NextResponse.json(
    { accessToken: auth.accessToken, accessTokenExpiresAt: auth.accessTokenExpiresAt, user: auth.user },
    { status },
  );
}

export async function readRefreshCookie(policy: SessionPolicy): Promise<string | undefined> {
  return (await cookies()).get(policy.cookie)?.value;
}

export async function clearRefreshCookie(policy: SessionPolicy): Promise<void> {
  (await cookies()).set(policy.cookie, "", { httpOnly: true, path: policy.path, maxAge: 0 });
}

type Auth = AuthResponse;

/** Route handlers: log in, log out, refresh. */
export function sessionHandlers(policy: SessionPolicy) {
  return {
    async login(request: Request) {
      const body = await request.json().catch(() => null);
      const response = await callBackend("/api/auth/login", { phone: body?.phone, password: body?.password });
      if (!response.ok) return forwardError(response);
      return startSession(policy, (await response.json()) as Auth);
    },
    async logout() {
      const refreshToken = await readRefreshCookie(policy);
      if (refreshToken) {
        await callBackend("/api/auth/logout", { refreshToken }).catch(() => undefined);
      }
      await clearRefreshCookie(policy);
      return new NextResponse(null, { status: 204 });
    },
    async refresh() {
      const refreshToken = await readRefreshCookie(policy);
      if (!refreshToken) {
        return NextResponse.json({ status: 401, error: "Unauthorized", message: "Not logged in" }, { status: 401 });
      }
      const response = await callBackend("/api/auth/refresh", { refreshToken });
      if (!response.ok) {
        await clearRefreshCookie(policy);
        return NextResponse.json({ status: 401, error: "Unauthorized", message: "Session expired" }, { status: 401 });
      }
      return startSession(policy, (await response.json()) as Auth);
    },
  };
}
