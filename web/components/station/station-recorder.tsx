"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { PairedStation } from "@/lib/station/device";
import { forgetPairing } from "@/lib/station/device";
import { openCamera } from "@/lib/station/recorder";
import { StationRuntime } from "@/lib/station/runtime";
import { useStomp } from "@/lib/realtime/use-stomp";
import { readJson, writeJson } from "@/lib/storage";
import { useBattery } from "@/lib/use-battery";
import { useInstallPrompt } from "@/lib/use-install-prompt";
import { useWakeLock } from "@/lib/use-wake-lock";

const AUTOSTART_KEY = "vk.station.autostart";

export function StationRecorder({ station }: { station: PairedStation }) {
  const [runtime] = useState(() => new StationRuntime());
  const snapshot = useSyncExternalStore(runtime.subscribe, runtime.getSnapshot, runtime.getSnapshot);
  const battery = useBattery();
  const preview = useRef<HTMLVideoElement>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [confirmUnpair, setConfirmUnpair] = useState(false);
  const [debugOpen, setDebugOpen] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const install = useInstallPrompt();
  const now = useNow(1_000);
  useWakeLock(true);

  useEffect(() => {
    runtime.startLoops();
    return () => runtime.dispose();
  }, [runtime]);

  const socket = useStomp({
    destination: `/topic/station/${station.stationId}`,
    connectHeaders: async () => ({ "X-Device-Token": station.deviceToken }),
    onMessage: (body) => {
      const message = body as { type?: string; window?: Parameters<StationRuntime["addWindow"]>[0] };
      if (message.type === "CLIP_WINDOW" && message.window) void runtime.addWindow(message.window);
    },
    // On every (re)connect: re-sync the clock and fetch windows pushed while offline.
    onConnected: () => {
      void runtime.syncClock();
      void runtime.catchUp();
    },
  });

  // Camera → recorder. State is only set in promise callbacks (after the camera answers).
  const attach = (stream: MediaStream) => {
    if (preview.current) preview.current.srcObject = stream;
    runtime.startRecording(stream);
    writeJson(AUTOSTART_KEY, true);
    setCameraError(null);
  };
  const cameraFailed = (error: unknown) =>
    setCameraError(error instanceof Error ? `${error.name}: ${error.message}` : "Camera unavailable");

  const start = () => {
    document.documentElement.requestFullscreen?.().catch(() => undefined); // needs the tap's user gesture
    openCamera().then(attach, cameraFailed);
  };

  const stop = () => {
    runtime.stopRecording();
    writeJson(AUTOSTART_KEY, false);
  };

  // Resume after a reload or crash if it was recording before (camera permission is remembered).
  useEffect(() => {
    if (readJson<boolean>(AUTOSTART_KEY)) openCamera().then(attach, cameraFailed);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (socket === "denied") {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-8 text-center">
        <p className="text-2xl font-bold">This phone is no longer paired</p>
        <p className="text-lg text-stone-400">The station was paired with another phone. Pair this one again from the kitchen display.</p>
        <button onClick={() => { runtime.dispose(); forgetPairing(); }} className="min-h-14 rounded-2xl bg-stone-800 px-8 text-lg font-semibold">
          Forget pairing
        </button>
      </div>
    );
  }

  const elapsed = snapshot.recordingSinceMs ? formatDuration(now - snapshot.recordingSinceMs) : null;
  const notCharging = battery && !battery.charging;

  return (
    <div className="flex min-h-dvh flex-col gap-3 p-4">
      <header className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm text-stone-400">{station.restaurantName}</p>
          <h1 className="text-2xl font-bold">{station.stationName}</h1>
        </div>
        <span className={`rounded-full px-3 py-1 text-sm font-bold ${socket === "connected" ? "bg-green-700" : "bg-red-700"}`}>
          {socket === "connected" ? "Online" : "Offline"}
        </span>
      </header>

      {notCharging && (
        <p role="alert" className="rounded-xl bg-amber-500 px-4 py-3 text-lg font-bold text-black">
          Not charging: plug this phone in ({Math.round(battery.level * 100)}%)
        </p>
      )}
      {(snapshot.error || cameraError) && (
        <p role="alert" className="rounded-xl bg-red-800 px-4 py-3 text-white">{cameraError ?? snapshot.error}</p>
      )}

      <div className="relative overflow-hidden rounded-2xl bg-stone-900">
        <video ref={preview} autoPlay muted playsInline className={`aspect-video w-full object-cover ${snapshot.recording ? "opacity-60" : "opacity-20"}`} />
        <div className="absolute left-3 top-3 flex items-center gap-2 rounded-full bg-black/70 px-3 py-1">
          <span className={`size-3 rounded-full ${snapshot.recording ? "animate-pulse bg-red-500" : "bg-stone-500"}`} />
          <span className="font-bold tabular-nums">{snapshot.recording ? `REC ${elapsed}` : "Not recording"}</span>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-2 text-base">
        <Stat label="Battery" value={battery ? `${Math.round(battery.level * 100)}%${battery.charging ? " · charging" : ""}` : "n/a"} warn={!!notCharging} />
        <Stat label="Buffer" value={`${snapshot.bufferMinutes} min · ${formatMb(snapshot.bufferBytes)}`} />
        <Stat
          label="Clock"
          value={snapshot.clock ? `${snapshot.clock.offsetMs >= 0 ? "+" : ""}${snapshot.clock.offsetMs} ms (±${Math.round(snapshot.clock.rttMs / 2)})` : "not synced"}
          warn={!snapshot.clock}
        />
        <Stat label="Last upload" value={snapshot.lastUploaded ? `#${snapshot.lastUploaded.displayNumber} · ${formatAgo(now - snapshot.lastUploaded.atMs)}` : "none yet"} />
      </dl>

      <section className="rounded-2xl bg-stone-900 p-3">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h2 className="font-semibold">Uploads waiting ({snapshot.uploads.length})</h2>
          {snapshot.uploads.length > 0 && (
            <button
              disabled={retrying}
              onClick={() => {
                setRetrying(true);
                runtime.retryUploadsNow().finally(() => setRetrying(false));
              }}
              className="min-h-10 rounded-xl bg-stone-800 px-3 text-sm font-semibold disabled:opacity-50"
            >
              {retrying ? "Sending…" : "Retry now"}
            </button>
          )}
        </div>
        {snapshot.uploads.length === 0 ? (
          <p className="text-stone-500">Nothing to send.</p>
        ) : (
          <ul className="space-y-1 text-sm">
            {snapshot.uploads.map((u) => (
              <li key={u.orderId} className="flex justify-between gap-2">
                <span>Order #{u.displayNumber}</span>
                <span className="text-stone-400">
                  {u.attempts === 0 ? "waiting for footage" : `retry ${u.attempts}${u.lastError ? `: ${u.lastError}` : ""}`}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <button onClick={() => setDebugOpen((o) => !o)} className="self-start text-sm text-stone-400 underline" aria-expanded={debugOpen}>
        {debugOpen ? "Hide debug info" : "Show debug info"}
      </button>
      {debugOpen && <DebugPanel snapshot={snapshot} now={now} />}
      {install && (
        <button onClick={install} className="min-h-12 rounded-2xl border border-stone-700 text-lg">
          Install as app
        </button>
      )}

      <div className="mt-auto grid grid-cols-2 gap-3">
        {snapshot.recording ? (
          <button onClick={stop} className="min-h-16 rounded-2xl bg-stone-800 text-xl font-bold">Stop</button>
        ) : (
          <button onClick={start} className="min-h-16 rounded-2xl bg-red-600 text-xl font-bold text-white">● Start recording</button>
        )}
        {confirmUnpair ? (
          <button onClick={() => { runtime.dispose(); forgetPairing(); }} className="min-h-16 rounded-2xl bg-red-900 text-lg font-bold">
            Tap again to unpair
          </button>
        ) : (
          <button onClick={() => setConfirmUnpair(true)} className="min-h-16 rounded-2xl border border-stone-700 text-lg">Unpair</button>
        )}
      </div>
    </div>
  );
}

/** Toggleable diagnostics: what's in IndexedDB, the raw upload queue, and clock/heartbeat state. */
function DebugPanel({ snapshot, now }: { snapshot: ReturnType<StationRuntime["getSnapshot"]>; now: number }) {
  const time = (ms: number) => new Date(ms).toLocaleTimeString("en-IN", { hour12: false });
  return (
    <section className="space-y-3 rounded-2xl border border-dashed border-stone-700 p-3 font-mono text-xs" aria-label="Debug info">
      <div>
        <p className="font-bold text-stone-300">Clock / heartbeat</p>
        <p>
          offset {snapshot.clock ? `${snapshot.clock.offsetMs} ms · rtt ${snapshot.clock.rttMs} ms · synced ${formatAgo(now - snapshot.clock.syncedAtMs)}` : "not synced"}
        </p>
        <p>server now ≈ {time(now + (snapshot.clock?.offsetMs ?? 0))} · device now {time(now)}</p>
        <p>last heartbeat {snapshot.lastHeartbeatMs ? formatAgo(now - snapshot.lastHeartbeatMs) : "never"}</p>
      </div>
      <div>
        <p className="font-bold text-stone-300">
          IndexedDB chunks ({snapshot.chunks.length}, {formatMb(snapshot.bufferBytes)})
        </p>
        <ul className="max-h-48 overflow-y-auto">
          {snapshot.chunks.map((c) => (
            <li key={c.id}>
              {time(c.startMs)}–{time(c.endMs)} · {(c.sizeBytes / 1024).toFixed(0)} KB · {c.mimeType.split(";")[0]} · {c.id.slice(0, 8)}
            </li>
          ))}
        </ul>
      </div>
      <div>
        <p className="font-bold text-stone-300">Pending uploads ({snapshot.uploads.length})</p>
        <ul>
          {snapshot.uploads.map((u) => (
            <li key={u.orderId}>
              #{u.displayNumber} window {time(u.windowStartMs)}–{time(u.windowEndMs)} · sent {Object.keys(u.uploadedKeys).length} · attempts {u.attempts}
              {u.nextAttemptAtMs > now ? ` · next in ${Math.ceil((u.nextAttemptAtMs - now) / 1000)} s` : ""}
              {u.lastError ? ` · ${u.lastError}` : ""}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function Stat({ label, value, warn = false }: { label: string; value: string; warn?: boolean }) {
  return (
    <div className={`rounded-xl px-3 py-2 ${warn ? "bg-amber-950" : "bg-stone-900"}`}>
      <dt className="text-xs uppercase tracking-wide text-stone-400">{label}</dt>
      <dd className="font-semibold tabular-nums">{value}</dd>
    </div>
  );
}

function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}

function formatDuration(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
}

function formatAgo(ms: number): string {
  const min = Math.floor(ms / 60_000);
  return min < 1 ? "just now" : `${min} min ago`;
}

function formatMb(bytes: number): string {
  return `${(bytes / 1_048_576).toFixed(0)} MB`;
}
