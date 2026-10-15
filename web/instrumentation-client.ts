// Sentry in the browser. Off unless NEXT_PUBLIC_SENTRY_DSN is set at build time.
import * as Sentry from "@sentry/nextjs";

const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

Sentry.init({
  dsn,
  enabled: Boolean(dsn),
  environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT ?? process.env.NEXT_PUBLIC_VERCEL_ENV ?? "development",
  tracesSampleRate: Number(process.env.NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE ?? 0),
  // No session replay; no cookies or IPs (sendDefaultPii) — customers' phones and chefs' faces stay out of Sentry.
  sendDefaultPii: false,
  ignoreErrors: [
    // Expected while a phone/tablet is offline; the app shows its own banner and retries.
    "Failed to fetch",
    "NetworkError when attempting to fetch resource.",
    "Load failed",
  ],
});

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
