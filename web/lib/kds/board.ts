import type { Order, OrderStatus, OrderType } from "@/lib/api/types";
import { isTerminal } from "@/lib/order-status";

export type Column = "new" | "preparing" | "ready";
export type KdsRole = "CHEF" | "OWNER";

export const COLUMNS: { id: Column; title: string }[] = [
  { id: "new", title: "New" },
  { id: "preparing", title: "Preparing" },
  { id: "ready", title: "Ready" },
];

const COLUMN_OF: Partial<Record<OrderStatus, Column>> = {
  PLACED: "new",
  ACCEPTED: "new",
  PREPARING: "preparing",
  READY: "ready",
  OUT_FOR_DELIVERY: "ready",
};

export function columnOf(order: Pick<Order, "status">): Column | null {
  return COLUMN_OF[order.status] ?? null;
}

/** Board contents per column, oldest first (the order that has waited longest is on top). */
export function groupByColumn(orders: Order[]): Record<Column, Order[]> {
  const columns: Record<Column, Order[]> = { new: [], preparing: [], ready: [] };
  for (const order of orders) {
    const column = columnOf(order);
    if (column) columns[column].push(order);
  }
  for (const list of Object.values(columns)) {
    list.sort((a, b) => Date.parse(a.timeline.placedAt) - Date.parse(b.timeline.placedAt));
  }
  return columns;
}

export const AMBER_AFTER_MIN = 15;
export const RED_AFTER_MIN = 25;

export function minutesSince(iso: string, now: number): number {
  return Math.max(0, Math.floor((now - Date.parse(iso)) / 60_000));
}

export function urgency(minutes: number): "normal" | "amber" | "red" {
  if (minutes >= RED_AFTER_MIN) return "red";
  if (minutes >= AMBER_AFTER_MIN) return "amber";
  return "normal";
}

/** Inserts or replaces an order; drops it once it's finished (it leaves the board). */
export function upsertOrder(list: Order[] | undefined, order: Order): Order[] {
  const rest = (list ?? []).filter((o) => o.id !== order.id);
  return isTerminal(order.status) ? rest : [...rest, order];
}

/** Ids on the board now that weren't seen before and are waiting in the New column. */
export function newArrivals(seen: ReadonlySet<string>, orders: Order[]): string[] {
  return orders.filter((o) => !seen.has(o.id) && columnOf(o) === "new").map((o) => o.id);
}

export type Action = {
  label: string;
  status: OrderStatus;
  tone: "primary" | "success" | "danger";
  /** REJECTED needs a reason picked first. */
  needsReason?: boolean;
};

const DONE: Record<OrderType, Action> = {
  DINE_IN: { label: "Served", status: "SERVED", tone: "success" },
  TAKEAWAY: { label: "Picked up", status: "PICKED_UP", tone: "success" },
  DELIVERY: { label: "Out for delivery", status: "OUT_FOR_DELIVERY", tone: "success" },
};

/**
 * One-tap actions for a card, mirroring the backend rules: chefs can only start (ACCEPTED →
 * PREPARING, recorded against their station) and finish cooking (PREPARING → READY); owners
 * accept or reject new orders and close them out.
 */
export function actionsFor(order: Pick<Order, "status" | "orderType">, role: KdsRole): Action[] {
  switch (order.status) {
    case "PLACED":
      return role === "OWNER"
        ? [
            { label: "Accept", status: "ACCEPTED", tone: "primary" },
            { label: "Reject", status: "REJECTED", tone: "danger", needsReason: true },
          ]
        : [];
    case "ACCEPTED":
      return role === "CHEF" ? [{ label: "Start", status: "PREPARING", tone: "primary" }] : [];
    case "PREPARING":
      return [{ label: "Ready", status: "READY", tone: "success" }];
    case "READY":
      return role === "OWNER" ? [DONE[order.orderType]] : [];
    case "OUT_FOR_DELIVERY":
      return role === "OWNER" ? [{ label: "Delivered", status: "DELIVERED", tone: "success" }] : [];
    default:
      return [];
  }
}

/** Shown where a role has no button, so staff know who acts next. */
export function waitingHint(order: Pick<Order, "status" | "orderType">, role: KdsRole): string | null {
  if (actionsFor(order, role).length > 0) return null;
  if (order.status === "PLACED") return "Waiting for the owner to accept";
  if (order.status === "ACCEPTED") return "Waiting for a chef to start";
  if (order.status === "READY") return order.orderType === "DINE_IN" ? "Ready to serve" : "Ready for pickup";
  if (order.status === "OUT_FOR_DELIVERY") return "Out for delivery";
  return null;
}

export const REJECT_REASONS = ["Item unavailable", "Kitchen too busy", "Closing soon"] as const;

export function fulfilmentLabel(order: Pick<Order, "orderType" | "tableLabel">): string {
  if (order.orderType === "DINE_IN") return order.tableLabel ? `Table ${order.tableLabel}` : "Dine-in";
  return order.orderType === "TAKEAWAY" ? "Takeaway" : "Delivery";
}
