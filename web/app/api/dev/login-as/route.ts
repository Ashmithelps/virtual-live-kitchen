import { NextResponse } from "next/server";
import type { components } from "@/lib/api/schema";
import { SESSION_POLICY, callBackend, forwardError, startSession } from "@/lib/auth/session-server";
import { DEV_ACCOUNTS } from "@/lib/dev/accounts";
import type { Role } from "@/lib/auth/roles";

/**
 * Dev role switcher: logs in as one of the dev-seed accounts. Exists only under `next dev`; production
 * builds answer 404 and never read DEV_SEED_PASSWORD.
 */
export async function POST(request: Request) {
  if (process.env.NODE_ENV !== "development") {
    return NextResponse.json({ status: 404, error: "Not Found", message: "Not found" }, { status: 404 });
  }
  const password = process.env.DEV_SEED_PASSWORD;
  if (!password) {
    return NextResponse.json(
      { status: 500, error: "Internal Server Error", message: "Set DEV_SEED_PASSWORD in web/.env.local (same value as the backend's)." },
      { status: 500 },
    );
  }
  const body = (await request.json().catch(() => null)) as { role?: Role } | null;
  const account = body?.role ? DEV_ACCOUNTS[body.role] : undefined;
  if (!account) {
    return NextResponse.json({ status: 400, error: "Bad Request", message: "Unknown role" }, { status: 400 });
  }
  const response = await callBackend("/api/auth/login", { phone: account.phone, password });
  if (!response.ok) return forwardError(response);
  return startSession(SESSION_POLICY, (await response.json()) as components["schemas"]["AuthResponse"]);
}
