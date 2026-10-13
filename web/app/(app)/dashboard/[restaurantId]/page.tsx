"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { Order } from "@/lib/api/types";
import { session } from "@/lib/auth/token-store";
import { formatPaise, formatTime } from "@/lib/format";
import { REJECT_REASONS, actionsFor, fulfilmentLabel, upsertOrder, waitingHint, type Action } from "@/lib/kds/board";
import { STATUS_LABEL } from "@/lib/order-status";
import { ownerKeys } from "@/lib/owner/queries";
import { useStomp } from "@/lib/realtime/use-stomp";
import { unpaidBillsKey, useUnpaidBills } from "@/components/kds/bills-drawer";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading } from "@/components/ui";
import { useToast } from "@/components/toast";

const OFFLINE_POLL_MS = 5_000;
const PAYMENT_LABEL: Record<Order["paymentStatus"], { label: string; tone: "green" | "amber" | "red" | "stone" }> = {
  PAID: { label: "Paid", tone: "green" },
  PENDING: { label: "Awaiting payment", tone: "amber" },
  FAILED: { label: "Payment failed", tone: "red" },
  PAY_LATER: { label: "Pay at counter", tone: "amber" },
  REFUND_PENDING: { label: "Refund pending", tone: "amber" },
  REFUNDED: { label: "Refunded", tone: "stone" },
};

/** Owner's live orders: realtime over STOMP (polling while offline), accept/reject, close out, settle bills. */
export default function LiveOrdersPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const queryKey = ownerKeys.orders(restaurantId);

  const socket = useStomp({
    destination: `/topic/restaurant/${restaurantId}/orders`,
    connectHeaders: async (): Promise<Record<string, string> | null> => {
      const token = await session.freshAccessToken();
      return token ? { Authorization: `Bearer ${token}` } : null;
    },
    onMessage: (body) => {
      const event = body as { type?: string; order?: Order };
      if (event.type === "ORDER_UPDATED" && event.order) {
        queryClient.setQueryData<Order[]>(queryKey, (list) => upsertOrder(list, event.order!));
        void queryClient.invalidateQueries({ queryKey: unpaidBillsKey(restaurantId) });
      }
    },
    onConnected: () => queryClient.invalidateQueries({ queryKey }),
  });

  const orders = useQuery({
    queryKey,
    queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/orders", { params: { path: { restaurantId } } })),
    refetchInterval: socket === "connected" ? false : OFFLINE_POLL_MS,
  });
  const unpaid = useUnpaidBills(restaurantId, true);

  const change = useMutation({
    mutationFn: ({ order, action, reason }: { order: Order; action: Action; reason?: string }) =>
      unwrap(api.PATCH("/api/owner/restaurants/{restaurantId}/orders/{orderId}/status", { params: { path: { restaurantId, orderId: order.id } }, body: { status: action.status, reason } })),
    onSuccess: (updated) => {
      queryClient.setQueryData<Order[]>(queryKey, (list) => upsertOrder(list, updated));
      toast.success(`#${updated.displayNumber}: ${STATUS_LABEL[updated.status]}`);
    },
    onError: (error, { order }) => {
      toast.error(`#${order.displayNumber}: ${errorMessage(error)}`);
      void queryClient.invalidateQueries({ queryKey });
    },
  });

  const settle = useMutation({
    mutationFn: ({ order, method }: { order: Order; method: "CASH" | "UPI" | "CARD" }) =>
      unwrap(api.POST("/api/owner/restaurants/{restaurantId}/orders/{orderId}/payment/counter", { params: { path: { restaurantId, orderId: order.id } }, body: { method } })),
    onSuccess: (updated) => {
      queryClient.setQueryData<Order[]>(queryKey, (list) => upsertOrder(list, updated));
      toast.success(`#${updated.displayNumber} marked paid at counter`);
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => queryClient.invalidateQueries({ queryKey: unpaidBillsKey(restaurantId) }),
  });

  if (orders.isPending) return <PageLoading />;
  if (orders.isError) return <ErrorState message={errorMessage(orders.error)} onRetry={() => orders.refetch()} />;

  // Unpaid bills include served orders, which have left the active list.
  const active = orders.data;
  const closedUnpaid = (unpaid.data ?? []).filter((bill) => !active.some((o) => o.id === bill.id));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <span className={socket === "connected" ? "text-green-700" : "text-amber-700"}>
          ● {socket === "connected" ? "Live" : socket === "denied" ? "Live updates refused" : "Reconnecting (refreshing every 5 s)"}
        </span>
        <Link href={`/kitchen`} className="text-brand-700 underline">
          Open the full-screen kitchen display
        </Link>
      </div>

      {active.length === 0 && closedUnpaid.length === 0 ? (
        <EmptyState title="No active orders" message="New orders appear here instantly (online orders once they're paid)." />
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {[...active, ...closedUnpaid].map((order) => (
            <OrderCard
              key={order.id}
              order={order}
              busy={(change.isPending && change.variables?.order.id === order.id) || (settle.isPending && settle.variables?.order.id === order.id)}
              onAction={(action, reason) => change.mutate({ order, action, reason })}
              onSettle={(method) => settle.mutate({ order, method })}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function OrderCard({
  order,
  busy,
  onAction,
  onSettle,
}: {
  order: Order;
  busy: boolean;
  onAction: (action: Action, reason?: string) => void;
  onSettle: (method: "CASH" | "UPI" | "CARD") => void;
}) {
  const [rejecting, setRejecting] = useState(false);
  const actions = actionsFor(order, "OWNER");
  const hint = waitingHint(order, "OWNER");
  const payment = PAYMENT_LABEL[order.paymentStatus];
  const settleable = order.paymentMethod === "PAY_LATER" && order.paymentStatus === "PAY_LATER" && order.status !== "REJECTED" && order.status !== "CANCELLED";

  return (
    <Card className="space-y-2" >
      <div className="flex items-baseline justify-between gap-2">
        <p className="text-2xl font-black">#{order.displayNumber}</p>
        <p className="font-semibold">{formatPaise(order.totalPaise)}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Badge tone="brand">{STATUS_LABEL[order.status]}</Badge>
        <Badge tone={payment.tone}>{payment.label}</Badge>
        <Badge>{fulfilmentLabel(order)}</Badge>
      </div>
      <p className="text-sm text-stone-500">
        Placed {formatTime(order.timeline.placedAt)}
        {order.preparation?.chefDisplayName ? ` · ${order.preparation.chefDisplayName} at ${order.preparation.stationName}` : ""}
      </p>
      <ul className="text-sm">
        {order.items.map((item) => (
          <li key={item.menuItemId + item.name}>
            {item.quantity} × {item.name}
          </li>
        ))}
      </ul>
      {order.notes && <p className="text-sm text-stone-600">Note: {order.notes}</p>}
      {hint && <p className="text-sm text-stone-500">{hint}</p>}

      {rejecting ? (
        <div className="space-y-2">
          <p className="text-sm font-medium">Reason for rejecting</p>
          <div className="flex flex-wrap gap-2">
            {REJECT_REASONS.map((reason) => (
              <Button key={reason} variant="danger" disabled={busy} onClick={() => onAction(actions.find((a) => a.status === "REJECTED")!, reason)}>
                {reason}
              </Button>
            ))}
            <Button variant="ghost" onClick={() => setRejecting(false)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        actions.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {actions.map((action) => (
              <Button
                key={action.status}
                variant={action.tone === "danger" ? "danger" : "primary"}
                disabled={busy}
                onClick={() => (action.needsReason ? setRejecting(true) : onAction(action))}
              >
                {action.label}
              </Button>
            ))}
          </div>
        )
      )}

      {settleable && (
        <div className="space-y-1 border-t border-stone-100 pt-2">
          <p className="text-sm font-medium">Paid at counter</p>
          <div className="flex gap-2">
            {(["CASH", "UPI", "CARD"] as const).map((method) => (
              <Button key={method} variant="secondary" disabled={busy} onClick={() => onSettle(method)}>
                {method === "CASH" ? "Cash" : method === "CARD" ? "Card" : "UPI"}
              </Button>
            ))}
          </div>
        </div>
      )}
    </Card>
  );
}
