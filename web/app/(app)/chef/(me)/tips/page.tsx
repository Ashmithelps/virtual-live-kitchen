"use client";

import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { formatDateTime, formatPaise } from "@/lib/format";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading } from "@/components/ui";

const PAGE = 20;

/** "My tips": tips received, newest first (no customer details). */
export default function MyTipsPage() {
  const [page, setPage] = useState(0);
  const tips = useQuery({
    queryKey: ["chef-tips", page],
    queryFn: () => unwrap(api.GET("/api/chef/tips", { params: { query: { page, size: PAGE } } })),
    placeholderData: keepPreviousData,
  });
  if (tips.isPending) return <PageLoading />;
  if (tips.isError) return <ErrorState message={errorMessage(tips.error)} onRetry={() => tips.refetch()} />;
  const t = tips.data;
  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold">My tips</h1>
      <Card className="grid grid-cols-2 gap-2 text-center">
        <div>
          <p className="text-2xl font-bold">{t.tipCount}</p>
          <p className="text-sm text-stone-500">tips received</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{formatPaise(t.totalChefAmountPaise)}</p>
          <p className="text-sm text-stone-500">to your bank account</p>
        </div>
      </Card>
      {t.items.length === 0 ? (
        <EmptyState
          title="No tips yet"
          message="Customers can tip you after watching the clip of their order."
          action={<Link href="/chef/payouts" className="font-medium text-brand-700 underline">Check your payout set-up</Link>}
        />
      ) : (
        <>
          <ul className="divide-y divide-stone-100 rounded-2xl border border-stone-200 bg-white">
            {t.items.map((tip) => (
              <li key={tip.id} className="flex flex-wrap items-center gap-2 px-4 py-3">
                <div className="mr-auto">
                  <p className="font-semibold">{formatPaise(tip.chefAmountPaise)}</p>
                  <p className="text-sm text-stone-500">
                    Order #{tip.orderDisplayNumber} · {tip.source === "CLIP" ? "after watching the clip" : "at checkout"} · {formatDateTime(tip.paidAt ?? tip.createdAt)}
                  </p>
                </div>
                <Badge tone={tip.status === "TRANSFERRED" ? "green" : "amber"}>{tip.status === "TRANSFERRED" ? "Sent to your bank" : "On its way"}</Badge>
              </li>
            ))}
          </ul>
          <div className="flex items-center justify-between">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Newer</Button>
            <span className="text-sm text-stone-500">Page {page + 1} of {Math.max(1, t.totalPages)}</span>
            <Button variant="secondary" disabled={page + 1 >= t.totalPages} onClick={() => setPage((p) => p + 1)}>Older</Button>
          </div>
        </>
      )}
    </div>
  );
}
