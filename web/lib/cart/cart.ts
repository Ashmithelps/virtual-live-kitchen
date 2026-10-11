import type { MenuItem } from "@/lib/api/types";

export const MAX_QUANTITY = 50;

export type CartLine = {
  menuItemId: string;
  name: string;
  unitPricePaise: number;
  veg: boolean;
  quantity: number;
};

export type CartRestaurant = { restaurantId: string; restaurantSlug: string; restaurantName: string };

export type Cart = CartRestaurant & { lines: CartLine[] };

export const cartKey = (slug: string) => `vk.cart.${slug}`;

export function addItem(cart: Cart | null, restaurant: CartRestaurant, item: Pick<MenuItem, "id" | "name" | "pricePaise" | "veg">): Cart {
  const base: Cart = cart ?? { ...restaurant, lines: [] };
  const existing = base.lines.find((l) => l.menuItemId === item.id);
  if (existing) return changeQuantity(base, item.id, +1);
  return {
    ...base,
    lines: [...base.lines, { menuItemId: item.id, name: item.name, unitPricePaise: item.pricePaise, veg: item.veg, quantity: 1 }],
  };
}

/** Adds `delta` (±1) to a line, clamped to [0, MAX_QUANTITY]; lines that reach 0 are removed. */
export function changeQuantity(cart: Cart, menuItemId: string, delta: number): Cart {
  return {
    ...cart,
    lines: cart.lines
      .map((l) => (l.menuItemId === menuItemId ? { ...l, quantity: Math.min(MAX_QUANTITY, l.quantity + delta) } : l))
      .filter((l) => l.quantity > 0),
  };
}

export function quantityOf(cart: Cart | null, menuItemId: string): number {
  return cart?.lines.find((l) => l.menuItemId === menuItemId)?.quantity ?? 0;
}

export function itemCount(cart: Cart | null): number {
  return cart?.lines.reduce((n, l) => n + l.quantity, 0) ?? 0;
}

/** Pre-tax total using the prices seen when items were added; the server re-prices on order. */
export function subtotalPaise(cart: Cart | null): number {
  return cart?.lines.reduce((sum, l) => sum + l.unitPricePaise * l.quantity, 0) ?? 0;
}

/** Defends against corrupted or old-format storage. */
export function isCart(value: unknown): value is Cart {
  if (!value || typeof value !== "object") return false;
  const c = value as Partial<Cart>;
  return (
    typeof c.restaurantId === "string" &&
    typeof c.restaurantSlug === "string" &&
    Array.isArray(c.lines) &&
    c.lines.every((l) => typeof l?.menuItemId === "string" && Number.isInteger(l.quantity) && l.quantity > 0)
  );
}
