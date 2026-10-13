"use client";

import { ApiError, errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { backoffMs, chunksForWindow, expiredChunkIds, pinnedChunkIds, readyToUpload, type ChunkMeta, type PendingUpload } from "./buffer";
import { bestSample, computeOffset } from "./clock";
import { chunkBlob, deleteChunks, enqueueUpload, listChunks, listUploads, removeUpload, saveChunk, saveUpload } from "./db";
import { stationApi } from "./device";
import { ChunkRecorder } from "./recorder";

type ClipWindow = components["schemas"]["ClipWindowResponse"];

export type StationSnapshot = {
  recording: boolean;
  recordingSinceMs: number | null;
  chunkCount: number;
  bufferBytes: number;
  bufferMinutes: number;
  uploads: PendingUpload[];
  lastUploaded: { displayNumber: number; atMs: number } | null;
  clock: { offsetMs: number; rttMs: number; syncedAtMs: number } | null;
  /** Last successful heartbeat to the backend (drives the owner's online/offline dot). */
  lastHeartbeatMs: number | null;
  /** For the debug panel. */
  chunks: ChunkMeta[];
  error: string | null;
};

const CLOCK_SYNC_MS = 5 * 60_000;
const UPLOAD_TICK_MS = 5_000;
const CLEANUP_TICK_MS = 60_000;
const HEARTBEAT_MS = 30_000;

/**
 * Everything the station phone does besides drawing the screen: clock sync, recording into the
 * IndexedDB ring buffer, cleanup, and the upload queue. React reads it via subscribe/getSnapshot.
 */
export class StationRuntime {
  private offsetMs = 0;
  private recorder: ChunkRecorder | null = null;
  private stream: MediaStream | null = null;
  private uploading = false;
  private timers: ReturnType<typeof setInterval>[] = [];
  private listeners = new Set<() => void>();
  private snapshot: StationSnapshot = {
    recording: false,
    recordingSinceMs: null,
    chunkCount: 0,
    bufferBytes: 0,
    bufferMinutes: 0,
    uploads: [],
    lastUploaded: null,
    clock: null,
    lastHeartbeatMs: null,
    chunks: [],
    error: null,
  };

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getSnapshot = () => this.snapshot;

  /** Device time corrected to server time; every chunk is stamped with this. */
  serverNow = () => Date.now() + this.offsetMs;

  startLoops(): void {
    if (this.timers.length > 0) return;
    void navigator.storage?.persist?.(); // ask the browser not to evict the buffer under pressure
    void this.syncClock();
    void this.refresh();
    this.timers.push(setInterval(() => void this.syncClock(), CLOCK_SYNC_MS));
    this.timers.push(setInterval(() => void this.uploadPass(), UPLOAD_TICK_MS));
    this.timers.push(setInterval(() => void this.cleanup(), CLEANUP_TICK_MS));
    void this.heartbeat();
    this.timers.push(setInterval(() => void this.heartbeat(), HEARTBEAT_MS));
  }

  /** Any device call marks the station's phone as seen; this keeps it "online" between orders. */
  async heartbeat(): Promise<void> {
    try {
      await unwrap(stationApi.GET("/api/station-device/me"));
      this.update({ lastHeartbeatMs: Date.now() });
    } catch {
      // the socket status and next beat tell the story
    }
  }

  /** "Retry now" on the screen: skip the backoff of every waiting upload. */
  async retryUploadsNow(): Promise<void> {
    const uploads = await listUploads();
    await Promise.all(uploads.map((u) => saveUpload({ ...u, nextAttemptAtMs: 0 })));
    await this.uploadPass();
  }

  dispose(): void {
    this.timers.forEach(clearInterval);
    this.timers = [];
    this.stopRecording();
  }

  // ── clock ────────────────────────────────────────────────────────────────

  /** Three quick samples of /api/time; the shortest round trip wins. */
  async syncClock(): Promise<void> {
    const samples = [];
    for (let i = 0; i < 3; i++) {
      try {
        const sent = Date.now();
        const { epochMillis } = await unwrap(stationApi.GET("/api/time"));
        samples.push(computeOffset(sent, Date.now(), epochMillis));
      } catch {
        // offline: keep the previous offset
      }
    }
    const best = bestSample(samples);
    if (best) {
      this.offsetMs = best.offsetMs;
      this.update({ clock: { ...best, syncedAtMs: Date.now() } });
    }
  }

  // ── recording ────────────────────────────────────────────────────────────

  startRecording(stream: MediaStream): void {
    this.stopRecording();
    this.stream = stream;
    this.recorder = new ChunkRecorder(
      stream,
      this.serverNow,
      (chunk) => {
        const meta: ChunkMeta = {
          // The backend prefixes keys with the start time; the id just has to be unique.
          id: crypto.randomUUID().replaceAll("-", "").slice(0, 16),
          startMs: chunk.startMs,
          endMs: chunk.endMs,
          sizeBytes: chunk.blob.size,
          mimeType: chunk.mimeType,
        };
        saveChunk(meta, chunk.blob)
          .then(() => this.refresh())
          .catch((e) => this.update({ error: `Couldn't store video (${errorMessage(e)}). Free up space on this phone.` }));
      },
      (error) => this.update({ error: `Recording failed: ${error instanceof Error ? error.message : "unknown error"}` }),
    );
    this.recorder.start();
    this.update({ recording: true, recordingSinceMs: Date.now(), error: null });
  }

  stopRecording(): void {
    this.recorder?.stop();
    this.recorder = null;
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    if (this.snapshot.recording) this.update({ recording: false, recordingSinceMs: null });
  }

  // ── windows & uploads ────────────────────────────────────────────────────

  async addWindow(window: ClipWindow): Promise<void> {
    await enqueueUpload({
      orderId: window.orderId,
      clipRequestId: window.clipRequestId,
      displayNumber: window.displayNumber,
      windowStartMs: Date.parse(window.windowStart),
      windowEndMs: Date.parse(window.windowEnd),
      uploadedKeys: {},
      attempts: 0,
      nextAttemptAtMs: 0,
      lastError: null,
      createdAtMs: Date.now(),
    });
    await this.refresh();
  }

  /** Windows pushed while this phone was offline. */
  async catchUp(): Promise<void> {
    try {
      const pending = await unwrap(stationApi.GET("/api/station-device/clip-requests"));
      for (const window of pending) await this.addWindow(window);
    } catch {
      // next reconnect tries again
    }
  }

  async uploadPass(): Promise<void> {
    if (this.uploading) return;
    this.uploading = true;
    try {
      const [uploads, chunks] = await Promise.all([listUploads(), listChunks()]);
      for (const upload of uploads) {
        if (readyToUpload(upload, chunks, !!this.recorder?.isRecording, this.serverNow())) {
          await this.uploadOne(upload, chunks);
        }
      }
    } finally {
      this.uploading = false;
      await this.refresh();
    }
  }

  private async uploadOne(upload: PendingUpload, chunks: ChunkMeta[]): Promise<void> {
    try {
      const needed = chunksForWindow(chunks, upload).filter((c) => !upload.uploadedKeys[c.id]);
      if (needed.length > 0) {
        const urls = await unwrap(
          stationApi.POST("/api/clips/{orderId}/upload-urls", {
            params: { path: { orderId: upload.orderId } },
            body: {
              chunks: needed.map((c) => ({
                chunkId: c.id,
                startedAt: new Date(c.startMs).toISOString(),
                endedAt: new Date(c.endMs).toISOString(),
                contentType: c.mimeType,
                sizeBytes: c.sizeBytes,
              })),
            },
          }),
        );
        for (const url of urls) {
          const blob = await chunkBlob(url.chunkId);
          if (!blob) continue; // evicted; send what we have
          const response = await fetch(url.uploadUrl, { method: "PUT", headers: url.headers, body: blob });
          if (!response.ok) throw new Error(`Storage refused the upload (HTTP ${response.status})`);
          upload.uploadedKeys[url.chunkId] = url.objectKey;
          await saveUpload(upload); // progress survives a crash or reload
        }
      }
      // Empty list = no footage for this window; the backend closes the request.
      await unwrap(
        stationApi.POST("/api/clips/{orderId}/chunks-uploaded", {
          params: { path: { orderId: upload.orderId } },
          body: { objectKeys: Object.values(upload.uploadedKeys) },
        }),
      );
      await removeUpload(upload.orderId);
      this.update({ lastUploaded: { displayNumber: upload.displayNumber, atMs: Date.now() } });
    } catch (error) {
      if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
        await removeUpload(upload.orderId); // closed (timed out) or not this station's: nothing to retry
        return;
      }
      upload.attempts += 1;
      upload.nextAttemptAtMs = this.serverNow() + backoffMs(upload.attempts);
      upload.lastError = errorMessage(error);
      await saveUpload(upload);
    }
  }

  // ── ring buffer ──────────────────────────────────────────────────────────

  /** Drops footage older than 45 minutes, except chunks a pending upload still needs. */
  async cleanup(): Promise<void> {
    const [chunks, uploads] = await Promise.all([listChunks(), listUploads()]);
    const pinned = pinnedChunkIds(chunks, uploads);
    let doomed = expiredChunkIds(chunks, this.serverNow(), pinned);
    // Running out of space beats losing new footage: drop the oldest unpinned chunks early.
    const estimate = await navigator.storage?.estimate?.().catch(() => undefined);
    if (estimate?.quota && estimate.usage && estimate.usage / estimate.quota > 0.9) {
      const oldest = chunks.filter((c) => !pinned.has(c.id) && !doomed.includes(c.id)).sort((a, b) => a.startMs - b.startMs).slice(0, 5);
      doomed = [...doomed, ...oldest.map((c) => c.id)];
    }
    await deleteChunks(doomed);
    await this.refresh();
  }

  private async refresh(): Promise<void> {
    try {
      const [chunks, uploads] = await Promise.all([listChunks(), listUploads()]);
      const oldest = chunks.reduce((min, c) => Math.min(min, c.startMs), Infinity);
      const newest = chunks.reduce((max, c) => Math.max(max, c.endMs), 0);
      this.update({
        chunkCount: chunks.length,
        bufferBytes: chunks.reduce((sum, c) => sum + c.sizeBytes, 0),
        bufferMinutes: chunks.length ? Math.round((newest - oldest) / 60_000) : 0,
        uploads: uploads.sort((a, b) => a.createdAtMs - b.createdAtMs),
        chunks: chunks.sort((a, b) => b.startMs - a.startMs),
      });
    } catch (error) {
      this.update({ error: `Storage unavailable: ${errorMessage(error)}` });
    }
  }

  private update(patch: Partial<StationSnapshot>): void {
    this.snapshot = { ...this.snapshot, ...patch };
    this.listeners.forEach((l) => l());
  }
}
