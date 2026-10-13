import { describe, expect, it } from "vitest";
import { backoffMs, chunksForWindow, expiredChunkIds, pinnedChunkIds, readyToUpload, type ChunkMeta, type PendingUpload } from "./buffer";
import { bestSample, computeOffset } from "./clock";

const MIN = 60_000;
const chunk = (id: string, startMin: number): ChunkMeta => ({ id, startMs: startMin * MIN, endMs: (startMin + 1) * MIN, sizeBytes: 1, mimeType: "video/webm" });
const upload = (startMs: number, endMs: number, extra: Partial<PendingUpload> = {}): PendingUpload => ({
  orderId: "o",
  clipRequestId: "c",
  displayNumber: 1,
  windowStartMs: startMs,
  windowEndMs: endMs,
  uploadedKeys: {},
  attempts: 0,
  nextAttemptAtMs: 0,
  lastError: null,
  createdAtMs: 0,
  ...extra,
});

describe("station buffer", () => {
  const chunks = [chunk("a", 0), chunk("b", 1), chunk("c", 2), chunk("d", 3)];

  it("selects every chunk overlapping the window, in time order", () => {
    // 1:30 .. 2:10 touches b (1–2) and c (2–3)
    expect(chunksForWindow(chunks, upload(1.5 * MIN, 2.17 * MIN)).map((c) => c.id)).toEqual(["b", "c"]);
    // touching edges only is not overlap
    expect(chunksForWindow(chunks, upload(1 * MIN, 2 * MIN)).map((c) => c.id)).toEqual(["b"]);
  });

  it("deletes chunks past retention unless a pending upload needs them", () => {
    const now = 50 * MIN; // 45-minute retention → anything ending before minute 5 is old
    const pinned = pinnedChunkIds(chunks, [upload(2.5 * MIN, 2.6 * MIN)]);
    expect([...pinned]).toEqual(["c"]);
    expect(expiredChunkIds(chunks, now, pinned)).toEqual(["a", "b", "d"]);
    expect(expiredChunkIds(chunks, 10 * MIN, new Set())).toEqual([]);
  });

  it("waits for the chunk that holds the end of the window while recording", () => {
    const u = upload(2.5 * MIN, 4.2 * MIN); // ends inside the chunk still being recorded (4–5)
    expect(readyToUpload(u, chunks, true, 4.5 * MIN)).toBe(false);
    expect(readyToUpload(u, [...chunks, chunk("e", 4)], true, 5 * MIN)).toBe(true);
    expect(readyToUpload(u, chunks, false, 4.5 * MIN)).toBe(true); // not recording: send what exists
    expect(readyToUpload({ ...u, nextAttemptAtMs: 9 * MIN }, [...chunks, chunk("e", 4)], true, 5 * MIN)).toBe(false); // backing off
  });

  it("backs off exponentially up to five minutes", () => {
    expect([1, 2, 3, 4].map(backoffMs)).toEqual([5_000, 10_000, 20_000, 40_000]);
    expect(backoffMs(20)).toBe(300_000);
  });
});

describe("clock sync", () => {
  it("assumes the server stamped halfway through the round trip", () => {
    expect(computeOffset(1_000, 1_200, 5_100)).toEqual({ offsetMs: 4_000, rttMs: 200 });
  });

  it("prefers the fastest round trip", () => {
    expect(bestSample([{ offsetMs: 10, rttMs: 300 }, { offsetMs: 4, rttMs: 40 }, { offsetMs: 7, rttMs: 90 }])).toEqual({ offsetMs: 4, rttMs: 40 });
    expect(bestSample([])).toBeNull();
  });
});
