/**
 * Server-clock estimate (NTP-style): the server's timestamp is assumed to be taken halfway through
 * the round trip, so offset = server − (sent + received) / 2. Error is at most half the RTT.
 */
export function computeOffset(sentAtMs: number, receivedAtMs: number, serverMs: number): { offsetMs: number; rttMs: number } {
  const rttMs = receivedAtMs - sentAtMs;
  return { offsetMs: Math.round(serverMs - (sentAtMs + receivedAtMs) / 2), rttMs };
}

/** Of several samples, trust the one with the shortest round trip (least network noise). */
export function bestSample(samples: { offsetMs: number; rttMs: number }[]): { offsetMs: number; rttMs: number } | null {
  return samples.reduce<{ offsetMs: number; rttMs: number } | null>((best, s) => (!best || s.rttMs < best.rttMs ? s : best), null);
}
