"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { api } from "@/lib/api/client";
import { ApiError, errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { SUBSCRIPTION_STATUS, formatDate, type Invoice } from "@/lib/billing";
import { saveBlob } from "@/lib/download";
import { formatDateTime, formatPaise } from "@/lib/format";
import { stateName } from "@/lib/gst-states";
import { RESTAURANT_STATUS } from "@/lib/owner/status";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Badge, Button, Card, ErrorState, PageLoading } from "@/components/ui";
import { useToast } from "@/components/toast";

type SimulatedEvent = components["schemas"]["SimulateRequest"]["event"];
const SIMULATIONS: { event: SimulatedEvent; label: string }[] = [
  { event: "CHARGED", label: "Charge succeeded" },
  { event: "PENDING", label: "Charge failed" },
  { event: "HALTED", label: "Halted" },
  { event: "CANCELLED", label: "Cancelled" },
];

/** One restaurant for review: owner, FSSAI licence, GST details, subscription and invoices. Opening it is audited. */
export default function AdminRestaurantDetailPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ["admin", "restaurant", restaurantId];
  const detail = useQuery({
    queryKey: key,
    queryFn: () => unwrap(api.GET("/api/admin/restaurants/{restaurantId}", { params: { path: { restaurantId } } })),
    // Each fetch writes an audit entry (and mints a fresh licence link), so don't refetch on focus.
    refetchOnWindowFocus: false,
  });
  const settings = useQuery({ queryKey: ["admin", "billing", "settings"], queryFn: () => unwrap(api.GET("/api/admin/billing/settings")) });

  const act = useMutation({
    mutationFn: (task: { work: () => Promise<unknown>; done: string }) => task.work(),
    onSuccess: (_, task) => toast.success(task.done),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["admin"] }),
  });

  const [downloading, setDownloading] = useState<string | null>(null);
  const download = async (invoice: Invoice) => {
    setDownloading(invoice.id);
    try {
      const result = await api.GET("/api/admin/billing/restaurants/{restaurantId}/invoices/{invoiceId}.pdf", {
        params: { path: { restaurantId, invoiceId: invoice.id } },
        parseAs: "blob",
      });
      saveBlob(result, `${invoice.number.replaceAll("/", "-")}.pdf`);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setDownloading(null);
    }
  };

  if (detail.isPending) return <PageLoading />;
  if (detail.isError) {
    const missing = detail.error instanceof ApiError && detail.error.status === 404;
    return <ErrorState title={missing ? "Restaurant not found" : undefined} message={errorMessage(detail.error)} onRetry={missing ? undefined : () => detail.refetch()} />;
  }
  const { restaurant: r, owner, compliance: c, subscription: sub, invoices } = detail.data;
  const path = { params: { path: { restaurantId } } };

  return (
    <div className="space-y-4">
      <Link href="/admin/restaurants" className="text-sm text-stone-500 hover:text-stone-800">
        ← All restaurants
      </Link>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold">{r.name}</h1>
        <Badge tone={RESTAURANT_STATUS[r.status].tone}>{RESTAURANT_STATUS[r.status].label}</Badge>
        {r.suspendedReason && <Badge tone="red">{r.suspendedReason === "BILLING" ? "Unpaid subscription" : "By admin"}</Badge>}
        <div className="ml-auto flex flex-wrap gap-2">
          {r.status !== "ACTIVE" && (
            <Button disabled={act.isPending} onClick={() => act.mutate({ work: () => unwrap(api.POST("/api/admin/restaurants/{restaurantId}/approve", path)), done: `${r.name} is live` })}>
              {r.status === "SUSPENDED" ? "Reinstate" : "Approve"}
            </Button>
          )}
          {r.status !== "SUSPENDED" && (
            <ConfirmButton
              label="Suspend"
              confirmLabel="Hide from customers?"
              onConfirm={() => act.mutate({ work: () => unwrap(api.POST("/api/admin/restaurants/{restaurantId}/suspend", path)), done: `${r.name} suspended` })}
            />
          )}
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        <Card className="space-y-2">
          <h2 className="font-semibold">Restaurant</h2>
          <Facts>
            <Fact label="Address" value={`${r.address}, ${r.city}`} />
            <Fact label="Public page" value={<Link href={`/r/${r.slug}`} className="text-brand-700 underline" target="_blank">/r/{r.slug}</Link>} />
            <Fact label="Order types" value={r.orderTypes.join(", ").replaceAll("_", "-").toLowerCase()} />
            <Fact label="Created" value={formatDateTime(r.createdAt)} />
          </Facts>
        </Card>
        <Card className="space-y-2">
          <h2 className="font-semibold">Owner</h2>
          <Facts>
            <Fact label="Name" value={owner.name} />
            <Fact label="Phone" value={owner.phone} />
            <Fact label="Email" value={owner.email ?? "—"} />
            <Fact label="Account" value={owner.status === "ACTIVE" ? "Approved" : owner.status === "PENDING_APPROVAL" ? "Awaiting approval" : "Disabled"} />
          </Facts>
        </Card>
        <Card className="space-y-2">
          <h2 className="font-semibold">Licence & tax</h2>
          <Facts>
            <Fact label="FSSAI licence no." value={c.fssaiNumber ?? <Missing />} />
            <Fact
              label="Licence document"
              value={c.fssaiDocUrl ? <a href={c.fssaiDocUrl} target="_blank" rel="noreferrer" className="text-brand-700 underline">Open document</a> : <Missing />}
            />
            <Fact label="Legal name" value={c.legalName ?? <Missing />} />
            <Fact label="GSTIN" value={c.gstin ?? "Not registered"} />
            <Fact label="GST state" value={c.billingStateCode ? `${c.billingStateCode} · ${stateName(c.billingStateCode)}` : <Missing />} />
          </Facts>
          <p className="text-xs text-stone-500">The document link expires after a few minutes; reload the page for a new one.</p>
        </Card>
        <Card className="space-y-2">
          <h2 className="font-semibold">Subscription</h2>
          {!sub ? (
            <p className="text-sm text-stone-500">No subscription.</p>
          ) : (
            <>
              <Facts>
                <Fact label="Status" value={<Badge tone={SUBSCRIPTION_STATUS[sub.status].tone}>{SUBSCRIPTION_STATUS[sub.status].label}</Badge>} />
                <Fact label="Plan" value={`${sub.planName} · ${formatPaise(sub.pricePaise)}/month`} />
                <Fact label="Trial ends" value={formatDate(sub.trialEndsAt)} />
                <Fact label="Paid until" value={sub.currentPeriodEnd ? formatDate(sub.currentPeriodEnd) : "—"} />
                <Fact label="Grace until" value={sub.graceUntil ? formatDate(sub.graceUntil) : "—"} />
                <Fact label="Autopay" value={sub.paymentMethodReady ? "Set up" : "Not set up"} />
                <Fact label="Gateway id" value={<span className="font-mono text-xs">{sub.providerSubscriptionId ?? "—"}</span>} />
              </Facts>
              {settings.data?.simulationAvailable && sub.providerSubscriptionId && (
                <div className="space-y-1 rounded-lg border border-dashed border-amber-400 bg-amber-50 p-2">
                  <p className="text-xs font-semibold text-amber-900">Mock gateway: simulate a webhook</p>
                  <div className="flex flex-wrap gap-1">
                    {SIMULATIONS.map((s) => (
                      <Button
                        key={s.event}
                        variant="secondary"
                        disabled={act.isPending}
                        onClick={() =>
                          act.mutate({ work: () => unwrap(api.POST("/api/admin/billing/restaurants/{restaurantId}/simulate", { ...path, body: { event: s.event } })), done: `Simulated: ${s.label}` })
                        }
                      >
                        {s.label}
                      </Button>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </Card>
      </div>

      <Card className="space-y-2">
        <h2 className="font-semibold">Invoices</h2>
        {invoices.length === 0 ? (
          <p className="text-sm text-stone-500">No invoices yet.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {invoices.map((inv) => (
              <li key={inv.id} className="flex flex-wrap items-center gap-3 py-2 text-sm">
                <span className="font-mono text-xs">{inv.number}</span>
                <span className="text-stone-500">{formatDate(inv.issuedAt)}</span>
                <span className="mr-auto">{inv.planName}</span>
                <span className="font-semibold">{formatPaise(inv.totalPaise)}</span>
                <Button variant="ghost" busy={downloading === inv.id} onClick={() => download(inv)}>
                  PDF
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function Facts({ children }: { children: ReactNode }) {
  return <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">{children}</dl>;
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <>
      <dt className="text-stone-500">{label}</dt>
      <dd className="min-w-0 break-words">{value}</dd>
    </>
  );
}

function Missing() {
  return <span className="text-amber-700">Not provided</span>;
}
