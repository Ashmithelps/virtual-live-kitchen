"use client";

import Link from "next/link";
import { useInfiniteQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { Order } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/auth-context";
import { ORDER_TYPE_LABEL, formatDateTime, formatPaise } from "@/lib/format";
import { useGuestOrders } from "@/lib/guest-orders";
import { STATUS_LABEL, isTerminal } from "@/lib/order-status";
import { useHydrated } from "@/lib/storage";
import { Badge, Button, EmptyState, ErrorState, PageLoading, TextLink } from "@/components/ui";

const PAGE_SIZE = 10;

export default function OrdersPage() {
  const auth = useAuth();
  const hydrated = useHydrated();
  const guestOrders = useGuestOrders();

  if (!hydrated || auth.status === "loading") return <PageLoading />;

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold">My orders</h1>
      {auth.status === "authenticated" ? (
        <AccountOrders />
      ) : (
        <EmptyState
          title="Log in to see your orders"
          message="Ordered at a table as a guest? Create an account with the same mobile number to see those orders too."
          action={<TextLink href="/login?next=/orders">Log in or sign up</TextLink>}
        />
      )}
      {auth.status !== "authenticated" && guestOrders.length > 0 && (
        <section className="space-y-2">
          <h2 className="font-semibold">On this device</h2>
          <ul className="space-y-2">
            {guestOrders.map((g) => (
              <li key={g.orderId}>
                <Link href={`/orders/${g.orderId}`} className="flex items-center justify-between rounded-2xl border border-stone-200 bg-white p-4 hover:border-brand-500">
                  <span>
                    <span className="font-semibold">#{g.displayNumber}</span> · {g.restaurantName}
                  </span>
                  <span className="text-xs text-stone-500">{formatDateTime(g.placedAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function AccountOrders() {
  const orders = useInfiniteQuery({
    queryKey: ["my-orders"],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => unwrap(api.GET("/api/orders", { params: { query: { page: pageParam, size: PAGE_SIZE } } })),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });

  if (orders.isPending) return <PageLoading />;
  if (orders.isError) return <ErrorState message={errorMessage(orders.error)} onRetry={() => orders.refetch()} />;
  const list = orders.data.pages.flatMap((p) => p.items);
  if (list.length === 0) {
    return <EmptyState title="No orders yet" message="When you place an order it will show up here." action={<TextLink href="/">Find a restaurant</TextLink>} />;
  }
  return (
    <>
      <ul className="space-y-2">
        {list.map((o) => (
          <li key={o.id}>
            <OrderRow order={o} />
          </li>
        ))}
      </ul>
      {orders.hasNextPage && (
        <Button variant="secondary" className="w-full" busy={orders.isFetchingNextPage} onClick={() => orders.fetchNextPage()}>
          Load more
        </Button>
      )}
    </>
  );
}

function OrderRow({ order }: { order: Order }) {
  const stopped = order.status === "REJECTED" || order.status === "CANCELLED";
  return (
    <Link href={`/orders/${order.id}`} className="block rounded-2xl border border-stone-200 bg-white p-4 hover:border-brand-500">
      <div className="flex items-center justify-between gap-2">
        <span className="font-semibold">
          #{order.displayNumber} · {order.restaurantName}
        </span>
        <Badge tone={stopped ? "red" : isTerminal(order.status) ? "green" : "brand"}>{STATUS_LABEL[order.status]}</Badge>
      </div>
      <p className="mt-1 text-sm text-stone-500">
        {ORDER_TYPE_LABEL[order.orderType]} · {order.items.reduce((n, i) => n + i.quantity, 0)} items · {formatPaise(order.totalPaise)} · {formatDateTime(order.timeline.placedAt)}
      </p>
    </Link>
  );
}
