"use client";

import { useEffect, type ReactNode } from "react";

/** Dark, full-viewport chrome for the kitchen display and its PWA service worker. */
export function KitchenShell({ children }: { children: ReactNode }) {
  useEffect(() => {
    // Dev builds change constantly; caching them would serve stale code.
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/kitchen-sw.js", { scope: "/kitchen" }).catch(() => undefined);
  }, []);

  return (
    <div className="min-h-dvh select-none bg-stone-950 text-stone-100">{children}</div>
  );
}
