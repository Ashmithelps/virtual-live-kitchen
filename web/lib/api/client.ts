import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./schema";
import { customerSession, kitchenSession, type SessionStore } from "@/lib/auth/token-store";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function bearerAuth(store: SessionStore): Middleware {
  return {
    async onRequest({ request }) {
      if (request.headers.has("Authorization")) return request;
      const token = await store.freshAccessToken();
      if (token) request.headers.set("Authorization", `Bearer ${token}`);
      return request;
    },
  };
}

function createApi(store: SessionStore) {
  const client = createClient<paths>({ baseUrl: API_URL });
  client.use(bearerAuth(store));
  return client;
}

/** Typed clients generated from the backend's OpenAPI spec (lib/api/schema.ts). */
export const api = createApi(customerSession);
export const kitchenApi = createApi(kitchenSession);
