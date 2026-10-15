"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { formatDateTime } from "@/lib/format";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Badge, Button, Card, ErrorState, PageLoading } from "@/components/ui";
import { useToast } from "@/components/toast";

/** A chef's own settings: profile and recording consent (accept, or withdraw at any time). */
export default function ChefSettingsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const me = useQuery({ queryKey: ["kds-chef-me"], queryFn: () => unwrap(api.GET("/api/chef/me")) });
  const consent = useQuery({ queryKey: ["kds-consent"], queryFn: () => unwrap(api.GET("/api/chef/consent")) });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["kds-chef-me"] }).then(() => queryClient.invalidateQueries({ queryKey: ["kds-consent"] }));

  const withdraw = useMutation({
    mutationFn: () => unwrap(api.POST("/api/chef/consent/withdraw")),
    onSuccess: () => toast.success("Consent withdrawn. You've been taken off your stations."),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: refresh,
  });
  const accept = useMutation({
    mutationFn: () => unwrap(api.POST("/api/chef/consent", { body: { consentVersion: consent.data!.version, consentTextHash: consent.data!.textHash } })),
    onSuccess: () => toast.success("Thanks. Ask your manager to put you on a station."),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: refresh,
  });

  if (me.isPending || consent.isPending) return <PageLoading />;
  if (me.isError) return <ErrorState message={errorMessage(me.error)} onRetry={() => me.refetch()} />;
  if (consent.isError) return <ErrorState message={errorMessage(consent.error)} onRetry={() => consent.refetch()} />;

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold">Settings</h1>
      <Card className="space-y-1">
        <p className="text-lg font-semibold">{me.data.displayName}</p>
        <p className="text-sm text-stone-600">{me.data.restaurantName}</p>
        <p className="text-sm text-stone-600">Stations: {me.data.stations.map((s) => s.name).join(", ") || "none yet"}</p>
        <Link href="/kitchen" className="inline-block pt-2 text-sm font-medium text-brand-700 underline">
          Open the kitchen display
        </Link>
      </Card>

      <Card className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="mr-auto font-semibold">Recording consent</h2>
          {consent.data.accepted ? <Badge tone="green">Accepted</Badge> : <Badge tone="amber">Not accepted</Badge>}
        </div>
        {consent.data.accepted ? (
          <>
            <p className="text-sm text-stone-600">
              You accepted version {consent.data.version}
              {consent.data.acceptedAt ? ` on ${formatDateTime(consent.data.acceptedAt)}` : ""}. Station cameras record orders you cook, and customers receive a clip of their order.
            </p>
            <p className="text-sm text-stone-600">You can withdraw at any time. You&apos;ll be taken off your stations, and no new clips will be made of you.</p>
            <ConfirmButton label="Withdraw consent" confirmLabel="Withdraw and leave my stations?" onConfirm={() => withdraw.mutate()} />
          </>
        ) : (
          <>
            <pre className="max-h-80 overflow-auto whitespace-pre-wrap rounded-xl bg-stone-50 p-4 font-sans text-sm leading-relaxed">{consent.data.text}</pre>
            <Button onClick={() => accept.mutate()} busy={accept.isPending}>
              I agree (version {consent.data.version})
            </Button>
          </>
        )}
      </Card>
    </div>
  );
}
