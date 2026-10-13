"use client";

import { useEffect, type ReactNode } from "react";

/** Black full-screen chrome and the station PWA's service worker. No user login here. */
export function StationShell({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/station-sw.js", { scope: "/station" }).catch(() => undefined);
  }, []);
  return <div className="min-h-dvh select-none bg-black text-stone-100">{children}</div>;
}
