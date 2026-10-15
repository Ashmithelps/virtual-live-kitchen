"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { SUBSCRIPTION_STATUS, formatDate, planFeatureLines, type Plan } from "@/lib/billing";
import { formatPaise, paiseFromRupees } from "@/lib/format";
import { Badge, Button, Card, EmptyState, ErrorState, Field, InlineError, PageLoading, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

/** Plans and prices, trial and grace lengths, and every restaurant's subscription. All changes are audited. */
export default function AdminBillingPage() {
  const plans = useQuery({ queryKey: ["admin", "billing", "plans"], queryFn: () => unwrap(api.GET("/api/admin/billing/plans")) });
  const subscriptions = useQuery({ queryKey: ["admin", "billing", "subscriptions"], queryFn: () => unwrap(api.GET("/api/admin/billing/subscriptions")) });
  const [editing, setEditing] = useState<Plan | "new" | null>(null);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Billing</h1>
      <SettingsCard />

      <section className="space-y-2">
        <div className="flex items-center gap-3">
          <h2 className="mr-auto text-lg font-semibold">Plans</h2>
          {editing === null && <Button onClick={() => setEditing("new")}>New plan</Button>}
        </div>
        {editing !== null && <PlanForm plan={editing === "new" ? null : editing} onDone={() => setEditing(null)} />}
        {plans.isPending ? (
          <PageLoading />
        ) : plans.isError ? (
          <ErrorState message={errorMessage(plans.error)} onRetry={() => plans.refetch()} />
        ) : (
          <div className="grid gap-3 md:grid-cols-3">
            {plans.data.map((plan) => (
              <Card key={plan.id} className={`space-y-1 ${plan.active ? "" : "opacity-60"}`}>
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold">{plan.name}</h3>
                  <span className="font-mono text-xs text-stone-500">{plan.code}</span>
                  {!plan.active && <Badge>Hidden</Badge>}
                </div>
                <p>
                  <span className="text-xl font-black">{formatPaise(plan.pricePaise)}</span>
                  <span className="text-sm text-stone-500">/month + GST</span>
                </p>
                <ul className="list-disc pl-5 text-sm text-stone-700">
                  {planFeatureLines(plan).map((line) => (
                    <li key={line}>{line}</li>
                  ))}
                </ul>
                <Button variant="secondary" disabled={editing !== null} onClick={() => setEditing(plan)}>
                  Edit
                </Button>
              </Card>
            ))}
          </div>
        )}
        <p className="text-xs text-stone-500">
          A new price applies to new subscriptions and plan changes. Existing autopay mandates keep the price they were set up with until the owner changes plan.
        </p>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Subscriptions</h2>
        {subscriptions.isPending ? (
          <PageLoading />
        ) : subscriptions.isError ? (
          <ErrorState message={errorMessage(subscriptions.error)} onRetry={() => subscriptions.refetch()} />
        ) : subscriptions.data.length === 0 ? (
          <EmptyState title="No subscriptions yet" />
        ) : (
          <Card className="overflow-x-auto p-0">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-stone-200 text-stone-500">
                <tr>
                  <th className="px-3 py-2 font-medium">Restaurant</th>
                  <th className="px-3 py-2 font-medium">Status</th>
                  <th className="px-3 py-2 font-medium">Plan</th>
                  <th className="px-3 py-2 font-medium">Trial ends</th>
                  <th className="px-3 py-2 font-medium">Paid until</th>
                  <th className="px-3 py-2 font-medium">Grace until</th>
                  <th className="px-3 py-2 font-medium">Autopay</th>
                </tr>
              </thead>
              <tbody>
                {subscriptions.data.map((s) => (
                  <tr key={s.restaurantId} className="border-b border-stone-100 last:border-0">
                    <td className="px-3 py-2">
                      <Link href={`/admin/restaurants/${s.restaurantId}`} className="text-brand-700 underline">
                        {s.restaurantName}
                      </Link>
                    </td>
                    <td className="px-3 py-2">
                      <Badge tone={SUBSCRIPTION_STATUS[s.status].tone}>{SUBSCRIPTION_STATUS[s.status].label}</Badge>
                    </td>
                    <td className="px-3 py-2">{s.planName}</td>
                    <td className="px-3 py-2">{formatDate(s.trialEndsAt)}</td>
                    <td className="px-3 py-2">{s.currentPeriodEnd ? formatDate(s.currentPeriodEnd) : "—"}</td>
                    <td className="px-3 py-2">{s.graceUntil ? formatDate(s.graceUntil) : "—"}</td>
                    <td className="px-3 py-2">{s.paymentMethodReady ? "Yes" : "No"}</td>
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

function SettingsCard() {
  const key = ["admin", "billing", "settings"];
  const settings = useQuery({ queryKey: key, queryFn: () => unwrap(api.GET("/api/admin/billing/settings")) });
  if (settings.isPending) return <PageLoading />;
  if (settings.isError) return <ErrorState message={errorMessage(settings.error)} onRetry={() => settings.refetch()} />;
  return <SettingsForm saved={settings.data} />;
}

function SettingsForm({ saved }: { saved: components["schemas"]["BillingSettingsResponse"] }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ["admin", "billing", "settings"];
  const [form, setForm] = useState({ trialDays: String(saved.trialDays), graceDays: String(saved.graceDays) });
  const save = useMutation({
    mutationFn: () => unwrap(api.PUT("/api/admin/billing/settings", { body: { trialDays: Number(form.trialDays), graceDays: Number(form.graceDays) } })),
    onSuccess: (next) => {
      queryClient.setQueryData(key, next);
      toast.success("Billing settings saved");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const valid = /^\d+$/.test(form.trialDays) && Number(form.trialDays) <= 365 && /^\d+$/.test(form.graceDays) && Number(form.graceDays) <= 60;
  return (
    <Card>
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <div className="w-36">
          <Field label="Free trial (days)" hint="New restaurants">
            <input className={inputClass} inputMode="numeric" value={form.trialDays} onChange={(e) => setForm({ ...form, trialDays: e.target.value })} />
          </Field>
        </div>
        <div className="w-36">
          <Field label="Grace (days)" hint="After a failed payment">
            <input className={inputClass} inputMode="numeric" value={form.graceDays} onChange={(e) => setForm({ ...form, graceDays: e.target.value })} />
          </Field>
        </div>
        <Button type="submit" busy={save.isPending} disabled={!valid}>
          Save
        </Button>
        <p className="text-sm text-stone-500">
          Gateway: <strong>{saved.provider}</strong> · GST {saved.gstRateBps / 100}%
        </p>
      </form>
    </Card>
  );
}

function PlanForm({ plan, onDone }: { plan: Plan | null; onDone: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({
    code: plan?.code ?? "",
    name: plan?.name ?? "",
    price: plan ? String(plan.pricePaise / 100) : "",
    maxStations: String(plan?.features.maxStations ?? 2),
    clipRetentionDays: String(plan?.features.clipRetentionDays ?? 30),
    analytics: plan?.features.analytics ?? false,
    active: plan?.active ?? true,
    sortOrder: String(plan?.sortOrder ?? 0),
  });
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: (body: components["schemas"]["PlanRequest"]) =>
      plan
        ? unwrap(api.PUT("/api/admin/billing/plans/{planId}", { params: { path: { planId: plan.id } }, body }))
        : unwrap(api.POST("/api/admin/billing/plans", { body })),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "billing"] });
      toast.success(`${saved.name} saved`);
      onDone();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    const pricePaise = paiseFromRupees(form.price);
    const int = (v: string) => (/^\d+$/.test(v) ? Number(v) : NaN);
    if (!plan && !/^[a-z0-9-]{2,40}$/.test(form.code)) return setError("Code: 2–40 lowercase letters, digits or dashes.");
    if (!form.name.trim()) return setError("Name is required.");
    if (pricePaise === null) return setError("Enter a valid monthly price in rupees.");
    const maxStations = int(form.maxStations);
    const clipRetentionDays = int(form.clipRetentionDays);
    const sortOrder = int(form.sortOrder);
    if (!(maxStations >= 1) || !(clipRetentionDays >= 1) || !(sortOrder >= 0)) return setError("Stations and retention must be at least 1; sort order 0 or more.");
    save.mutate({
      code: plan ? undefined : form.code,
      name: form.name.trim(),
      pricePaise,
      features: { maxStations, clipRetentionDays, analytics: form.analytics },
      active: form.active,
      sortOrder,
    });
  };

  return (
    <Card>
      <form onSubmit={submit} className="space-y-3" noValidate>
        <h3 className="font-semibold">{plan ? `Edit ${plan.name}` : "New plan"}</h3>
        <div className="grid gap-3 sm:grid-cols-4">
          <Field label="Code" hint={plan ? "Fixed" : "e.g. growth"}>
            <input className={inputClass} value={form.code} disabled={!!plan} onChange={(e) => setForm({ ...form, code: e.target.value })} />
          </Field>
          <Field label="Name">
            <input className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label="Price ₹/month" hint="Excluding GST">
            <input className={inputClass} inputMode="decimal" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
          </Field>
          <Field label="Sort order">
            <input className={inputClass} inputMode="numeric" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} />
          </Field>
          <Field label="Max stations">
            <input className={inputClass} inputMode="numeric" value={form.maxStations} onChange={(e) => setForm({ ...form, maxStations: e.target.value })} />
          </Field>
          <Field label="Clip retention (days)">
            <input className={inputClass} inputMode="numeric" value={form.clipRetentionDays} onChange={(e) => setForm({ ...form, clipRetentionDays: e.target.value })} />
          </Field>
          <label className="flex items-center gap-2 self-end pb-3 text-sm">
            <input type="checkbox" checked={form.analytics} onChange={(e) => setForm({ ...form, analytics: e.target.checked })} /> Analytics
          </label>
          <label className="flex items-center gap-2 self-end pb-3 text-sm">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Offered to owners
          </label>
        </div>
        <InlineError message={error} />
        <div className="flex gap-2">
          <Button type="submit" busy={save.isPending}>
            Save plan
          </Button>
          <Button type="button" variant="ghost" onClick={onDone}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  );
}
