"use client";

import { useParams } from "next/navigation";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { ClipState, OwnerClip } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { ownerKeys } from "@/lib/owner/queries";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading, inputBaseClass } from "@/components/ui";
import { useToast } from "@/components/toast";

const PAGE_SIZE = 20;
const STATUS: Record<ClipState, { label: string; tone: "green" | "amber" | "red" | "stone" | "brand" }> = {
  PENDING: { label: "Waiting for footage", tone: "amber" },
  PROCESSING: { label: "Processing", tone: "brand" },
  READY: { label: "Ready", tone: "green" },
  FAILED: { label: "Failed", tone: "red" },
  NOT_AVAILABLE: { label: "Not available", tone: "stone" },
  EXPIRED: { label: "Expired", tone: "stone" },
};

/** Not-available reasons the backend records; anything else (e.g. a render error) is shown as-is. */
const REASON: Record<string, string> = {
  NO_CONSENT: "The chef hadn't given recording consent",
  NO_DEVICE: "No phone was paired at the station",
  NO_FOOTAGE: "The phone had no footage for this order",
  UPLOAD_TIMEOUT: "The phone didn't upload the footage in time",
};

const describeReason = (reason: string) => REASON[reason] ?? (reason.startsWith("NO_FOOTAGE") ? REASON.NO_FOOTAGE : reason);

export default function ClipsPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [status, setStatus] = useState<ClipState | "">("");
  const [page, setPage] = useState(0);
  const [playing, setPlaying] = useState<string | null>(null);

  const clips = useQuery({
    queryKey: ownerKeys.clips(restaurantId, status, page),
    queryFn: () =>
      unwrap(api.GET("/api/owner/restaurants/{restaurantId}/clips", { params: { path: { restaurantId }, query: { status: status || undefined, page, size: PAGE_SIZE } } })),
    placeholderData: keepPreviousData,
    // Clips move PENDING → PROCESSING → READY in the background.
    refetchInterval: 15_000,
  });

  const retry = useMutation({
    mutationFn: (clip: OwnerClip) => unwrap(api.POST("/api/owner/restaurants/{restaurantId}/clips/{orderId}/retry", { params: { path: { restaurantId, orderId: clip.orderId } } })),
    onSuccess: (clip) => toast.success(`Clip for #${clip.displayNumber} queued again`),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["owner", "clips", restaurantId] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <select
          className={`${inputBaseClass} w-56`}
          aria-label="Filter by status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ClipState | "");
            setPage(0);
          }}
        >
          <option value="">All clips</option>
          {(Object.keys(STATUS) as ClipState[]).map((s) => (
            <option key={s} value={s}>
              {STATUS[s].label}
            </option>
          ))}
        </select>
        <p className="text-sm text-stone-600">Kitchen clips are made from the station phone&apos;s recording when an order is marked ready.</p>
      </div>

      {clips.isPending ? (
        <PageLoading />
      ) : clips.isError ? (
        <ErrorState message={errorMessage(clips.error)} onRetry={() => clips.refetch()} />
      ) : clips.data.items.length === 0 ? (
        <EmptyState title="No clips" message={status ? "Nothing with this status." : "Clips appear once orders are cooked at a station with a paired phone."} />
      ) : (
        <>
          <div className="space-y-3">
            {clips.data.items.map((clip) => (
              <Card key={clip.orderId} className="flex flex-col gap-3 sm:flex-row">
                <div className="sm:w-64 sm:shrink-0">
                  {playing === clip.orderId && clip.videoUrl ? (
                    <video src={clip.videoUrl} poster={clip.thumbnailUrl ?? undefined} controls autoPlay playsInline className="aspect-video w-full rounded-lg bg-black" />
                  ) : clip.thumbnailUrl ? (
                    <button onClick={() => setPlaying(clip.orderId)} className="group relative block w-full" aria-label={`Play clip for order ${clip.displayNumber}`}>
                      { }
                      <img src={clip.thumbnailUrl} alt="" className="aspect-video w-full rounded-lg object-cover" />
                      <span className="absolute inset-0 flex items-center justify-center text-4xl text-white drop-shadow group-hover:scale-110">▶</span>
                    </button>
                  ) : (
                    <div className="flex aspect-video w-full items-center justify-center rounded-lg bg-stone-100 text-3xl" aria-hidden>
                      🎥
                    </div>
                  )}
                </div>
                <div className="flex-1 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-lg font-semibold">Order #{clip.displayNumber}</p>
                    <Badge tone={STATUS[clip.status].tone}>{STATUS[clip.status].label}</Badge>
                    {clip.durationSeconds != null && <span className="text-sm text-stone-500">{Math.round(clip.durationSeconds)} s</span>}
                  </div>
                  <p className="text-sm text-stone-600">
                    {clip.businessDate} · {clip.chefName ?? "Unknown chef"} · {clip.stationName ?? "Unknown station"}
                  </p>
                  <p className="text-xs text-stone-500">
                    Created {formatDateTime(clip.createdAt)}
                    {clip.readyAt && ` · ready ${formatDateTime(clip.readyAt)}`}
                    {clip.expiresAt && ` · deleted after ${formatDateTime(clip.expiresAt)}`}
                  </p>
                  {clip.failureReason && (
                    <p className="break-words rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">
                      <span className="font-medium">Reason:</span> {describeReason(clip.failureReason)}
                    </p>
                  )}
                  {clip.status === "FAILED" &&
                    (clip.retryable ? (
                      <Button onClick={() => retry.mutate(clip)} busy={retry.isPending && retry.variables?.orderId === clip.orderId}>
                        Retry
                      </Button>
                    ) : (
                      <p className="text-sm text-stone-500">Can&apos;t retry: the raw footage has been deleted.</p>
                    ))}
                </div>
              </Card>
            ))}
          </div>
          <div className="flex items-center justify-between">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Newer
            </Button>
            <span className="text-sm text-stone-500">
              Page {page + 1} of {Math.max(1, clips.data.totalPages)}
            </span>
            <Button variant="secondary" disabled={page + 1 >= clips.data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Older
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
