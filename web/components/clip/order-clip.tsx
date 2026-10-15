"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { errorMessage } from "@/lib/api/errors";
import type { ClipPlayback, Order } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { fetchClipPlayback, revokeClipShare, shareClip, type OrderAccess } from "@/lib/orders";
import { copyText, shareOrCopy } from "@/lib/share";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { TipPanel } from "@/components/tip/tip-panel";
import { useToast } from "@/components/toast";
import { Button, Card, ErrorState, Spinner, inputClass } from "@/components/ui";

type ClipStatus = NonNullable<Order["clipStatus"]>;

const PREPARING_STEPS: { status: ClipStatus[]; label: string }[] = [
  { status: ["PENDING"], label: "Getting the footage" },
  { status: ["UPLOADED"], label: "Queued for editing" },
  { status: ["PROCESSING"], label: "Editing your clip" },
];

/** The order page's kitchen-clip card: progress while it's made, then the player, download and sharing. */
/** {@code offerTip}: show the tip prompt once the clip has (mostly) played. Off when checkout already asked. */
export function OrderClip({ order, access, offerTip = true }: { order: Order; access: OrderAccess; offerTip?: boolean }) {
  const status = order.clipStatus;
  if (status === "READY") return <ReadyClip order={order} access={access} offerTip={offerTip} />;
  if (status === "PENDING" || status === "UPLOADED" || status === "PROCESSING") return <BeingPrepared status={status} />;
  if (status === "NOT_AVAILABLE" || status === "FAILED" || status === "EXPIRED") return <Unavailable status={status} />;

  const cooking = order.preparation
    ? `${order.preparation.chefDisplayName ?? "Your chef"} is cooking at the ${order.preparation.stationName ?? "kitchen"} station. Your clip starts once it's ready.`
    : "Once the kitchen starts cooking, you'll get a short video of your food being made.";
  return (
    <Card className="border-dashed bg-stone-50 text-center">
      <p className="text-2xl" aria-hidden>
        🎥
      </p>
      <p className="font-semibold">Your kitchen clip</p>
      <p className="mt-1 text-sm text-stone-500">{cooking}</p>
    </Card>
  );
}

function BeingPrepared({ status }: { status: ClipStatus }) {
  const current = PREPARING_STEPS.findIndex((s) => s.status.includes(status));
  return (
    <Card className="space-y-3 text-center" aria-live="polite">
      <p className="text-2xl" aria-hidden>
        🎬
      </p>
      <p className="font-semibold">Being prepared</p>
      <p className="text-sm text-stone-500">We&apos;re turning the kitchen footage into a short clip of your order. It usually takes a minute or two.</p>
      <div className="relative h-2 overflow-hidden rounded-full bg-stone-200" role="progressbar" aria-label="Preparing your clip" aria-valuetext={PREPARING_STEPS[current]?.label}>
        <div className="absolute inset-y-0 w-1/3 animate-clip-progress rounded-full bg-brand-600" />
      </div>
      <ol className="flex justify-between gap-2 text-xs">
        {PREPARING_STEPS.map((step, i) => (
          <li key={step.label} className={i < current ? "text-green-700" : i === current ? "font-semibold text-brand-700" : "text-stone-400"}>
            {i < current ? "✓ " : ""}
            {step.label}
          </li>
        ))}
      </ol>
    </Card>
  );
}

function Unavailable({ status }: { status: ClipStatus }) {
  const text: Partial<Record<ClipStatus, string>> = {
    NOT_AVAILABLE: "There's no kitchen clip for this order this time, sorry. We hope you enjoyed the food!",
    FAILED: "We couldn't make a clip of this order, sorry. The restaurant has been told and may still add it.",
    EXPIRED: "This order's kitchen clip has expired. Clips are kept for a limited time.",
  };
  return (
    <Card className="border-dashed bg-stone-50 text-center">
      <p className="text-2xl" aria-hidden>
        🍽️
      </p>
      <p className="font-semibold">No kitchen clip</p>
      <p className="mt-1 text-sm text-stone-500">{text[status]}</p>
    </Card>
  );
}

const EXPIRY_MARGIN_MS = 30_000;

function ReadyClip({ order, access, offerTip }: { order: Order; access: OrderAccess; offerTip: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ["clip-playback", order.id, access.kind];
  // Links last 10 minutes. Keep the player's src stable (a new URL would restart the video);
  // fetch again only when a link is needed after it expired.
  const playback = useQuery({ queryKey: key, queryFn: () => fetchClipPlayback(order.id, access), staleTime: Infinity, gcTime: 15 * 60_000 });
  const [src, setSrc] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  // "After it plays": most of the clip watched (or it ended).
  const [watched, setWatched] = useState(false);

  const fresh = async (): Promise<ClipPlayback> => {
    const current = playback.data;
    if (current && Date.parse(current.linksExpireAt) - Date.now() > EXPIRY_MARGIN_MS) return current;
    const { data } = await playback.refetch({ throwOnError: true });
    return data!;
  };

  const share = useMutation({
    mutationFn: async () => {
      const link = playback.data?.share ?? (await shareClip(order.id, access));
      queryClient.setQueryData<ClipPlayback>(key, (p) => (p ? { ...p, share: link } : p));
      return shareOrCopy({ title: `Watch my order being made at ${order.restaurantName}`, text: `My order #${order.displayNumber} at ${order.restaurantName}`, url: link.url });
    },
    onSuccess: (outcome) => {
      if (outcome === "copied") toast.success("Link copied. Paste it anywhere to share.");
      if (outcome === "failed") toast.info("Couldn't open sharing; copy the link below.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const revoke = useMutation({
    mutationFn: () => revokeClipShare(order.id, access),
    onSuccess: () => {
      queryClient.setQueryData<ClipPlayback>(key, (p) => (p ? { ...p, share: null } : p));
      toast.success("Link turned off. Anyone opening it now sees nothing.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const download = async () => {
    setDownloading(true);
    try {
      // Content-Disposition: attachment, so the browser saves it instead of leaving the page.
      window.location.href = (await fresh()).downloadUrl;
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setDownloading(false);
    }
  };

  if (playback.isPending) {
    return (
      <Card className="flex aspect-video items-center justify-center">
        <Spinner />
      </Card>
    );
  }
  if (playback.isError) return <ErrorState title="Couldn't load your clip" message={errorMessage(playback.error)} onRetry={() => playback.refetch()} />;
  const clip = playback.data;
  const link = clip.share;

  return (
    <Card className="space-y-0 overflow-hidden p-0!">
      <video
        key={src ?? clip.videoUrl}
        src={src ?? clip.videoUrl}
        poster={clip.thumbnailUrl}
        controls
        playsInline
        preload="metadata"
        className="aspect-video w-full bg-black"
        onEnded={() => setWatched(true)}
        onTimeUpdate={(e) => {
          const v = e.currentTarget;
          if (!watched && v.duration > 0 && v.currentTime / v.duration >= 0.8) setWatched(true);
        }}
        onError={async () => {
          // Most likely the 10-minute link ran out while the page sat open: get a new one.
          try {
            setSrc((await fresh()).videoUrl);
          } catch {
            // the error card on the next render covers it
          }
        }}
      />
      <div className="space-y-3 p-4">
        <div>
          <p className="font-semibold">Your kitchen clip 🎬</p>
          <p className="text-sm text-stone-500">
            {Math.round(clip.durationSeconds)} s{clip.chefName ? ` · cooked by ${clip.chefName}` : ""} · available until {formatDateTime(clip.clipExpiresAt)}
            {clip.viewCount > 0 && ` · ${clip.viewCount} ${clip.viewCount === 1 ? "view" : "views"} from your link`}
          </p>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <Button onClick={() => share.mutate()} busy={share.isPending}>
            Share
          </Button>
          <Button variant="secondary" onClick={download} busy={downloading}>
            Download
          </Button>
        </div>
        {link && (
          <div className="space-y-2 rounded-xl bg-stone-50 p-3">
            <p className="text-sm font-medium">Your public link</p>
            <div className="flex gap-2">
              <input className={inputClass} value={link.url} readOnly aria-label="Public link to your clip" onFocus={(e) => e.target.select()} />
              <Button
                variant="secondary"
                onClick={async () => ((await copyText(link.url)) ? toast.success("Link copied") : toast.error("Couldn't copy; select the link and copy it."))}
              >
                Copy
              </Button>
            </div>
            <p className="text-xs text-stone-500">Anyone with this link can watch the clip. It shows the restaurant and chef, never your name or number.</p>
            <ConfirmButton label="Turn off link" confirmLabel="Turn it off for everyone?" onConfirm={() => revoke.mutate()} />
          </div>
        )}
        {offerTip && watched && <TipPanel orderId={order.id} access={access} source="CLIP" />}
      </div>
    </Card>
  );
}
