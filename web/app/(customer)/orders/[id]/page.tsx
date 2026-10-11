"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { customerSession } from "@/lib/auth/token-store";
import { useStomp } from "@/lib/realtime/use-stomp";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ApiError, errorMessage } from "@/lib/api/errors";
import type { Order } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/auth-context";
import { ORDER_TYPE_LABEL, formatDateTime, formatPaise } from "@/lib/format";
import { useGuestToken } from "@/lib/guest-orders";
import { STATUS_LABEL, canCustomerCancel, isTerminal } from "@/lib/order-status";
import { cancelOrder, confirmPayment, fetchOrder, startPayment, type OrderAccess } from "@/lib/orders";
import { openRazorpayCheckout, type Checkout } from "@/lib/razorpay";
import { useHydrated } from "@/lib/storage";
import { OrderTimeline } from "@/components/order-timeline";
import { Badge, Button, Card, EmptyState, ErrorState, InlineError, PageLoading, TextLink, VegMark } from "@/components/ui";

/** Fallback only: used while the WebSocket isn't connected (or was refused). */
const POLL_MS = 5_000;

export default function OrderPage() {
  const { id } = useParams<{ id: string }>();
  // Set by the checkout page: open the payment dialog straight away.
  const autoPay = useSearchParams().get("pay") === "1";
  const auth = useAuth();
  const hydrated = useHydrated();
  const guestToken = useGuestToken(id);

  const access: OrderAccess | null = guestToken
    ? { kind: "guest", token: guestToken }
    : auth.status === "authenticated"
      ? { kind: "customer" }
      : null;

  const queryClient = useQueryClient();
  const queryKey = ["order", id, access?.kind];
  const finished = (() => {
    const cached = queryClient.getQueryData<Order>(queryKey);
    return cached ? isTerminal(cached.status) : false;
  })();

  // Live updates: pushed over STOMP; reconnects refetch whatever was missed.
  const socket = useStomp({
    destination: hydrated && access && !finished ? `/topic/order/${id}` : null,
    connectHeaders: async (): Promise<Record<string, string> | null> => {
      if (access?.kind === "guest") return { "X-Guest-Order-Token": access.token };
      const token = await customerSession.freshAccessToken();
      return token ? { Authorization: `Bearer ${token}` } : null;
    },
    onMessage: (body) => {
      const event = body as OrderEvent;
      if (event?.type === "ORDER_UPDATED" && event.order?.id === id) queryClient.setQueryData(queryKey, event.order);
    },
    onConnected: () => queryClient.invalidateQueries({ queryKey }),
  });

  const order = useQuery({
    queryKey,
    queryFn: () => fetchOrder(id, access!),
    enabled: hydrated && access !== null,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (query.state.error || (data && isTerminal(data.status))) return false;
      return socket === "connected" ? false : POLL_MS;
    },
    refetchIntervalInBackground: false,
  });

  if (!hydrated || auth.status === "loading") return <PageLoading label="Loading your order…" />;
  if (!access) {
    return (
      <EmptyState
        title="Log in to see this order"
        message="Orders are private to the account that placed them."
        action={<TextLink href={`/login?next=${encodeURIComponent(`/orders/${id}`)}`}>Log in</TextLink>}
      />
    );
  }
  if (order.isPending) return <PageLoading label="Loading your order…" />;
  if (order.isError) {
    if (order.error instanceof ApiError && (order.error.status === 404 || order.error.status === 401)) {
      return <EmptyState title="Order not found" message="It may belong to a different account." action={<TextLink href="/orders">My orders</TextLink>} />;
    }
    return <ErrorState message={errorMessage(order.error)} onRetry={() => order.refetch()} />;
  }
  return <OrderDetails order={order.data} access={access} live={socket === "connected"} autoPay={autoPay} />;
}

type OrderEvent = { type: string; order: Order };

function OrderDetails({ order, access, live, autoPay }: { order: Order; access: OrderAccess; live: boolean; autoPay: boolean }) {
  const queryClient = useQueryClient();
  const update = (o: Order) => queryClient.setQueryData(["order", o.id, access.kind], o);

  const awaitingPayment = order.paymentMethod === "ONLINE" && order.status === "PLACED" && (order.paymentStatus === "PENDING" || order.paymentStatus === "FAILED");
  const openBill = order.paymentStatus === "PAY_LATER" && order.status !== "REJECTED" && order.status !== "CANCELLED";
  const stopped = order.status === "REJECTED" || order.status === "CANCELLED";

  return (
    <div className="space-y-4">
      <Card className="text-center">
        <p className="text-xs uppercase tracking-wider text-stone-500">Order number</p>
        <p className="text-5xl font-black tracking-tight text-brand-700">#{order.displayNumber}</p>
        <p className="mt-1 font-semibold">{order.restaurantName}</p>
        <p className="text-sm text-stone-500">
          {ORDER_TYPE_LABEL[order.orderType]}
          {order.tableLabel && ` · Table ${order.tableLabel}`} · {formatDateTime(order.timeline.placedAt)}
        </p>
        <div className="mt-2">
          <Badge tone={stopped ? "red" : isTerminal(order.status) ? "green" : "brand"}>{STATUS_LABEL[order.status]}</Badge>
        </div>
        {order.statusReason && <p className="mt-2 text-sm text-stone-600">{order.statusReason}</p>}
        {order.paymentStatus === "REFUND_PENDING" && <p className="mt-1 text-sm text-amber-700">Your refund is on its way (usually 5–7 working days to your account).</p>}
        {order.paymentStatus === "REFUNDED" && <p className="mt-1 text-sm text-green-700">Your payment has been refunded.</p>}
      </Card>

      {(awaitingPayment || openBill) && (
        <PaymentPanel order={order} access={access} onPaid={update} autoStart={autoPay && awaitingPayment} payLater={openBill} />
      )}

      <Card>
        <h2 className="mb-3 font-semibold">Status</h2>
        {awaitingPayment ? (
          <p className="text-sm text-stone-600">The restaurant will see your order as soon as payment goes through.</p>
        ) : (
          <OrderTimeline order={order} />
        )}
        {!isTerminal(order.status) && !awaitingPayment && (
          <p className="mt-3 flex items-center gap-1.5 text-xs text-stone-400">
            <span aria-hidden className={`size-2 rounded-full ${live ? "bg-green-500" : "bg-amber-400"}`} />
            {live ? "Live" : "Reconnecting… checking every few seconds"}
          </p>
        )}
      </Card>

      <Card className="border-dashed bg-stone-50 text-center">
        <p className="text-2xl" aria-hidden>
          🎥
        </p>
        <p className="font-semibold">Your kitchen clip will appear here</p>
        <p className="mt-1 text-sm text-stone-500">
          {order.preparation
            ? `${order.preparation.chefDisplayName ?? "Your chef"} is cooking at the ${order.preparation.stationName ?? "kitchen"} station.`
            : "Once the kitchen starts cooking, you'll get a short video of your food being made."}
        </p>
      </Card>

      <Card>
        <h2 className="mb-2 font-semibold">Items</h2>
        <ul className="divide-y divide-stone-100">
          {order.items.map((item, i) => (
            <li key={i} className="flex items-start gap-3 py-2 text-sm">
              <VegMark veg={item.veg} />
              <span className="flex-1">
                {item.quantity} × {item.name}
                {item.notes && <span className="block text-xs text-stone-500">{item.notes}</span>}
              </span>
              <span>{formatPaise(item.lineTotalPaise)}</span>
            </li>
          ))}
        </ul>
        <dl className="mt-3 space-y-1 border-t border-stone-200 pt-3 text-sm">
          <Row label="Subtotal" value={formatPaise(order.subtotalPaise)} />
          <Row label="GST" value={formatPaise(order.taxPaise)} />
          <Row label="Total" value={formatPaise(order.totalPaise)} strong />
          <Row label="Payment" value={paymentLabel(order)} />
        </dl>
        {order.deliveryAddress && <p className="mt-3 text-sm text-stone-600">Delivering to: {order.deliveryAddress}</p>}
        {order.notes && <p className="mt-2 text-sm text-stone-600">Notes: {order.notes}</p>}
      </Card>

      {canCustomerCancel(order.status) && <CancelPanel order={order} access={access} onCancelled={update} />}
    </div>
  );
}

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex justify-between ${strong ? "font-semibold" : ""}`}>
      <dt className="text-stone-600">{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function paymentLabel(order: Order): string {
  if (order.paymentStatus === "PAID" && order.payment?.provider === "counter") {
    return `Paid at counter (${order.payment.counterMethod?.toLowerCase() ?? "in person"})`;
  }
  const labels = {
    PENDING: "Awaiting payment",
    PAID: "Paid online",
    FAILED: "Payment failed",
    PAY_LATER: "Pay at the table",
    REFUND_PENDING: "Refund in progress",
    REFUNDED: "Refunded",
  } as const;
  return labels[order.paymentStatus];
}

/**
 * Pays through whichever gateway the backend uses. Razorpay opens its own checkout dialog; the
 * development mock shows a test panel. Either way the backend verifies the result before the
 * order is marked paid; the amount always comes from the server.
 */
function PaymentPanel({
  order,
  access,
  onPaid,
  autoStart,
  payLater,
}: {
  order: Order;
  access: OrderAccess;
  onPaid: (o: Order) => void;
  autoStart: boolean;
  payLater: boolean;
}) {
  const [notice, setNotice] = useState<string | null>(null);
  const [mockCheckout, setMockCheckout] = useState<Checkout | null>(null);
  const autoStarted = useRef(false);

  const pay = useMutation({
    mutationFn: async () => {
      setNotice(null);
      const checkout = await startPayment(order.id, access);
      if (checkout.provider !== "razorpay") {
        setMockCheckout(checkout);
        return null;
      }
      const outcome = await openRazorpayCheckout(checkout);
      if (outcome.kind === "dismissed") {
        setNotice(outcome.lastError ? `${outcome.lastError} You can try again.` : "Payment not completed. You can try again.");
        return null;
      }
      return confirmPayment(order.id, access, outcome.result);
    },
    onSuccess: (updated) => updated && onPaid(updated),
  });

  const mockPay = useMutation({
    mutationFn: (fail: boolean) =>
      confirmPayment(order.id, access, {
        providerOrderId: mockCheckout!.providerOrderId,
        providerPaymentId: `${fail ? "fail" : "pay"}_mock_${crypto.randomUUID().slice(0, 8)}`,
        signature: "mock",
      }),
    onSuccess: (updated) => {
      setMockCheckout(null);
      onPaid(updated);
    },
  });

  // Coming straight from checkout (?pay=1): open the payment dialog once, no extra tap.
  useEffect(() => {
    if (autoStart && !autoStarted.current) {
      autoStarted.current = true;
      pay.mutate();
    }
  }, [autoStart, pay]);

  const busy = pay.isPending || mockPay.isPending;
  return (
    <Card className={`space-y-3 ${payLater ? "" : "border-amber-300 bg-amber-50"}`}>
      <div>
        <h2 className="font-semibold">{payLater ? "Pay your bill online" : "Complete payment"}</h2>
        <p className="text-sm text-stone-600">{payLater ? "Skip the wait: pay from your phone any time." : "The restaurant gets your order once payment goes through."}</p>
      </div>
      {order.paymentStatus === "FAILED" && <InlineError message="Your last payment didn't go through. Please try again." />}
      {notice && <p className="rounded-lg bg-stone-100 px-3 py-2 text-sm text-stone-700">{notice}</p>}
      <InlineError message={pay.isError ? errorMessage(pay.error) : mockPay.isError ? errorMessage(mockPay.error) : null} />
      {mockCheckout ? (
        <div className="space-y-2 rounded-xl border border-dashed border-amber-400 p-3">
          <p className="text-sm text-amber-800">Test mode (mock gateway): no real money is charged.</p>
          <Button className="w-full" busy={mockPay.isPending && mockPay.variables === false} disabled={busy} onClick={() => mockPay.mutate(false)}>
            Approve {formatPaise(mockCheckout.amountPaise)}
          </Button>
          <Button variant="ghost" className="w-full" disabled={busy} onClick={() => mockPay.mutate(true)}>
            Simulate a failed payment
          </Button>
        </div>
      ) : (
        <Button className="w-full" variant={payLater ? "secondary" : "primary"} busy={busy} onClick={() => pay.mutate()}>
          Pay {formatPaise(order.totalPaise)}
        </Button>
      )}
    </Card>
  );
}

function CancelPanel({ order, access, onCancelled }: { order: Order; access: OrderAccess; onCancelled: (o: Order) => void }) {
  const [confirming, setConfirming] = useState(false);
  const cancel = useMutation({ mutationFn: () => cancelOrder(order.id, access, undefined), onSuccess: onCancelled });
  if (!confirming) {
    return (
      <Button variant="danger" className="w-full" onClick={() => setConfirming(true)}>
        Cancel order
      </Button>
    );
  }
  return (
    <Card className="space-y-3 border-red-200">
      <p className="text-sm">
        Cancel order #{order.displayNumber}?{order.paymentStatus === "PAID" && " Your payment will be refunded."}
      </p>
      <InlineError message={cancel.isError ? errorMessage(cancel.error) : null} />
      <div className="grid grid-cols-2 gap-2">
        <Button variant="secondary" onClick={() => setConfirming(false)} disabled={cancel.isPending}>
          Keep order
        </Button>
        <Button variant="danger" busy={cancel.isPending} onClick={() => cancel.mutate()}>
          Yes, cancel
        </Button>
      </div>
    </Card>
  );
}
