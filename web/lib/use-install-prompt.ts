"use client";

import { useEffect, useState } from "react";

type InstallEvent = Event & { prompt(): Promise<void>; userChoice: Promise<{ outcome: "accepted" | "dismissed" }> };

/**
 * Chrome/Edge/Android fire `beforeinstallprompt` when a PWA is installable (HTTPS or localhost, manifest,
 * service worker). Returns an install function while that's possible, else null (already installed,
 * iOS, or not installable; then the browser menu's "Add to Home screen" still works).
 */
export function useInstallPrompt(): (() => Promise<void>) | null {
  const [event, setEvent] = useState<InstallEvent | null>(null);
  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault();
      setEvent(e as InstallEvent);
    };
    const onInstalled = () => setEvent(null);
    window.addEventListener("beforeinstallprompt", onPrompt);
    window.addEventListener("appinstalled", onInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);
  if (!event) return null;
  return async () => {
    await event.prompt();
    await event.userChoice.catch(() => undefined);
    setEvent(null);
  };
}
