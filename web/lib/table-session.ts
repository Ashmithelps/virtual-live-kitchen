"use client";

import type { TableLookup } from "@/lib/api/types";
import { useStoredJson, writeJson } from "@/lib/storage";

/**
 * Saved when a table QR is scanned. While it is valid the restaurant page is in dine-in mode and
 * checkout is locked to this table; the token itself is what the backend pins the order to.
 */
export type TableSession = {
  restaurantId: string;
  restaurantSlug: string;
  restaurantName: string;
  tableLabel: string;
  token: string;
  expiresAt: string;
};

const key = (slug: string) => `vk.table.${slug}`;

export function saveTableSession(lookup: TableLookup): void {
  const session: TableSession = {
    restaurantId: lookup.restaurantId,
    restaurantSlug: lookup.restaurantSlug,
    restaurantName: lookup.restaurantName,
    tableLabel: lookup.tableLabel,
    token: lookup.tableSessionToken,
    expiresAt: lookup.tableSessionExpiresAt,
  };
  writeJson(key(lookup.restaurantSlug), session);
}

export function clearTableSession(slug: string): void {
  writeJson(key(slug), null);
}

export function isSessionActive(session: TableSession | null, now = Date.now()): session is TableSession {
  return !!session && typeof session.token === "string" && new Date(session.expiresAt).getTime() > now;
}

/** The active table session for a restaurant, or null (none, or expired). */
export function useTableSession(slug: string): TableSession | null {
  const session = useStoredJson<TableSession>(key(slug));
  return isSessionActive(session) ? session : null;
}
