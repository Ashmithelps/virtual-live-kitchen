import type { components } from "@/lib/api/schema";
import { CUSTOMER_POLICY, callBackend, forwardError, startSession } from "@/lib/auth/session-server";

/** Customer sign-up (also claims a guest account created earlier with the same phone). */
export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const response = await callBackend("/api/auth/register", {
    name: body?.name,
    phone: body?.phone,
    email: body?.email || null,
    password: body?.password,
    role: "CUSTOMER",
  });
  if (!response.ok) return forwardError(response);
  return startSession(CUSTOMER_POLICY, (await response.json()) as components["schemas"]["AuthResponse"], 201);
}
