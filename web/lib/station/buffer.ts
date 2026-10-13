/**
 * Pure rules for the station phone's footage buffer and upload queue (unit-tested). All times are
 * server-time epoch milliseconds.
 */

export const CHUNK_MS = 60_000;
export const RETENTION_MS = 45 * 60_000;

/** Metadata of one recorded chunk (the video bytes live in a separate IndexedDB store). */
export type ChunkMeta = { id: string; startMs: number; endMs: number; sizeBytes: number; mimeType: string };

/** A footage window the backend asked this station for. */
export type PendingUpload = {
  orderId: string;
  clipRequestId: string;
  displayNumber: number;
  windowStartMs: number;
  windowEndMs: number;
  /** chunkId → object key, for chunks already PUT (so a retry doesn't upload them again). */
  uploadedKeys: Record<string, string>;
  attempts: number;
  nextAttemptAtMs: number;
  lastError: string | null;
  createdAtMs: number;
};

export function overlaps(chunk: Pick<ChunkMeta, "startMs" | "endMs">, startMs: number, endMs: number): boolean {
  return chunk.startMs < endMs && chunk.endMs > startMs;
}

export function chunksForWindow(chunks: ChunkMeta[], upload: Pick<PendingUpload, "windowStartMs" | "windowEndMs">): ChunkMeta[] {
  return chunks.filter((c) => overlaps(c, upload.windowStartMs, upload.windowEndMs)).sort((a, b) => a.startMs - b.startMs);
}

/** Chunks a pending upload still needs; they must survive ring-buffer cleanup. */
export function pinnedChunkIds(chunks: ChunkMeta[], uploads: PendingUpload[]): Set<string> {
  const pinned = new Set<string>();
  for (const upload of uploads) {
    for (const chunk of chunksForWindow(chunks, upload)) pinned.add(chunk.id);
  }
  return pinned;
}

/** Chunks to delete: older than the retention window and not pinned by a pending upload. */
export function expiredChunkIds(chunks: ChunkMeta[], nowMs: number, pinned: ReadonlySet<string>, retentionMs = RETENTION_MS): string[] {
  return chunks.filter((c) => c.endMs < nowMs - retentionMs && !pinned.has(c.id)).map((c) => c.id);
}

/**
 * An upload can go once footage up to the window's end is safely in a finished chunk. While
 * recording, the chunk in progress may still hold the end of the window, so wait for it; when not
 * recording, upload whatever exists.
 */
export function readyToUpload(upload: PendingUpload, chunks: ChunkMeta[], recording: boolean, nowMs: number): boolean {
  if (upload.nextAttemptAtMs > nowMs) return false;
  if (!recording) return true;
  const latestEnd = chunks.reduce((max, c) => Math.max(max, c.endMs), 0);
  return latestEnd >= upload.windowEndMs;
}

/** 5 s, 10 s, 20 s … capped at 5 minutes. */
export function backoffMs(attempts: number): number {
  return Math.min(5_000 * 2 ** Math.max(0, attempts - 1), 5 * 60_000);
}
