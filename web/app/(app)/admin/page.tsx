"use client";

import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { SUBSCRIPTION_STATUS, type SubscriptionStatus } from "@/lib/billing";
import { formatPaise } from "@/lib/format";
import { Badge, Card, ErrorState, PageLoading, inputBaseClass } from "@/components/ui";

type Day = components["schemas"]["Day"];
type Series = { key: Exclude<keyof Day, "date">; label: string; money?: boolean };

const SERIES: Series[] = [
  { key: "orders", label: "Orders" },
  { key: "gmvPaise", label: "GMV", money: true },
  { key: "clips", label: "Clips generated" },
  { key: "clipViews", label: "Clip views" },
  { key: "shares", label: "Shares" },
  { key: "tips", label: "Tips" },
];

/** Platform overview: daily activity, active restaurants and recurring revenue. */
export default function AdminOverviewPage() {
  const [days, setDays] = useState(30);
  const metrics = useQuery({
    queryKey: ["admin", "metrics", days],
    queryFn: () => unwrap(api.GET("/api/admin/metrics", { params: { query: { days } } })),
    placeholderData: keepPreviousData,
  });

  if (metrics.isPending) return <PageLoading />;
  if (metrics.isError) return <ErrorState message={errorMessage(metrics.error)} onRetry={() => metrics.refetch()} />;
  const m = metrics.data;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="mr-auto text-2xl font-bold">Overview</h1>
        <select className={`${inputBaseClass} w-40`} aria-label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={7}>Last 7 days</option>
          <option value={30}>Last 30 days</option>
          <option value={90}>Last 90 days</option>
        </select>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Card>
          <p className="text-sm text-stone-500">Active restaurants</p>
          <p className="text-3xl font-black">{m.activeRestaurants}</p>
          <Link href="/admin/restaurants" className="text-sm text-brand-700 underline">
            Manage restaurants
          </Link>
        </Card>
        <Card>
          <p className="text-sm text-stone-500">MRR (excl. GST)</p>
          <p className="text-3xl font-black">{formatPaise(m.mrrPaise)}</p>
          <p className="text-xs text-stone-500">Plan prices of active subscriptions</p>
        </Card>
        <Card className="space-y-1">
          <p className="text-sm text-stone-500">Subscriptions</p>
          <div className="flex flex-wrap gap-1">
            {Object.entries(m.subscriptionsByStatus).length === 0 && <span className="text-sm text-stone-500">None yet</span>}
            {Object.entries(m.subscriptionsByStatus).map(([status, count]) => {
              const s = SUBSCRIPTION_STATUS[status as SubscriptionStatus];
              return (
                <Badge key={status} tone={s?.tone ?? "stone"}>
                  {s?.label ?? status}: {count}
                </Badge>
              );
            })}
          </div>
          <Link href="/admin/billing" className="text-sm text-brand-700 underline">
            Billing
          </Link>
        </Card>
      </div>

      <div className={`grid gap-3 md:grid-cols-2 ${metrics.isFetching ? "opacity-70" : ""}`}>
        {SERIES.map((s) => (
          <SeriesCard key={s.key} series={s} days={m.series} total={m.totals[s.key]} />
        ))}
      </div>
    </div>
  );
}

function SeriesCard({ series, days, total }: { series: Series; days: Day[]; total: number }) {
  const show = (v: number) => (series.money ? formatPaise(v) : v.toLocaleString("en-IN"));
  const max = Math.max(1, ...days.map((d) => d[series.key]));
  return (
    <Card className="space-y-2">
      <div className="flex items-baseline justify-between gap-2">
        <h2 className="font-semibold">{series.label}</h2>
        <span className="text-lg font-bold">{show(total)}</span>
      </div>
      <div className="flex h-24 items-end gap-px" role="img" aria-label={`${series.label} per day`}>
        {days.map((d) => (
          <div
            key={d.date}
            title={`${d.date}: ${show(d[series.key])}`}
            className="flex-1 rounded-t bg-brand-500"
            style={{ height: `${(d[series.key] / max) * 100}%`, minHeight: d[series.key] > 0 ? 2 : 0 }}
          />
        ))}
      </div>
      <div className="flex justify-between text-xs text-stone-500">
        <span>{days[0]?.date}</span>
        <span>{days.at(-1)?.date}</span>
      </div>
    </Card>
  );
}
