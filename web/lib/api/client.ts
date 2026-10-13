import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./schema";
import { session, type SessionStore } from "@/lib/auth/token-store";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/**
 * Adds the bearer token, and on a 401 refreshes the session once and replays the request. If the
 * refresh fails the session is cleared, which sends guarded pages to /login.
 */
function bearerAuth(store: SessionStore): Middleware {
  const replayable = new Map<string, Request>();
  return {
    async onRequest({ request, id }) {
      if (request.headers.has("Authorization")) return request;
      const token = await store.freshAccessToken();
      if (!token) return request;
      request.headers.set("Authorization", `Bearer ${token}`);
      replayable.set(id, request.clone());
      return request;
    },
    async onResponse({ response, id }) {
      const original = replayable.get(id);
      replayable.delete(id);
      if (response.status !== 401 || !original) return response;
      const refreshed = await store.refreshSession().catch(() => null);
      if (!refreshed) return response;
      const retry = new Request(original, { headers: new Headers(original.headers) });
      retry.headers.set("Authorization", `Bearer ${refreshed.accessToken}`);
      return fetch(retry);
    },
    onError({ id }) {
      replayable.delete(id);
    },
  };
}

/** Typed client generated from the backend's OpenAPI spec (lib/api/schema.ts). */
export const api = (() => {
  const client = createClient<paths>({ baseUrl: API_URL });
  client.use(bearerAuth(session));
  return client;
})();
