"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { kitchenApi } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { Order } from "@/lib/api/types";
import { kitchenSession } from "@/lib/auth/token-store";
import { COLUMNS, groupByColumn, newArrivals, upsertOrder, type Action, type KdsRole } from "@/lib/kds/board";
import { playChime, soundReady, unlockSound } from "@/lib/kds/chime";
import { useStomp, type SocketStatus } from "@/lib/realtime/use-stomp";
import { BillsDrawer, unpaidBillsKey, useUnpaidBills } from "./bills-drawer";
import { OrderCard } from "./order-card";

const FLASH_MS = 8_000;
/** Safety net while the socket is down; live pushes make it unnecessary otherwise. */
const OFFLINE_POLL_MS = 10_000;

type Props = {
  role: KdsRole;
  restaurantId: string;
  restaurantName: string;
  /** Chefs only: the station they're working this shift (recorded when they tap Start). */
  station: { id: string; name: string } | null;
  userName: string;
  onSwitch: () => void;
  onLogout: () => void;
};

export function Board({ role, restaurantId, restaurantName, station, userName, onSwitch, onLogout }: Props) {
  const queryClient = useQueryClient();
  const queryKey = useMemo(() => ["kds-orders", role, restaurantId], [role, restaurantId]);

  const socket = useStomp({
    destination: `/topic/restaurant/${restaurantId}/orders`,
    connectHeaders: async (): Promise<Record<string, string> | null> => {
      const token = await kitchenSession.freshAccessToken();
      return token ? { Authorization: `Bearer ${token}` } : null;
    },
    onMessage: (body) => {
      const event = body as { type?: string; order?: Order };
      if (event.type === "ORDER_UPDATED" && event.order) {
        queryClient.setQueryData<Order[]>(queryKey, (list) => upsertOrder(list, event.order!));
        if (role === "OWNER") void queryClient.invalidateQueries({ queryKey: unpaidBillsKey(restaurantId) });
      }
    },
    // Catch up on anything that changed while we were disconnected.
    onConnected: () => queryClient.invalidateQueries({ queryKey }),
  });

  const orders = useQuery({
    queryKey,
    queryFn: () =>
      role === "CHEF"
        ? unwrap(kitchenApi.GET("/api/chef/orders"))
        : unwrap(kitchenApi.GET("/api/owner/restaurants/{restaurantId}/orders", { params: { path: { restaurantId } } })),
    refetchInterval: socket === "connected" ? false : OFFLINE_POLL_MS,
  });

  // New-order chime + flash. The first load only records what's already there.
  const seen = useRef<Set<string> | null>(null);
  const [flashing, setFlashing] = useState<ReadonlySet<string>>(new Set());
  useEffect(() => {
    if (!orders.data) return;
    if (seen.current === null) {
      seen.current = new Set(orders.data.map((o) => o.id));
      return;
    }
    const fresh = newArrivals(seen.current, orders.data);
    orders.data.forEach((o) => seen.current!.add(o.id));
    if (fresh.length === 0) return;
    playChime();
    setFlashing((current) => new Set([...current, ...fresh]));
    const timer = setTimeout(() => setFlashing((current) => new Set([...current].filter((id) => !fresh.includes(id)))), FLASH_MS);
    return () => clearTimeout(timer);
  }, [orders.data]);

  const now = useNow(15_000);
  useWakeLock();

  const [toast, setToast] = useState<string | null>(null);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 6_000);
    return () => clearTimeout(timer);
  }, [toast]);

  const change = useMutation({
    mutationFn: ({ order, action, reason }: { order: Order; action: Action; reason?: string }) => {
      const body = { status: action.status, stationId: action.status === "PREPARING" ? station?.id : undefined, reason };
      return role === "CHEF"
        ? unwrap(kitchenApi.PATCH("/api/chef/orders/{orderId}/status", { params: { path: { orderId: order.id } }, body }))
        : unwrap(kitchenApi.PATCH("/api/owner/restaurants/{restaurantId}/orders/{orderId}/status", { params: { path: { restaurantId, orderId: order.id } }, body }));
    },
    onSuccess: (updated) => queryClient.setQueryData<Order[]>(queryKey, (list) => upsertOrder(list, updated)),
    onError: (error, { order }) => {
      setToast(`#${order.displayNumber}: ${errorMessage(error)}`);
      void queryClient.invalidateQueries({ queryKey });
    },
  });

  const columns = groupByColumn(orders.data ?? []);
  const [soundOn, setSoundOn] = useState(soundReady);
  const [billsOpen, setBillsOpen] = useState(false);
  const unpaid = useUnpaidBills(restaurantId, role === "OWNER");

  return (
    <div className="flex h-dvh flex-col">
      <header className="flex h-16 shrink-0 items-center gap-4 border-b border-stone-800 px-4">
        <div className="min-w-0 flex-1">
          <p className="truncate text-lg font-bold">{restaurantName}</p>
          <p className="truncate text-sm text-stone-400">
            {userName}
            {station ? ` · ${station.name}` : " · Owner"}
          </p>
        </div>
        <ConnectionPill status={socket} />
        {!soundOn && (
          <button onClick={() => setSoundOn(unlockSound() && soundReady())} className="min-h-12 rounded-xl bg-amber-500 px-4 font-bold text-stone-950">
            🔇 Tap for sound
          </button>
        )}
        {role === "OWNER" && (
          <HeaderButton onClick={() => setBillsOpen(true)}>
            Bills{unpaid.data && unpaid.data.length > 0 ? ` (${unpaid.data.length})` : ""}
          </HeaderButton>
        )}
        <Clock now={now} />
        <HeaderButton onClick={toggleFullscreen}>⛶</HeaderButton>
        <HeaderButton onClick={onSwitch}>{role === "CHEF" ? "Station" : "Restaurant"}</HeaderButton>
        <HeaderButton onClick={onLogout}>Log out</HeaderButton>
      </header>

      {(socket === "offline" || socket === "denied") && (
        <div role="alert" className="shrink-0 bg-red-700 px-4 py-2 text-center text-lg font-bold text-white">
          {socket === "denied" ? "Live updates refused. Log out and back in." : "Offline: reconnecting… Orders may be out of date."}
        </div>
      )}

      {orders.isError && !orders.data ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3">
          <p className="text-xl">Couldn&apos;t load orders: {errorMessage(orders.error)}</p>
          <button onClick={() => orders.refetch()} className="min-h-14 rounded-2xl bg-stone-800 px-8 text-lg">
            Try again
          </button>
        </div>
      ) : (
        <div className="grid min-h-0 flex-1 grid-cols-3 gap-3 overflow-x-auto p-3" style={{ minWidth: 900 }}>
          {COLUMNS.map((column) => (
            <section key={column.id} className="flex min-h-0 flex-col rounded-2xl bg-stone-900">
              <h2 className="flex items-center justify-between px-4 py-3 text-xl font-bold">
                {column.title}
                <span className="rounded-full bg-stone-800 px-3 py-0.5 text-lg tabular-nums">{columns[column.id].length}</span>
              </h2>
              <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-3 pb-3">
                {orders.isPending ? (
                  <p className="p-4 text-stone-500">Loading…</p>
                ) : columns[column.id].length === 0 ? (
                  <p className="p-4 text-center text-stone-600">No orders</p>
                ) : (
                  columns[column.id].map((order) => (
                    <OrderCard
                      key={order.id}
                      order={order}
                      role={role}
                      now={now}
                      flashing={flashing.has(order.id)}
                      busy={change.isPending && change.variables?.order.id === order.id}
                      onAction={(action, reason) => change.mutate({ order, action, reason })}
                    />
                  ))
                )}
              </div>
            </section>
          ))}
        </div>
      )}

      {billsOpen && <BillsDrawer restaurantId={restaurantId} onClose={() => setBillsOpen(false)} />}

      {toast && (
        <div role="alert" className="fixed inset-x-0 bottom-6 mx-auto w-fit max-w-[90vw] rounded-2xl bg-red-700 px-6 py-4 text-lg font-semibold text-white shadow-2xl">
          {toast}
        </div>
      )}
    </div>
  );
}

function ConnectionPill({ status }: { status: SocketStatus }) {
  const style = { connected: "bg-green-700", connecting: "bg-stone-700", offline: "bg-red-700", denied: "bg-red-700" }[status];
  const label = { connected: "Live", connecting: "Connecting…", offline: "Offline", denied: "Offline" }[status];
  return <span className={`rounded-full px-3 py-1 text-sm font-bold ${style}`}>{label}</span>;
}

function Clock({ now }: { now: number }) {
  const time = new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", hour: "numeric", minute: "2-digit" }).format(now);
  return <span className="text-xl font-semibold tabular-nums">{time}</span>;
}

function HeaderButton({ onClick, children }: { onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} className="min-h-12 min-w-12 rounded-xl bg-stone-800 px-3 text-base font-semibold active:bg-stone-700">
      {children}
    </button>
  );
}

function toggleFullscreen() {
  if (document.fullscreenElement) void document.exitFullscreen();
  else void document.documentElement.requestFullscreen?.().catch(() => undefined);
}

function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}

/** Keeps the tablet screen on while the board is open (re-acquired when the tab becomes visible). */
function useWakeLock() {
  useEffect(() => {
    if (!("wakeLock" in navigator)) return;
    let lock: WakeLockSentinel | null = null;
    const acquire = () => {
      if (document.visibilityState === "visible") {
        navigator.wakeLock.request("screen").then((l) => (lock = l)).catch(() => undefined);
      }
    };
    acquire();
    document.addEventListener("visibilitychange", acquire);
    return () => {
      document.removeEventListener("visibilitychange", acquire);
      void lock?.release();
    };
  }, []);
}
