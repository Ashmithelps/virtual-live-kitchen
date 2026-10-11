"use client";

/**
 * New-order chime with the Web Audio API (no audio file to load or cache). Browsers only allow
 * sound after a user gesture, so `unlock()` must run inside a tap handler first.
 */
let context: AudioContext | null = null;

export function unlockSound(): boolean {
  try {
    context ??= new AudioContext();
    if (context.state === "suspended") void context.resume();
    return true;
  } catch {
    return false;
  }
}

export function soundReady(): boolean {
  return context?.state === "running";
}

export function playChime(): void {
  if (!context || context.state !== "running") return;
  const start = context.currentTime;
  // Two rising notes, loud enough for a noisy kitchen.
  [880, 1320].forEach((frequency, i) => {
    const osc = context!.createOscillator();
    const gain = context!.createGain();
    osc.type = "triangle";
    osc.frequency.value = frequency;
    const t = start + i * 0.18;
    gain.gain.setValueAtTime(0.0001, t);
    gain.gain.exponentialRampToValueAtTime(0.6, t + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);
    osc.connect(gain).connect(context!.destination);
    osc.start(t);
    osc.stop(t + 0.4);
  });
}
