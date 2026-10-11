import type { Order, OrderStatus, OrderType } from "@/lib/api/types";

export const TERMINAL: ReadonlySet<OrderStatus> = new Set(["REJECTED", "SERVED", "PICKED_UP", "DELIVERED", "CANCELLED"]);

export function isTerminal(status: OrderStatus): boolean {
  return TERMINAL.has(status);
}

/** Customers may cancel until the kitchen starts cooking (mirrors the backend state machine). */
export function canCustomerCancel(status: OrderStatus): boolean {
  return status === "PLACED" || status === "ACCEPTED";
}

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PLACED: "Order placed",
  ACCEPTED: "Accepted by restaurant",
  REJECTED: "Rejected by restaurant",
  PREPARING: "Being prepared",
  READY: "Ready",
  SERVED: "Served",
  PICKED_UP: "Picked up",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

type TimelineKey = keyof Order["timeline"];

const TIMESTAMP: Record<OrderStatus, TimelineKey> = {
  PLACED: "placedAt",
  ACCEPTED: "acceptedAt",
  REJECTED: "rejectedAt",
  PREPARING: "preparingAt",
  READY: "readyAt",
  SERVED: "servedAt",
  PICKED_UP: "pickedUpAt",
  OUT_FOR_DELIVERY: "outForDeliveryAt",
  DELIVERED: "deliveredAt",
  CANCELLED: "cancelledAt",
};

/** The happy path for an order type, in order. */
export function stepsFor(type: OrderType): OrderStatus[] {
  const tail: Record<OrderType, OrderStatus[]> = {
    DINE_IN: ["SERVED"],
    TAKEAWAY: ["PICKED_UP"],
    DELIVERY: ["OUT_FOR_DELIVERY", "DELIVERED"],
  };
  return ["PLACED", "ACCEPTED", "PREPARING", "READY", ...tail[type]];
}

export type TimelineStep = { status: OrderStatus; label: string; at: string | null; state: "done" | "current" | "upcoming" | "stopped" };

/**
 * Steps to draw for an order. A rejected/cancelled order shows the steps it reached, then the
 * stopping step; otherwise the full happy path with the current step highlighted.
 */
export function timeline(order: Pick<Order, "orderType" | "status" | "timeline">): TimelineStep[] {
  const reached = (s: OrderStatus) => order.timeline[TIMESTAMP[s]] ?? null;
  if (order.status === "REJECTED" || order.status === "CANCELLED") {
    const done = stepsFor(order.orderType)
      .filter((s) => reached(s) !== null)
      .map((s): TimelineStep => ({ status: s, label: STATUS_LABEL[s], at: reached(s), state: "done" }));
    return [...done, { status: order.status, label: STATUS_LABEL[order.status], at: reached(order.status), state: "stopped" }];
  }
  return stepsFor(order.orderType).map((s): TimelineStep => ({
    status: s,
    label: STATUS_LABEL[s],
    at: reached(s),
    state: s === order.status ? (isTerminal(s) ? "done" : "current") : reached(s) !== null ? "done" : "upcoming",
  }));
}
