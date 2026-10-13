import type { components } from "@/lib/api/schema";
import { SESSION_POLICY, callBackend, forwardError, startSession } from "@/lib/auth/session-server";

const SELF_SERVICE_ROLES = new Set(["CUSTOMER", "RESTAURANT_OWNER"]);

/**
 * Sign-up for customers (also claims a guest account made earlier with the same phone) and restaurant
 * owners (PENDING_APPROVAL until an admin approves them).
 */
export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const role = SELF_SERVICE_ROLES.has(body?.role) ? body.role : "CUSTOMER";
  const response = await callBackend("/api/auth/register", {
    name: body?.name,
    phone: body?.phone,
    email: body?.email || null,
    password: body?.password,
    role,
  });
  if (!response.ok) return forwardError(response);
  return startSession(SESSION_POLICY, (await response.json()) as components["schemas"]["AuthResponse"], 201);
}
