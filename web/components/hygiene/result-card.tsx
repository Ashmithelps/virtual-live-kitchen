"use client";

import { useRef, useState } from "react";
import type { components } from "@/lib/api/schema";
import { formatDateTime } from "@/lib/format";
import { Badge, Button } from "@/components/ui";

export type HygieneResult = components["schemas"]["HygieneResultResponse"];

const ITEM_LABEL: Record<string, string> = { gloves: "Gloves", headCover: "Head cover", mask: "Mask" };

export function scoreTone(score: number | null | undefined): "green" | "amber" | "red" | "stone" {
  if (score == null) return "stone";
  return score >= 85 ? "green" : score >= 65 ? "amber" : "red";
}

export function formatClock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/**
 * One analysed clip: item percentages, violation timestamps that jump the player there, and dispute state.
 * {@code loadVideo} fetches a short-lived watch link (owner or admin playback endpoint).
 */
export function HygieneResultCard({
  result,
  loadVideo,
  actions,
}: {
  result: HygieneResult;
  loadVideo: () => Promise<string>;
  actions?: React.ReactNode;
}) {
  const video = useRef<HTMLVideoElement>(null);
  const [src, setSrc] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Seek target for when the freshly loaded video is ready.
  const pendingSeek = useRef<number | null>(null);

  const seekNow = (v: HTMLVideoElement, seconds: number) => {
    v.currentTime = seconds;
    void v.play().catch(() => undefined);
  };

  const jump = async (seconds: number) => {
    setError(null);
    if (src && video.current) return seekNow(video.current, seconds);
    pendingSeek.current = seconds;
    setLoading(true);
    try {
      setSrc(await loadVideo());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't load the clip");
    } finally {
      setLoading(false);
    }
  };

  const items = [
    { key: "gloves", pct: result.glovesPct },
    { key: "headCover", pct: result.headCoverPct },
    { key: "mask", pct: result.maskPct },
  ];
  const effective = result.effectiveScore;

  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-stone-200 bg-white p-3 sm:flex-row">
      <div className="sm:w-64 sm:shrink-0">
        {src ? (
          <video
            ref={video}
            src={src}
            controls
            playsInline
            muted
            className="aspect-video w-full rounded-lg bg-black"
            onLoadedMetadata={(e) => {
              if (pendingSeek.current != null) seekNow(e.currentTarget, pendingSeek.current);
              pendingSeek.current = null;
            }}
          />
        ) : result.thumbnailUrl ? (
          <button onClick={() => jump(0)} className="group relative block w-full" aria-label={`Play clip of order ${result.orderDisplayNumber}`}>
            <img src={result.thumbnailUrl} alt="" className="aspect-video w-full rounded-lg object-cover" />
            <span className="absolute inset-0 flex items-center justify-center text-4xl text-white drop-shadow">{loading ? "…" : "▶"}</span>
          </button>
        ) : (
          <div className="flex aspect-video items-center justify-center rounded-lg bg-stone-100 text-sm text-stone-500">Clip no longer stored</div>
        )}
        {error && <p className="mt-1 text-xs text-red-700">{error}</p>}
      </div>
      <div className="flex-1 space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-semibold">Order #{result.orderDisplayNumber}</p>
          <Badge tone={scoreTone(effective)}>{effective != null ? `${effective.toFixed(1)} / 100` : result.excluded ? "Excluded" : "Not scored"}</Badge>
          {result.overrideScore != null && <span className="text-xs text-stone-500">(model said {result.score?.toFixed(1)})</span>}
          {result.dispute && (
            <Badge tone={result.dispute.status === "OPEN" ? "amber" : result.dispute.status === "UPHELD" ? "green" : "stone"}>
              Dispute {result.dispute.status.toLowerCase()}
            </Badge>
          )}
        </div>
        <p className="text-xs text-stone-500">
          {result.chefName ?? "Unknown chef"} · {result.readyAt ? formatDateTime(result.readyAt) : ""} · model {result.modelVersion}
        </p>
        <ul className="space-y-1 text-sm">
          {items.map(({ key, pct }) => (
            <li key={key} className="flex flex-wrap items-center gap-2">
              <span className="w-24 text-stone-600">{ITEM_LABEL[key]}</span>
              <span className="w-36 font-medium">{pct != null ? `${pct.toFixed(1)}% compliant` : "not evaluated"}</span>
              {(result.violations[key] ?? []).map((t) => (
                <Button key={t} variant="ghost" className="min-h-8! px-2! py-0! text-xs" onClick={() => jump(t)} aria-label={`Jump to ${ITEM_LABEL[key]} violation at ${formatClock(t)}`}>
                  ⏵ {formatClock(t)}
                </Button>
              ))}
            </li>
          ))}
        </ul>
        {result.overrideNote && <p className="text-xs text-stone-600">Admin note: {result.overrideNote}</p>}
        {result.dispute?.adminNote && !result.overrideNote && <p className="text-xs text-stone-600">Admin note: {result.dispute.adminNote}</p>}
        {actions}
      </div>
    </div>
  );
}
