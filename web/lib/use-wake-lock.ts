"use client";

import { useEffect } from "react";

/** Keeps the screen on while mounted (re-acquired whenever the page becomes visible again). */
export function useWakeLock(enabled = true): void {
  useEffect(() => {
    if (!enabled || !("wakeLock" in navigator)) return;
    let lock: WakeLockSentinel | null = null;
    const acquire = () => {
      if (document.visibilityState === "visible") {
        navigator.wakeLock.request("screen").then((l) => (lock = l)).catch(() => undefined);
      }
    };
    acquire();
    document.addEventListener("visibilitychange", acquire);
    return () => {
      document.removeEventListener("visibilitychange", acquire);
      void lock?.release();
    };
  }, [enabled]);
}
