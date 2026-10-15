"use client";

import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";

/** Last-resort error page (the root layout itself failed). Reports to Sentry when it is configured. */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <html lang="en-IN">
      <body style={{ fontFamily: "system-ui, sans-serif", padding: "2rem", textAlign: "center" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700 }}>Something went wrong</h1>
        <p style={{ color: "#57534e" }}>Please try again. If it keeps happening, contact support.</p>
        <button onClick={reset} style={{ marginTop: "1rem", padding: "0.5rem 1rem", borderRadius: "0.75rem", background: "#ea580c", color: "white" }}>
          Try again
        </button>
      </body>
    </html>
  );
}
