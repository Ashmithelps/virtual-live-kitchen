import "server-only";
import { headers } from "next/headers";

/**
 * Headers for calls from this web server (Vercel) to the backend on behalf of a visitor. The backend rate-limits
 * public endpoints per client IP; without these every visitor would share Vercel's address. The secret proves the
 * header comes from us (backend: BACKEND_PROXY_SECRET). Vercel overwrites x-forwarded-for with the real client IP.
 */
export async function proxyHeaders(): Promise<Record<string, string>> {
  const secret = process.env.BACKEND_PROXY_SECRET;
  if (!secret) return {};
  let ip: string | undefined;
  try {
    const h = await headers();
    ip = h.get("x-real-ip") ?? h.get("x-forwarded-for")?.split(",")[0]?.trim();
  } catch {
    // Outside a request (build time): nothing to forward.
  }
  return ip ? { "X-Proxy-Secret": secret, "X-Client-IP": ip } : {};
}
