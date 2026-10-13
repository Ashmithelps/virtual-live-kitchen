"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { errorMessage } from "@/lib/api/errors";
import { restaurantsQuery } from "@/lib/owner/queries";
import { RESTAURANT_STATUS } from "@/lib/owner/status";
import { Badge, Card, EmptyState, ErrorState, PageLoading } from "@/components/ui";

/** Owner home: their restaurants (an owner may have several). */
export default function DashboardHome() {
  const restaurants = useQuery(restaurantsQuery());

  if (restaurants.isPending) return <PageLoading />;
  if (restaurants.isError) return <ErrorState message={errorMessage(restaurants.error)} onRetry={() => restaurants.refetch()} />;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-bold">Your restaurants</h1>
        <Link href="/dashboard/new" className="rounded-xl bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700">
          Add restaurant
        </Link>
      </div>
      {restaurants.data.length === 0 ? (
        <EmptyState
          title="No restaurants yet"
          message="Create your restaurant, then an admin approves it before customers can see it."
          action={
            <Link href="/dashboard/new" className="font-medium text-brand-700 underline">
              Create your first restaurant
            </Link>
          }
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {restaurants.data.map((r) => (
            <Link key={r.id} href={`/dashboard/${r.id}`}>
              <Card className="h-full transition hover:border-brand-300">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-lg font-semibold">{r.name}</p>
                    <p className="text-sm text-stone-500">
                      {r.city} · /r/{r.slug}
                    </p>
                  </div>
                  <Badge tone={RESTAURANT_STATUS[r.status].tone}>{RESTAURANT_STATUS[r.status].label}</Badge>
                </div>
                {r.status === "PENDING_APPROVAL" && <p className="mt-2 text-sm text-stone-600">Customers can&apos;t see it until an admin approves it. You can set up the menu meanwhile.</p>}
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
