"use client";

import { useEffect, type ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-context";
import { kitchenSession } from "@/lib/auth/token-store";

/** Dark, full-viewport chrome for the kitchen display, its own session, and the PWA service worker. */
export function KitchenShell({ children }: { children: ReactNode }) {
  useEffect(() => {
    // Dev builds change constantly; caching them would serve stale code.
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/kitchen-sw.js", { scope: "/kitchen" }).catch(() => undefined);
  }, []);

  return (
    <AuthProvider store={kitchenSession}>
      <div className="min-h-dvh select-none bg-stone-950 text-stone-100">{children}</div>
    </AuthProvider>
  );
}
