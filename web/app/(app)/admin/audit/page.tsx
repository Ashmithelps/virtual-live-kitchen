"use client";

import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { formatDateTime } from "@/lib/format";
import { Button, EmptyState, ErrorState, PageLoading, inputBaseClass } from "@/components/ui";

const PAGE_SIZE = 50;

/** Actions recorded by `AdminAudit` on the backend. */
const ACTIONS: Record<string, string> = {
  RESTAURANT_APPROVED: "Restaurant approved",
  RESTAURANT_SUSPENDED: "Restaurant suspended",
  RESTAURANT_VIEWED: "Restaurant details viewed",
  OWNER_APPROVED: "Owner approved",
  CLIP_VIEWED: "Clip viewed",
  HYGIENE_DISPUTE_RESOLVED: "Hygiene dispute resolved",
  HYGIENE_PUBLIC_SCORES_SET: "Public hygiene scores toggled",
  PLAN_CREATED: "Plan created",
  PLAN_UPDATED: "Plan updated",
  BILLING_SETTINGS_UPDATED: "Billing settings updated",
  BILLING_SIMULATED: "Billing event simulated",
};

/** Read-only, newest first: who did what, to which record. */
export default function AdminAuditPage() {
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);
  const entries = useQuery({
    queryKey: ["admin", "audit", action, page],
    queryFn: () => unwrap(api.GET("/api/admin/audit", { params: { query: { action: action || undefined, page, size: PAGE_SIZE } } })),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="mr-auto text-2xl font-bold">Audit log</h1>
        <select
          className={`${inputBaseClass} w-64`}
          aria-label="Filter by action"
          value={action}
          onChange={(e) => {
            setAction(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All actions</option>
          {Object.entries(ACTIONS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      {entries.isPending ? (
        <PageLoading />
      ) : entries.isError ? (
        <ErrorState message={errorMessage(entries.error)} onRetry={() => entries.refetch()} />
      ) : entries.data.items.length === 0 ? (
        <EmptyState title="No entries" message="Admin actions appear here as they happen." />
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-stone-200 bg-white">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="bg-stone-50 text-stone-600">
                <tr>
                  <th className="px-3 py-2 font-medium">When</th>
                  <th className="px-3 py-2 font-medium">Admin</th>
                  <th className="px-3 py-2 font-medium">Action</th>
                  <th className="px-3 py-2 font-medium">Target</th>
                  <th className="px-3 py-2 font-medium">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {entries.data.items.map((e) => (
                  <tr key={e.id} className="align-top">
                    <td className="whitespace-nowrap px-3 py-2 text-stone-500">{formatDateTime(e.createdAt)}</td>
                    <td className="px-3 py-2">{e.adminName}</td>
                    <td className="px-3 py-2">{ACTIONS[e.action] ?? e.action}</td>
                    <td className="px-3 py-2">
                      {e.targetType === "restaurant" && e.targetId ? (
                        <Link href={`/admin/restaurants/${e.targetId}`} className="text-brand-700 underline">
                          restaurant
                        </Link>
                      ) : (
                        <span>{e.targetType.replaceAll("_", " ")}</span>
                      )}
                      {e.targetId && <span className="block font-mono text-xs text-stone-400">{e.targetId.slice(0, 8)}</span>}
                    </td>
                    <td className="px-3 py-2">
                      {Object.keys(e.details).length > 0 && <code className="break-all text-xs text-stone-600">{JSON.stringify(e.details)}</code>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="text-sm text-stone-500">
              {entries.data.totalItems} entries · page {page + 1} of {Math.max(1, entries.data.totalPages)}
            </span>
            <Button variant="secondary" disabled={page + 1 >= entries.data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
