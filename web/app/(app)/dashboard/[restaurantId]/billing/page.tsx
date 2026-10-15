"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { SUBSCRIPTION_STATUS, daysUntil, formatDate, planFeatureLines, type Invoice, type Plan } from "@/lib/billing";
import { saveBlob } from "@/lib/download";
import { ownerKeys } from "@/lib/owner/queries";
import { formatPaise } from "@/lib/format";
import { openRazorpaySubscription, type SubscriptionCheckout } from "@/lib/razorpay";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading } from "@/components/ui";
import { useToast } from "@/components/toast";

type Overview = components["schemas"]["BillingOverviewResponse"];

export default function BillingPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ["owner", "billing", restaurantId];
  const path = { restaurantId };
  const overview = useQuery({ queryKey: key, queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/billing", { params: { path } })) });
  const [mock, setMock] = useState<SubscriptionCheckout | null>(null);

  const settle = (next: Overview) => {
    queryClient.setQueryData(key, next);
    // Paying can reinstate a suspended listing, so the header badge may change too.
    void queryClient.invalidateQueries({ queryKey: ownerKeys.restaurant(restaurantId) });
    void queryClient.invalidateQueries({ queryKey: ownerKeys.restaurants });
  };

  const confirm = useMutation({
    mutationFn: (body: { subscriptionId: string; paymentId: string; signature: string }) =>
      unwrap(api.POST("/api/owner/restaurants/{restaurantId}/billing/checkout/confirm", { params: { path }, body })),
    onSuccess: (next) => {
      setMock(null);
      settle(next);
      toast.success("Payment method set up. You'll be charged automatically each month.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const checkout = useMutation({
    mutationFn: (plan: Plan) => unwrap(api.POST("/api/owner/restaurants/{restaurantId}/billing/checkout", { params: { path }, body: { planId: plan.id } })),
    onSuccess: async (started) => {
      void queryClient.invalidateQueries({ queryKey: key });
      if (started.provider !== "razorpay") {
        setMock(started);
        return;
      }
      const outcome = await openRazorpaySubscription(started);
      if (outcome.kind === "authorised") confirm.mutate(outcome.result);
      else if (outcome.lastError) toast.error(outcome.lastError);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const changePlan = useMutation({
    mutationFn: (plan: Plan) => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/billing/plan", { params: { path }, body: { planId: plan.id } })),
    onSuccess: (next, plan) => {
      settle(next);
      toast.success(next.pendingPlan?.id === plan.id ? `You'll move to ${plan.name} at the next charge.` : `You're now on ${next.plan.name}.`);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const cancel = useMutation({
    mutationFn: () => unwrap(api.POST("/api/owner/restaurants/{restaurantId}/billing/cancel", { params: { path } })),
    onSuccess: (next) => {
      settle(next);
      toast.info("Recurring payment cancelled. Your listing stays up until the paid period ends.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const [downloading, setDownloading] = useState<string | null>(null);
  const download = async (invoice: Invoice) => {
    setDownloading(invoice.id);
    try {
      const result = await api.GET("/api/owner/restaurants/{restaurantId}/billing/invoices/{invoiceId}.pdf", {
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

  if (overview.isPending) return <PageLoading />;
  if (overview.isError) return <ErrorState message={errorMessage(overview.error)} onRetry={() => overview.refetch()} />;
  const o = overview.data;
  const status = SUBSCRIPTION_STATUS[o.status];
  const mandateLive = o.paymentMethodReady && !o.cancelRequested && o.status !== "CANCELLED";
  const busy = checkout.isPending || confirm.isPending || changePlan.isPending || cancel.isPending;

  return (
    <div className="space-y-4">
      <Card className="space-y-2">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-lg font-semibold">{o.plan.name} plan</h2>
          <Badge tone={status.tone}>{status.label}</Badge>
          <span className="text-sm text-stone-600">
            {formatPaise(o.plan.pricePaise)}/month + GST ({formatPaise(o.plan.totalPaise)} total)
          </span>
        </div>
        <StatusBanner o={o} />
        <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-3">
          <Fact label="Next charge" value={o.nextChargeAt ? formatDate(o.nextChargeAt) : "Not scheduled"} />
          <Fact label="Paid until" value={o.currentPeriodEnd ? formatDate(o.currentPeriodEnd) : "—"} />
          <Fact label="Payment method" value={mandateLive ? "Set up (autopay)" : o.cancelRequested ? "Cancelled" : "Not set up"} />
        </dl>
        {o.pendingPlan && (
          <p className="text-sm text-stone-600">
            Moving to <strong>{o.pendingPlan.name}</strong> at the next charge.
          </p>
        )}
        {!o.billingDetailsComplete && (
          <p className="rounded-lg bg-amber-50 p-2 text-sm text-amber-900">
            Add your legal name and GST state under{" "}
            <Link href={`/dashboard/${restaurantId}/profile`} className="underline">
              Profile → Licence & tax details
            </Link>{" "}
            before setting up payment. They go on your invoices.
          </p>
        )}
        {mandateLive && (
          <div className="pt-1">
            <ConfirmButton label="Cancel recurring payment" confirmLabel="Yes, cancel autopay" onConfirm={() => cancel.mutate()} />
          </div>
        )}
      </Card>

      {mock && (
        <Card className="space-y-2 border-amber-300 bg-amber-50">
          <p className="text-sm text-amber-900">
            Test mode (mock gateway): no real money moves. Approving sets up the mandate; charges are then simulated from the admin panel.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button busy={confirm.isPending} onClick={() => confirm.mutate({ subscriptionId: mock.subscriptionId, paymentId: `pay_mock_${crypto.randomUUID().slice(0, 8)}`, signature: "mock" })}>
              Approve autopay of {formatPaise(mock.amountPaise)}/month
            </Button>
            <Button variant="ghost" disabled={confirm.isPending} onClick={() => confirm.mutate({ subscriptionId: mock.subscriptionId, paymentId: "fail_mock", signature: "mock" })}>
              Simulate a failure
            </Button>
          </div>
        </Card>
      )}

      <section className="space-y-2">
        <h2 className="font-semibold">Plans</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          {o.plans.map((plan) => {
            const current = plan.id === o.plan.id;
            const scheduled = o.pendingPlan?.id === plan.id;
            const upgrade = plan.pricePaise > o.plan.pricePaise;
            return (
              <Card key={plan.id} className={`flex flex-col gap-2 ${current ? "ring-2 ring-brand-500" : ""}`}>
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold">{plan.name}</h3>
                  {current && <Badge tone="brand">Current</Badge>}
                  {scheduled && <Badge tone="amber">Next</Badge>}
                </div>
                <p>
                  <span className="text-2xl font-black">{formatPaise(plan.pricePaise)}</span>
                  <span className="text-sm text-stone-500">/month + GST</span>
                </p>
                <ul className="flex-1 list-disc pl-5 text-sm text-stone-700">
                  {planFeatureLines(plan).map((line) => (
                    <li key={line}>{line}</li>
                  ))}
                </ul>
                {!mandateLive ? (
                  <Button variant={current ? "primary" : "secondary"} busy={checkout.isPending && checkout.variables?.id === plan.id} disabled={busy || !o.billingDetailsComplete} onClick={() => checkout.mutate(plan)}>
                    {current ? "Set up payment" : `Choose ${plan.name}`}
                  </Button>
                ) : current ? (
                  o.pendingPlan ? (
                    <Button variant="secondary" busy={changePlan.isPending} disabled={busy} onClick={() => changePlan.mutate(plan)}>
                      Keep {plan.name}
                    </Button>
                  ) : null
                ) : (
                  <Button variant={upgrade ? "primary" : "secondary"} busy={changePlan.isPending && changePlan.variables?.id === plan.id} disabled={busy || scheduled} onClick={() => changePlan.mutate(plan)}>
                    {scheduled ? "Scheduled" : upgrade ? `Upgrade to ${plan.name}` : `Downgrade to ${plan.name}`}
                  </Button>
                )}
              </Card>
            );
          })}
        </div>
        <p className="text-xs text-stone-500">{"Upgrades apply straight away. Downgrades apply at the next charge, so you keep what you've paid for."}</p>
      </section>

      <section className="space-y-2">
        <h2 className="font-semibold">Invoices</h2>
        {o.invoices.length === 0 ? (
          <EmptyState title="No invoices yet" message="A GST invoice is issued for every successful charge." />
        ) : (
          <Card className="overflow-x-auto p-0">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-stone-200 text-stone-500">
                <tr>
                  <th className="px-3 py-2 font-medium">Invoice</th>
                  <th className="px-3 py-2 font-medium">Date</th>
                  <th className="px-3 py-2 font-medium">Plan</th>
                  <th className="px-3 py-2 text-right font-medium">GST</th>
                  <th className="px-3 py-2 text-right font-medium">Total</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody>
                {o.invoices.map((inv) => (
                  <tr key={inv.id} className="border-b border-stone-100 last:border-0">
                    <td className="px-3 py-2 font-mono text-xs">{inv.number}</td>
                    <td className="px-3 py-2">{formatDate(inv.issuedAt)}</td>
                    <td className="px-3 py-2">{inv.planName}</td>
                    <td className="px-3 py-2 text-right">{formatPaise(inv.cgstPaise + inv.sgstPaise + inv.igstPaise)}</td>
                    <td className="px-3 py-2 text-right font-semibold">{formatPaise(inv.totalPaise)}</td>
                    <td className="px-3 py-2 text-right">
                      <Button variant="ghost" busy={downloading === inv.id} onClick={() => download(inv)}>
                        PDF
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )}
      </section>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-stone-500">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

function StatusBanner({ o }: { o: Overview }) {
  const box = (tone: string, text: string) => <p className={`rounded-lg p-2 text-sm ${tone}`}>{text}</p>;
  switch (o.status) {
    case "TRIALING": {
      const days = daysUntil(o.trialEndsAt);
      return box(
        "bg-brand-50 text-brand-700",
        o.paymentMethodReady
          ? `Free trial: ${days} ${days === 1 ? "day" : "days"} left. Your first charge is on ${formatDate(o.trialEndsAt)}.`
          : `Free trial: ${days} ${days === 1 ? "day" : "days"} left (ends ${formatDate(o.trialEndsAt)}). Set up payment to stay listed afterwards.`,
      );
    }
    case "PAST_DUE":
      return box(
        "bg-amber-50 text-amber-900",
        o.graceUntil
          ? `We couldn't collect payment. Your listing is hidden on ${formatDate(o.graceUntil)} unless a payment goes through.`
          : "We couldn't collect payment.",
      );
    case "SUSPENDED":
      return box("bg-red-50 text-red-900", "Your listing is hidden because the subscription is unpaid. Your data is kept; set up payment to go live again.");
    case "CANCELLED":
      return box("bg-stone-100 text-stone-700", "The subscription is cancelled. Choose a plan to set up payment again.");
    default:
      return null;
  }
}
