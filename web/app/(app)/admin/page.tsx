"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { Restaurant } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { RESTAURANT_STATUS } from "@/lib/owner/status";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading } from "@/components/ui";
import { useToast } from "@/components/toast";

type Status = Restaurant["status"];
const TABS: Status[] = ["PENDING_APPROVAL", "ACTIVE", "SUSPENDED"];

/** Minimal admin: approve owners and restaurants, suspend restaurants. (Full admin panel: Milestone 14.) */
export default function AdminRestaurantsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [tab, setTab] = useState<Status>("PENDING_APPROVAL");

  const restaurants = useQuery({
    queryKey: ["admin", "restaurants", tab],
    queryFn: () => unwrap(api.GET("/api/admin/restaurants", { params: { query: { status: tab } } })),
  });
  const owners = useQuery({ queryKey: ["admin", "pending-owners"], queryFn: () => unwrap(api.GET("/api/admin/owners/pending")) });

  const act = useMutation({
    mutationFn: (task: { work: () => Promise<unknown>; done: string }) => task.work(),
    onSuccess: (_, task) => toast.success(task.done),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["admin"] }),
  });

  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <h1 className="text-2xl font-bold">Owners awaiting approval</h1>
        {owners.isPending ? (
          <PageLoading />
        ) : owners.isError ? (
          <ErrorState message={errorMessage(owners.error)} onRetry={() => owners.refetch()} />
        ) : owners.data.length === 0 ? (
          <p className="text-sm text-stone-500">No owners waiting.</p>
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {owners.data.map((owner) => (
              <Card key={owner.id} className="flex flex-wrap items-center gap-3">
                <div className="mr-auto">
                  <p className="font-semibold">{owner.name}</p>
                  <p className="text-sm text-stone-500">
                    {owner.phone} · signed up {formatDateTime(owner.createdAt)}
                  </p>
                </div>
                <Button
                  disabled={act.isPending}
                  onClick={() => act.mutate({ work: () => unwrap(api.POST("/api/admin/owners/{userId}/approve", { params: { path: { userId: owner.id } } })), done: `${owner.name} approved` })}
                >
                  Approve owner
                </Button>
              </Card>
            ))}
          </div>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-2xl font-bold">Restaurants</h2>
        <div className="flex gap-1 border-b border-stone-200" role="tablist">
          {TABS.map((t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`border-b-2 px-3 py-2 text-sm font-medium ${tab === t ? "border-brand-600 text-brand-700" : "border-transparent text-stone-600"}`}
            >
              {RESTAURANT_STATUS[t].label}
            </button>
          ))}
        </div>
        {restaurants.isPending ? (
          <PageLoading />
        ) : restaurants.isError ? (
          <ErrorState message={errorMessage(restaurants.error)} onRetry={() => restaurants.refetch()} />
        ) : restaurants.data.length === 0 ? (
          <EmptyState title="Nothing here" message={`No ${RESTAURANT_STATUS[tab].label.toLowerCase()} restaurants.`} />
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {restaurants.data.map((r) => (
              <Card key={r.id} className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">{r.name}</p>
                    <p className="text-sm text-stone-500">
                      {r.address}, {r.city}
                    </p>
                    <p className="text-xs text-stone-500">
                      /r/{r.slug} · {r.orderTypes.join(", ").replaceAll("_", "-").toLowerCase()} · created {formatDateTime(r.createdAt)}
                    </p>
                  </div>
                  <Badge tone={RESTAURANT_STATUS[r.status].tone}>{RESTAURANT_STATUS[r.status].label}</Badge>
                </div>
                <div className="flex gap-2">
                  {r.status !== "ACTIVE" && (
                    <Button
                      disabled={act.isPending}
                      onClick={() => act.mutate({ work: () => unwrap(api.POST("/api/admin/restaurants/{restaurantId}/approve", { params: { path: { restaurantId: r.id } } })), done: `${r.name} is live` })}
                    >
                      {r.status === "SUSPENDED" ? "Reinstate" : "Approve"}
                    </Button>
                  )}
                  {r.status !== "SUSPENDED" && (
                    <ConfirmButton
                      label="Suspend"
                      confirmLabel="Hide from customers?"
                      onConfirm={() => act.mutate({ work: () => unwrap(api.POST("/api/admin/restaurants/{restaurantId}/suspend", { params: { path: { restaurantId: r.id } } })), done: `${r.name} suspended` })}
                    />
                  )}
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
