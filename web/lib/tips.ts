import { api } from "@/lib/api/client";
import { unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import type { OrderAccess } from "@/lib/orders";
import type { CheckoutSuccess } from "@/lib/razorpay";

export type OrderTips = components["schemas"]["OrderTipsResponse"];
export type TipCheckout = components["schemas"]["TipCheckoutResponse"];
export type Tip = components["schemas"]["TipResponse"];
export type TipSource = Tip["source"];

/** Shown before the order exists (checkout); the server's limits come with the order's tip options. */
export const DEFAULT_TIP_PRESETS_PAISE = [2000, 5000, 10000];

const guestHeader = (token: string) => ({ "X-Guest-Order-Token": token });

export function fetchOrderTips(orderId: string, access: OrderAccess): Promise<OrderTips> {
  return access.kind === "guest"
    ? unwrap(api.GET("/api/guest/orders/{orderId}/tips", { params: { path: { orderId }, header: guestHeader(access.token) } }))
    : unwrap(api.GET("/api/orders/{orderId}/tips", { params: { path: { orderId } } }));
}

export function startTip(orderId: string, access: OrderAccess, body: { chefId: string; amountPaise: number; source: TipSource }): Promise<TipCheckout> {
  return access.kind === "guest"
    ? unwrap(api.POST("/api/guest/orders/{orderId}/tips", { params: { path: { orderId }, header: guestHeader(access.token) }, body }))
    : unwrap(api.POST("/api/orders/{orderId}/tips", { params: { path: { orderId } }, body }));
}

export function confirmTip(orderId: string, access: OrderAccess, tipId: string, result: CheckoutSuccess): Promise<Tip> {
  return access.kind === "guest"
    ? unwrap(api.POST("/api/guest/orders/{orderId}/tips/{tipId}/confirm", { params: { path: { orderId, tipId }, header: guestHeader(access.token) }, body: result }))
    : unwrap(api.POST("/api/orders/{orderId}/tips/{tipId}/confirm", { params: { path: { orderId, tipId } }, body: result }));
}
