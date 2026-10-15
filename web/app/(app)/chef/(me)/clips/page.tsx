"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { ClipState } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { Badge, Button, EmptyState, ErrorState, PageLoading } from "@/components/ui";

const PAGE = 20;
const STATUS: Record<ClipState, { label: string; tone: "green" | "amber" | "red" | "stone" | "brand" }> = {
  PENDING: { label: "Waiting for footage", tone: "amber" },
  PROCESSING: { label: "Processing", tone: "brand" },
  READY: { label: "Ready", tone: "green" },
  FAILED: { label: "Failed", tone: "red" },
  NOT_AVAILABLE: { label: "No clip", tone: "stone" },
  EXPIRED: { label: "Expired", tone: "stone" },
};

/** "My clips": clips of orders I cooked, with how often customers' shared links were viewed. */
export default function MyClipsPage() {
  const [page, setPage] = useState(0);
  const clips = useQuery({
    queryKey: ["chef-clips", page],
    queryFn: () => unwrap(api.GET("/api/chef/clips", { params: { query: { page, size: PAGE } } })),
    placeholderData: keepPreviousData,
  });
  if (clips.isPending) return <PageLoading />;
  if (clips.isError) return <ErrorState message={errorMessage(clips.error)} onRetry={() => clips.refetch()} />;
  const c = clips.data;
  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <h1 className="text-2xl font-bold">My clips</h1>
      {c.items.length === 0 ? (
        <EmptyState title="No clips yet" message="Clips appear here when orders you cook at a station with a paired phone are ready." />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            {c.items.map((clip) => (
              <div key={`${clip.orderDisplayNumber}-${clip.createdAt}`} className="overflow-hidden rounded-2xl border border-stone-200 bg-white">
                {clip.thumbnailUrl ? (
                  <img src={clip.thumbnailUrl} alt="" className="aspect-video w-full object-cover" />
                ) : (
                  <div className="flex aspect-video items-center justify-center bg-stone-100 text-3xl" aria-hidden>🎥</div>
                )}
                <div className="space-y-1 p-3">
                  <div className="flex items-center gap-2">
                    <p className="mr-auto font-semibold">Order #{clip.orderDisplayNumber}</p>
                    <Badge tone={STATUS[clip.status].tone}>{STATUS[clip.status].label}</Badge>
                  </div>
                  <p className="text-sm text-stone-500">
                    {formatDateTime(clip.readyAt ?? clip.createdAt)}
                    {clip.durationSeconds != null && ` · ${Math.round(clip.durationSeconds)} s`} · 👀 {clip.viewCount} {clip.viewCount === 1 ? "view" : "views"}
                  </p>
                </div>
              </div>
            ))}
          </div>
          <div className="flex items-center justify-between">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Newer</Button>
            <span className="text-sm text-stone-500">Page {page + 1} of {Math.max(1, c.totalPages)}</span>
            <Button variant="secondary" disabled={page + 1 >= c.totalPages} onClick={() => setPage((p) => p + 1)}>Older</Button>
          </div>
        </>
      )}
    </div>
  );
}
