"use client";

import createClient from "openapi-fetch";
import type { components, paths } from "@/lib/api/schema";
import { API_URL } from "@/lib/api/client";
import { readJson, useStoredJson, writeJson } from "@/lib/storage";

/** What the phone keeps after pairing. The token is the station's only credential (no user login). */
export type PairedStation = { deviceToken: string } & components["schemas"]["StationInfo"];

const KEY = "vk.station.device";

export function storePairing(paired: components["schemas"]["PairedDevice"]): void {
  writeJson(KEY, { deviceToken: paired.deviceToken, ...paired.station } satisfies PairedStation);
}

export function forgetPairing(): void {
  writeJson(KEY, null);
}

export function usePairedStation(): PairedStation | null {
  return useStoredJson<PairedStation>(KEY);
}

export function currentDeviceToken(): string | null {
  return readJson<PairedStation>(KEY)?.deviceToken ?? null;
}

/** Typed client for the device endpoints; adds X-Device-Token from storage. */
export const stationApi = createClient<paths>({ baseUrl: API_URL });
stationApi.use({
  onRequest({ request }) {
    const token = currentDeviceToken();
    if (token) request.headers.set("X-Device-Token", token);
    return request;
  },
});
