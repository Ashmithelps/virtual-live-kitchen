"use client";

import { readJson, useStoredJson, writeJson } from "@/lib/storage";

/**
 * Guest checkout returns a token that opens only that one order. We keep it (and a little display
 * info) on this device so the guest can come back to the tracking page.
 */
export type GuestOrderRef = {
  orderId: string;
  token: string;
  expiresAt: string;
  displayNumber: number;
  restaurantName: string;
  placedAt: string;
};

const KEY = "vk.guestOrders";
const MAX_KEPT = 20;

export function saveGuestOrder(ref: GuestOrderRef): void {
  const current = readJson<GuestOrderRef[]>(KEY) ?? [];
  writeJson(KEY, [ref, ...current.filter((r) => r.orderId !== ref.orderId)].slice(0, MAX_KEPT));
}

export function activeGuestOrders(refs: GuestOrderRef[] | null, now = Date.now()): GuestOrderRef[] {
  return (refs ?? []).filter((r) => new Date(r.expiresAt).getTime() > now);
}

export function useGuestOrders(): GuestOrderRef[] {
  return activeGuestOrders(useStoredJson<GuestOrderRef[]>(KEY));
}

export function useGuestToken(orderId: string): string | null {
  return useGuestOrders().find((r) => r.orderId === orderId)?.token ?? null;
}
