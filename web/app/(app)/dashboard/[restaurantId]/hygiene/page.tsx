"use client";

import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { HygieneResultCard, scoreTone, type HygieneResult } from "@/components/hygiene/result-card";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

type TrendPoint = components["schemas"]["TrendPoint"];

export default function HygienePage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const key = ["owner", "hygiene", restaurantId];
  const overview = useQuery({
    queryKey: key,
    queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/hygiene", { params: { path: { restaurantId } } })),
  });

  if (overview.isPending) return <PageLoading />;
  if (overview.isError) return <ErrorState message={errorMessage(overview.error)} onRetry={() => overview.refetch()} />;
  const o = overview.data;
  const loadVideo = (result: HygieneResult) => () =>
    unwrap(api.GET("/api/owner/restaurants/{restaurantId}/clips/{orderId}/playback", { params: { path: { restaurantId, orderId: result.orderId } } })).then((p) => p.videoUrl);

  return (
    <div className="space-y-4">
      <Card className="flex flex-wrap items-center gap-4">
        <div>
          <p className="text-sm text-stone-500">Hygiene score · last {o.windowDays} days</p>
          <p className="text-4xl font-black">{o.score != null ? o.score.toFixed(1) : "—"}</p>
          <p className="text-sm text-stone-500">
            from {o.clipCount} scored {o.clipCount === 1 ? "clip" : "clips"}
          </p>
        </div>
        <div className="max-w-md flex-1 text-sm text-stone-600">
          {o.shownPublicly ? (
            <Badge tone="green">Shown to customers</Badge>
          ) : !o.publicScoresEnabled ? (
            <p>Only you see this. Public scores are off while we validate the AI&apos;s accuracy.</p>
          ) : (
            <p>
              Customers will see it once {o.minPublicClips} clips are scored ({Math.max(0, o.minPublicClips - o.clipCount)} to go).
            </p>
          )}
          <p className="mt-1 text-xs text-stone-500">
            The AI checks gloves, head cover and masks once per second in each clip. A score is the share of moments where the cook was compliant,
            averaged over the items it could see. Think a result is wrong? Dispute it below and an admin will review the clip.
          </p>
        </div>
      </Card>

      <Card className="space-y-2">
        <h2 className="font-semibold">Weekly trend</h2>
        <Trend points={o.trend} />
      </Card>

      <Card className="space-y-2">
        <h2 className="font-semibold">Chefs</h2>
        {o.chefs.length === 0 ? (
          <p className="text-sm text-stone-500">No scored clips yet.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-left text-stone-500">
              <tr>
                <th className="py-1 font-medium">Chef</th>
                <th className="py-1 font-medium">Score</th>
                <th className="py-1 font-medium">Clips</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {o.chefs.map((c) => (
                <tr key={c.chefId}>
                  <td className="py-1.5">{c.displayName}</td>
                  <td className="py-1.5">
                    <Badge tone={scoreTone(c.score)}>{c.score != null ? c.score.toFixed(1) : "—"}</Badge>
                  </td>
                  <td className="py-1.5">
                    {c.clipCount}
                    {c.shownPublicly && <span className="ml-2 text-xs text-green-700">public</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Lowest-scoring clips</h2>
        {o.worst.length === 0 ? (
          <EmptyState title="Nothing to review" message="Clips appear here once the AI has analysed them." />
        ) : (
          o.worst.map((r) => (
            <HygieneResultCard
              key={r.id}
              result={r}
              loadVideo={loadVideo(r)}
              actions={!r.dispute || r.dispute.status !== "OPEN" ? <DisputeForm restaurantId={restaurantId} result={r} onDone={() => queryClient.invalidateQueries({ queryKey: key })} /> : null}
            />
          ))
        )}
      </section>
    </div>
  );
}

/** Simple weekly bars (no chart library): height = score, label = week start. */
function Trend({ points }: { points: TrendPoint[] }) {
  if (points.length === 0) return <p className="text-sm text-stone-500">No data yet.</p>;
  return (
    <div className="flex h-40 items-end gap-2 overflow-x-auto pb-6" role="img" aria-label="Weekly hygiene score">
      {points.map((p) => (
        <div key={p.weekStart} className="flex w-10 shrink-0 flex-col items-center justify-end gap-1" title={`${p.weekStart}: ${p.score ?? "—"} (${p.clips} clips)`}>
          <span className="text-xs font-medium">{p.score != null ? Math.round(p.score) : "—"}</span>
          <div
            className={`w-full rounded-t ${scoreTone(p.score) === "green" ? "bg-green-600" : scoreTone(p.score) === "amber" ? "bg-amber-500" : scoreTone(p.score) === "red" ? "bg-red-600" : "bg-stone-300"}`}
            style={{ height: `${Math.max(4, ((p.score ?? 0) / 100) * 96)}px` }}
          />
          <span className="text-[10px] text-stone-500">{p.weekStart.slice(5)}</span>
        </div>
      ))}
    </div>
  );
}

function DisputeForm({ restaurantId, result, onDone }: { restaurantId: string; result: HygieneResult; onDone: () => void }) {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const submit = useMutation({
    mutationFn: () =>
      unwrap(api.POST("/api/owner/restaurants/{restaurantId}/hygiene/results/{resultId}/disputes", { params: { path: { restaurantId, resultId: result.id } }, body: { reason } })),
    onSuccess: () => {
      toast.success("Sent for review. An admin will look at the clip.");
      setOpen(false);
      onDone();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  if (result.dispute?.status === "OPEN") return null;
  if (!open) {
    return (
      <Button variant="secondary" onClick={() => setOpen(true)}>
        Dispute this result
      </Button>
    );
  }
  return (
    <div className="space-y-2">
      <textarea className={inputClass} rows={2} maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="What did the AI get wrong? (at least 10 characters)" aria-label="Reason for the dispute" />
      <div className="flex gap-2">
        <Button busy={submit.isPending} disabled={reason.trim().length < 10} onClick={() => submit.mutate()}>
          Send for review
        </Button>
        <Button variant="ghost" onClick={() => setOpen(false)}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
