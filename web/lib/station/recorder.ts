"use client";

import { CHUNK_MS } from "./buffer";

export type RecordedChunk = { blob: Blob; mimeType: string; startMs: number; endMs: number };

/** First container/codec this browser can record (Android Chrome: WebM/VP8). */
export function pickMimeType(): string {
  const candidates = ["video/webm;codecs=vp8", "video/webm;codecs=vp9", "video/webm", "video/mp4"];
  return candidates.find((t) => typeof MediaRecorder !== "undefined" && MediaRecorder.isTypeSupported(t)) ?? "";
}

/** Rear camera, 720p, no audio (kitchen conversations are not recorded). */
export function openCamera(): Promise<MediaStream> {
  return navigator.mediaDevices.getUserMedia({
    audio: false,
    video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 24, max: 30 } },
  });
}

/**
 * Records the stream in back-to-back 60-second files. Each chunk comes from its own
 * MediaRecorder (stop, then start a new one) so every chunk is a complete, playable file;
 * timeslice-based chunks would only have headers in the first piece. The switch leaves a gap of a
 * few milliseconds between chunks.
 */
export class ChunkRecorder {
  private recorder: MediaRecorder | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private running = false;

  constructor(
    private readonly stream: MediaStream,
    private readonly serverNow: () => number,
    private readonly onChunk: (chunk: RecordedChunk) => void,
    private readonly onError: (error: unknown) => void,
    private readonly bitsPerSecond = 1_200_000,
  ) {}

  get isRecording(): boolean {
    return this.running;
  }

  start(): void {
    if (this.running) return;
    this.running = true;
    this.next();
  }

  /** Finishes the current chunk (it's still saved) and stops. */
  stop(): void {
    this.running = false;
    if (this.timer) clearTimeout(this.timer);
    if (this.recorder && this.recorder.state !== "inactive") this.recorder.stop();
  }

  private next(): void {
    if (!this.running) return;
    const mimeType = pickMimeType();
    let recorder: MediaRecorder;
    try {
      recorder = new MediaRecorder(this.stream, { mimeType: mimeType || undefined, videoBitsPerSecond: this.bitsPerSecond });
    } catch (error) {
      this.running = false;
      this.onError(error);
      return;
    }
    const parts: Blob[] = [];
    let startMs = this.serverNow();
    recorder.onstart = () => {
      startMs = this.serverNow();
    };
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) parts.push(e.data);
    };
    recorder.onstop = () => {
      const endMs = this.serverNow();
      if (parts.length > 0) {
        const type = recorder.mimeType || mimeType || "video/webm";
        this.onChunk({ blob: new Blob(parts, { type }), mimeType: type, startMs, endMs });
      }
      this.next(); // immediately start the following chunk
    };
    recorder.onerror = (e) => this.onError(e);
    recorder.start();
    this.recorder = recorder;
    this.timer = setTimeout(() => {
      if (recorder.state !== "inactive") recorder.stop();
    }, CHUNK_MS);
  }
}
