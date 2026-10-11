"use client";

import { useCallback } from "react";
import type { MenuItem } from "@/lib/api/types";
import { readJson, useStoredJson, writeJson } from "@/lib/storage";
import { addItem, changeQuantity, cartKey, isCart, type Cart, type CartRestaurant } from "./cart";

/** The cart for one restaurant, persisted in localStorage so it survives reloads. */
export function useCart(slug: string) {
  const stored = useStoredJson<unknown>(cartKey(slug));
  const cart = isCart(stored) && stored.lines.length > 0 ? stored : null;

  const update = useCallback(
    (fn: (current: Cart | null) => Cart | null) => {
      const raw = readJson<unknown>(cartKey(slug));
      const next = fn(isCart(raw) ? raw : null);
      writeJson(cartKey(slug), next && next.lines.length > 0 ? next : null);
    },
    [slug],
  );

  return {
    cart,
    add: (restaurant: CartRestaurant, item: Pick<MenuItem, "id" | "name" | "pricePaise" | "veg">) =>
      update((c) => addItem(c, restaurant, item)),
    increment: (menuItemId: string) => update((c) => (c ? changeQuantity(c, menuItemId, +1) : c)),
    decrement: (menuItemId: string) => update((c) => (c ? changeQuantity(c, menuItemId, -1) : c)),
    clear: () => update(() => null),
  };
}
