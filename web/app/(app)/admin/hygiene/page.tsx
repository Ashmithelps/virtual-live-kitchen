"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { formatDateTime } from "@/lib/format";
import { HygieneResultCard } from "@/components/hygiene/result-card";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading, inputBaseClass, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

type Dispute = components["schemas"]["AdminDisputeResponse"];
type Status = "OPEN" | "UPHELD" | "REJECTED";

/** Hygiene: the platform switch for public scores, and the dispute review queue. */
export default function AdminHygienePage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [status, setStatus] = useState<Status>("OPEN");
  const settings = useQuery({ queryKey: ["admin", "hygiene-settings"], queryFn: () => unwrap(api.GET("/api/admin/hygiene/settings")) });
  const disputes = useQuery({
    queryKey: ["admin", "hygiene-disputes", status],
    queryFn: () => unwrap(api.GET("/api/admin/hygiene/disputes", { params: { query: { status } } })),
  });
  const toggle = useMutation({
    mutationFn: (on: boolean) => unwrap(api.PUT("/api/admin/hygiene/settings", { body: { publicScoresEnabled: on } })),
    onSuccess: (s) => {
      queryClient.setQueryData(["admin", "hygiene-settings"], s);
      toast.success(s.publicScoresEnabled ? "Public hygiene scores are ON" : "Public hygiene scores are off");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <h1 className="text-2xl font-bold">Hygiene</h1>
        {settings.isPending ? (
          <PageLoading />
        ) : settings.isError ? (
          <ErrorState message={errorMessage(settings.error)} onRetry={() => settings.refetch()} />
        ) : (
          <Card className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <p className="mr-auto font-semibold">Public hygiene scores</p>
              <Badge tone={settings.data.publicScoresEnabled ? "green" : "stone"}>{settings.data.publicScoresEnabled ? "On" : "Off"}</Badge>
            </div>
            <p className="text-sm text-stone-600">
              When on, restaurants and chefs with at least {settings.data.minPublicClips} scored clips in the last {settings.data.windowDays} days show their score
              publicly. Turn this on only after the model&apos;s precision/recall has been validated (see ai-service/README).
            </p>
            {!settings.data.analysisEnabled && <p className="text-sm text-amber-700">AI analysis is disabled on this server (HYGIENE_ENABLED=false), so no new clips are being scored.</p>}
            {settings.data.publicScoresEnabled ? (
              <Button variant="secondary" busy={toggle.isPending} onClick={() => toggle.mutate(false)}>
                Turn off
              </Button>
            ) : (
              <ConfirmButton label="Turn on public scores" confirmLabel="Customers will see scores. Turn on?" onConfirm={() => toggle.mutate(true)} />
            )}
          </Card>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-bold">Disputes</h2>
        <div className="flex gap-1 border-b border-stone-200" role="tablist">
          {(["OPEN", "UPHELD", "REJECTED"] as const).map((s) => (
            <button key={s} role="tab" aria-selected={status === s} onClick={() => setStatus(s)}
              className={`border-b-2 px-3 py-2 text-sm font-medium ${status === s ? "border-brand-600 text-brand-700" : "border-transparent text-stone-600"}`}>
              {s === "OPEN" ? "To review" : s === "UPHELD" ? "Upheld" : "Rejected"}
            </button>
          ))}
        </div>
        {disputes.isPending ? (
          <PageLoading />
        ) : disputes.isError ? (
          <ErrorState message={errorMessage(disputes.error)} onRetry={() => disputes.refetch()} />
        ) : disputes.data.length === 0 ? (
          <EmptyState title="Nothing here" message={status === "OPEN" ? "No disputes waiting for review." : undefined} />
        ) : (
          disputes.data.map((d) => <DisputeItem key={d.dispute.id} dispute={d} onDone={() => queryClient.invalidateQueries({ queryKey: ["admin", "hygiene-disputes"] })} />)
        )}
      </section>
    </div>
  );
}

function DisputeItem({ dispute, onDone }: { dispute: Dispute; onDone: () => void }) {
  const toast = useToast();
  const [score, setScore] = useState("");
  const [note, setNote] = useState("");
  const resolve = useMutation({
    mutationFn: (body: { decision: "UPHOLD" | "REJECT"; overrideScore?: number; exclude?: boolean; note?: string }) =>
      unwrap(api.POST("/api/admin/hygiene/disputes/{disputeId}/resolve", { params: { path: { disputeId: dispute.dispute.id } }, body })),
    onSuccess: (r) => {
      toast.success(`Dispute ${r.dispute.status.toLowerCase()}`);
      onDone();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const open = dispute.dispute.status === "OPEN";
  const corrected = score.trim() === "" ? null : Number(score);
  const scoreValid = corrected !== null && Number.isFinite(corrected) && corrected >= 0 && corrected <= 100;

  return (
    <div className="space-y-2">
      <p className="text-sm">
        <span className="font-semibold">{dispute.restaurantName}</span> · opened {formatDateTime(dispute.dispute.createdAt)}
      </p>
      <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm">“{dispute.dispute.reason}”</p>
      <HygieneResultCard
        result={dispute.result}
        loadVideo={() => unwrap(api.GET("/api/admin/clips/{orderId}/playback", { params: { path: { orderId: dispute.result.orderId } } })).then((p) => p.videoUrl)}
        actions={
          open ? (
            <div className="space-y-2 border-t border-stone-100 pt-2">
              <input className={inputClass} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Note for the owner (optional)" aria-label="Note" maxLength={500} />
              <div className="flex flex-wrap items-center gap-2">
                <input className={`${inputBaseClass} w-28`} value={score} onChange={(e) => setScore(e.target.value)} inputMode="decimal" placeholder="Score 0–100" aria-label="Corrected score" />
                <Button disabled={!scoreValid} busy={resolve.isPending} onClick={() => resolve.mutate({ decision: "UPHOLD", overrideScore: corrected!, note: note || undefined })}>
                  Uphold with this score
                </Button>
                <Button variant="secondary" busy={resolve.isPending} onClick={() => resolve.mutate({ decision: "UPHOLD", exclude: true, note: note || undefined })}>
                  Uphold & exclude clip
                </Button>
                <Button variant="danger" busy={resolve.isPending} onClick={() => resolve.mutate({ decision: "REJECT", note: note || undefined })}>
                  Reject
                </Button>
              </div>
            </div>
          ) : null
        }
      />
    </div>
  );
}
