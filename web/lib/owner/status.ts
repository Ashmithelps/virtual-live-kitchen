import type { Restaurant } from "@/lib/api/types";

export const RESTAURANT_STATUS: Record<Restaurant["status"], { label: string; tone: "green" | "amber" | "red" | "stone" }> = {
  ACTIVE: { label: "Live", tone: "green" },
  PENDING_APPROVAL: { label: "Awaiting approval", tone: "amber" },
  SUSPENDED: { label: "Suspended", tone: "red" },
};

/** A station phone heartbeats every 30 s; allow a couple of missed beats before calling it offline. */
export const DEVICE_ONLINE_WITHIN_MS = 90_000;

export function deviceOnline(lastSeenAt: string | null, now: number): boolean {
  return lastSeenAt !== null && now - new Date(lastSeenAt).getTime() < DEVICE_ONLINE_WITHIN_MS;
}
