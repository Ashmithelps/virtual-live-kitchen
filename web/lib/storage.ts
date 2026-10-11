"use client";

import { useSyncExternalStore } from "react";

/**
 * Tiny JSON-in-localStorage store that React can subscribe to. Snapshots are cached per raw
 * string so useSyncExternalStore gets a stable reference until the value actually changes.
 * Every access is guarded: storage can be unavailable (private mode, blocked site data).
 */
const EVENT = "vk-storage";
const cache = new Map<string, { raw: string | null; value: unknown }>();

function rawGet(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function readJson<T>(key: string): T | null {
  if (typeof window === "undefined") return null;
  const raw = rawGet(key);
  const hit = cache.get(key);
  if (hit && hit.raw === raw) return hit.value as T | null;
  let value: unknown = null;
  try {
    value = raw === null ? null : JSON.parse(raw);
  } catch {
    value = null;
  }
  cache.set(key, { raw, value });
  return value as T | null;
}

export function writeJson(key: string, value: unknown): void {
  try {
    if (value === null || value === undefined) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage unavailable: the app still works for this page view.
  }
  window.dispatchEvent(new Event(EVENT));
}

function subscribe(callback: () => void): () => void {
  window.addEventListener("storage", callback); // other tabs
  window.addEventListener(EVENT, callback); // this tab
  return () => {
    window.removeEventListener("storage", callback);
    window.removeEventListener(EVENT, callback);
  };
}

/** Current stored value; null during server render and on first client render. */
export function useStoredJson<T>(key: string): T | null {
  return useSyncExternalStore(subscribe, () => readJson<T>(key), () => null);
}

/** False during server render / hydration, true afterwards (to avoid flashing "empty cart"). */
export function useHydrated(): boolean {
  return useSyncExternalStore(subscribe, () => true, () => false);
}
