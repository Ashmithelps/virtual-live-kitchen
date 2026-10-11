import { api } from "@/lib/api/client";
import { unwrap } from "@/lib/api/errors";
import type { Order } from "@/lib/api/types";
import type { Checkout, CheckoutSuccess } from "@/lib/razorpay";

/**
 * An order is reached either as the logged-in customer or, after guest checkout, with the
 * order-scoped guest token saved on this device.
 */
export type OrderAccess = { kind: "customer" } | { kind: "guest"; token: string };

const guestHeader = (token: string) => ({ "X-Guest-Order-Token": token });

export function fetchOrder(id: string, access: OrderAccess): Promise<Order> {
  return access.kind === "guest"
    ? unwrap(api.GET("/api/guest/orders/{orderId}", { params: { path: { orderId: id }, header: guestHeader(access.token) } }))
    : unwrap(api.GET("/api/orders/{orderId}", { params: { path: { orderId: id } } }));
}

/** Opens (or reuses) a gateway checkout for this order; the amount is decided by the server. */
export function startPayment(id: string, access: OrderAccess): Promise<Checkout> {
  return access.kind === "guest"
    ? unwrap(api.POST("/api/guest/orders/{orderId}/payment", { params: { path: { orderId: id }, header: guestHeader(access.token) } }))
    : unwrap(api.POST("/api/orders/{orderId}/payment", { params: { path: { orderId: id } } }));
}

/** Hands the gateway's result to the backend, which verifies it before marking the order paid. */
export function confirmPayment(id: string, access: OrderAccess, result: CheckoutSuccess): Promise<Order> {
  return access.kind === "guest"
    ? unwrap(api.POST("/api/guest/orders/{orderId}/payment/confirm", { params: { path: { orderId: id }, header: guestHeader(access.token) }, body: result }))
    : unwrap(api.POST("/api/orders/{orderId}/payment/confirm", { params: { path: { orderId: id } }, body: result }));
}

export function cancelOrder(id: string, access: OrderAccess, reason: string | undefined): Promise<Order> {
  const body = reason ? { reason } : {};
  return access.kind === "guest"
    ? unwrap(api.POST("/api/guest/orders/{orderId}/cancel", { params: { path: { orderId: id }, header: guestHeader(access.token) }, body }))
    : unwrap(api.POST("/api/orders/{orderId}/cancel", { params: { path: { orderId: id } }, body }));
}
