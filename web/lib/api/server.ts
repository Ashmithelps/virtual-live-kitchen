import "server-only";
import createClient from "openapi-fetch";
import { cache } from "react";
import { proxyHeaders } from "@/lib/server/proxy-headers";
import type { components, paths } from "./schema";
import type { PublicChef, PublicClip } from "./types";

/** Typed client for server components and route handlers (public endpoints only; no session). */
const serverApi = createClient<paths>({ baseUrl: process.env.API_URL ?? process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080" });
serverApi.use({
  async onRequest({ request }) {
    for (const [name, value] of Object.entries(await proxyHeaders())) request.headers.set(name, value);
    return request;
  },
});

export type Loaded<T> = { ok: true; data: T } | { ok: false; status: number; message: string };

async function load<T>(request: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<Loaded<T>> {
  try {
    const { data, error, response } = await request;
    if (data !== undefined && response.ok) return { ok: true, data };
    const message = (error as { message?: string } | undefined)?.message ?? `Request failed (${response.status})`;
    return { ok: false, status: response.status, message };
  } catch {
    return { ok: false, status: 503, message: "The service is unavailable. Try again shortly." };
  }
}

/** Deduplicated per request, so generateMetadata and the page share one backend call. */
export const loadPublicClip = cache((token: string) =>
  load<PublicClip>(serverApi.GET("/api/public/clips/{token}", { params: { path: { token } }, cache: "no-store" })),
);

export const loadPublicChef = cache((chefId: string) =>
  load<PublicChef>(serverApi.GET("/api/public/chefs/{chefId}", { params: { path: { chefId } }, cache: "no-store" })),
);

export type RecordingConsent = components["schemas"]["PublicConsentResponse"];

export const loadRecordingConsent = cache(() =>
  load<RecordingConsent>(serverApi.GET("/api/public/legal/recording-consent", { next: { revalidate: 3600 } })),
);
