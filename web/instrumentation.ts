import * as Sentry from "@sentry/nextjs";

export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") await import("./sentry.server.config");
  if (process.env.NEXT_RUNTIME === "edge") await import("./sentry.edge.config");
}

/** Server-side render/route errors go to Sentry (a no-op without a DSN). */
export const onRequestError = Sentry.captureRequestError;
