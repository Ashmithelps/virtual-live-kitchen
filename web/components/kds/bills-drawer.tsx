"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { kitchenApi } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { Order } from "@/lib/api/types";
import { formatPaise, formatTime } from "@/lib/format";
import { fulfilmentLabel } from "@/lib/kds/board";
import { STATUS_LABEL } from "@/lib/order-status";

type Method = "CASH" | "UPI" | "CARD";

export const unpaidBillsKey = (restaurantId: string) => ["kds-unpaid", restaurantId] as const;

export function useUnpaidBills(restaurantId: string, enabled: boolean) {
  return useQuery({
    queryKey: unpaidBillsKey(restaurantId),
    enabled,
    queryFn: () => unwrap(kitchenApi.GET("/api/owner/restaurants/{restaurantId}/orders/unpaid", { params: { path: { restaurantId } } })),
  });
}

/** Owner only: open pay-at-table bills (served ones included) settled in person. */
export function BillsDrawer({ restaurantId, onClose }: { restaurantId: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const bills = useUnpaidBills(restaurantId, true);
  const settle = useMutation({
    mutationFn: ({ order, method }: { order: Order; method: Method }) =>
      unwrap(
        kitchenApi.POST("/api/owner/restaurants/{restaurantId}/orders/{orderId}/payment/counter", {
          params: { path: { restaurantId, orderId: order.id } },
          body: { method },
        }),
      ),
    onSettled: () => queryClient.invalidateQueries({ queryKey: unpaidBillsKey(restaurantId) }),
  });

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-black/60" onClick={onClose}>
      <aside className="flex h-full w-full max-w-xl flex-col bg-stone-900 shadow-2xl" onClick={(e) => e.stopPropagation()} aria-label="Unpaid bills">
        <header className="flex items-center justify-between border-b border-stone-800 p-4">
          <h2 className="text-2xl font-bold">Unpaid bills</h2>
          <button onClick={onClose} className="min-h-12 rounded-xl bg-stone-800 px-4 text-lg font-semibold">
            Close
          </button>
        </header>
        {settle.isError && <p className="m-4 rounded-xl bg-red-950 px-4 py-3 text-red-200">{errorMessage(settle.error)}</p>}
        <div className="flex-1 space-y-3 overflow-y-auto p-4">
          {bills.isPending ? (
            <p className="text-stone-400">Loading…</p>
          ) : bills.isError ? (
            <p className="text-red-300">{errorMessage(bills.error)}</p>
          ) : bills.data.length === 0 ? (
            <p className="py-12 text-center text-xl text-stone-500">All bills are settled.</p>
          ) : (
            bills.data.map((order) => (
              <div key={order.id} className="rounded-2xl bg-stone-800 p-4">
                <div className="flex items-baseline justify-between gap-2">
                  <p className="text-3xl font-black">#{order.displayNumber}</p>
                  <p className="text-2xl font-bold">{formatPaise(order.totalPaise)}</p>
                </div>
                <p className="text-stone-400">
                  {fulfilmentLabel(order)} · {STATUS_LABEL[order.status]} · {formatTime(order.timeline.placedAt)}
                </p>
                <div className="mt-3 grid grid-cols-3 gap-2">
                  {(["CASH", "UPI", "CARD"] as const).map((method) => (
                    <button
                      key={method}
                      disabled={settle.isPending}
                      onClick={() => settle.mutate({ order, method })}
                      className="min-h-14 rounded-xl bg-green-700 text-lg font-bold text-white active:bg-green-800 disabled:opacity-50"
                    >
                      {settle.isPending && settle.variables?.order.id === order.id && settle.variables.method === method ? "…" : `Paid · ${method}`}
                    </button>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      </aside>
    </div>
  );
}
