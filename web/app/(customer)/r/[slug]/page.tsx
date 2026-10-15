"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { useMemo, useState } from "react";
import { errorMessage, ApiError } from "@/lib/api/errors";
import { restaurantQuery } from "@/lib/queries";
import { useCart } from "@/lib/cart/use-cart";
import { itemCount, quantityOf, subtotalPaise } from "@/lib/cart/cart";
import { clearTableSession, useTableSession } from "@/lib/table-session";
import { ORDER_TYPE_LABEL } from "@/lib/format";
import { CartBar } from "@/components/cart-bar";
import { MenuItemRow } from "@/components/menu-item-row";
import { Badge, EmptyState, ErrorState, PageLoading, TextLink } from "@/components/ui";

export default function RestaurantPage() {
  const { slug } = useParams<{ slug: string }>();
  const restaurant = useQuery(restaurantQuery(slug));
  const { cart, add, increment, decrement } = useCart(slug);
  const table = useTableSession(slug);
  const [vegOnly, setVegOnly] = useState(false);

  const menu = useMemo(() => {
    const categories = restaurant.data?.menu ?? [];
    if (!vegOnly) return categories;
    return categories.map((c) => ({ ...c, items: c.items.filter((i) => i.veg) })).filter((c) => c.items.length > 0);
  }, [restaurant.data, vegOnly]);

  if (restaurant.isPending) return <PageLoading label="Loading menu…" />;
  if (restaurant.isError) {
    const notFound = restaurant.error instanceof ApiError && restaurant.error.status === 404;
    return notFound ? (
      <EmptyState title="Restaurant not found" message="It may have moved or is not taking orders right now." action={<TextLink href="/">Browse restaurants</TextLink>} />
    ) : (
      <ErrorState message={errorMessage(restaurant.error)} onRetry={() => restaurant.refetch()} />
    );
  }

  const r = restaurant.data;
  const info = { restaurantId: r.id, restaurantSlug: r.slug, restaurantName: r.name };

  return (
    <div className="space-y-4">
      {table && (
        <div className="flex items-center justify-between rounded-2xl bg-brand-600 px-4 py-3 text-white">
          <div>
            <p className="text-xs uppercase tracking-wide opacity-80">Dine-in</p>
            <p className="font-semibold">Table {table.tableLabel}</p>
          </div>
          <button onClick={() => clearTableSession(slug)} className="text-sm underline opacity-90">
            Not at this table?
          </button>
        </div>
      )}

      <header>
        <h1 className="text-2xl font-bold">{r.name}</h1>
        <p className="text-sm text-stone-500">{r.address}</p>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {r.openNow ? <Badge tone="green">Open now</Badge> : <Badge tone="red">Closed now</Badge>}
          {r.hygiene && (
            <span title={`Average AI hygiene score of ${r.hygiene.clipCount} kitchen clips in the last ${r.hygiene.windowDays} days`}>
              <Badge tone={r.hygiene.score >= 85 ? "green" : r.hygiene.score >= 65 ? "amber" : "red"}>🧤 Hygiene {Math.round(r.hygiene.score)}/100</Badge>
            </span>
          )}
          {r.orderTypes.map((t) => (
            <Badge key={t}>{ORDER_TYPE_LABEL[t]}</Badge>
          ))}
        </div>
      </header>

      {!r.openNow && (
        <p className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">This restaurant is closed right now. You can browse the menu, but orders are paused.</p>
      )}

      <div className="sticky top-14 z-10 -mx-4 space-y-2 border-b border-stone-200 bg-stone-50/95 px-4 py-2 backdrop-blur">
        <label className="flex items-center gap-2 text-sm font-medium">
          <input type="checkbox" checked={vegOnly} onChange={(e) => setVegOnly(e.target.checked)} className="size-4 accent-green-700" />
          Veg only
        </label>
        {menu.length > 1 && (
          <nav className="flex gap-2 overflow-x-auto pb-1" aria-label="Menu categories">
            {menu.map((c) => (
              <a key={c.id} href={`#cat-${c.id}`} className="shrink-0 rounded-full border border-stone-300 bg-white px-3 py-1 text-sm">
                {c.name}
              </a>
            ))}
          </nav>
        )}
      </div>

      {menu.length === 0 ? (
        <EmptyState title={vegOnly ? "No vegetarian dishes" : "The menu is empty"} message={vegOnly ? "Turn off “Veg only” to see everything." : "Check back soon."} />
      ) : (
        menu.map((category) => (
          <section key={category.id} id={`cat-${category.id}`} className="scroll-mt-32">
            <h2 className="text-lg font-bold">{category.name}</h2>
            {category.description && <p className="text-sm text-stone-500">{category.description}</p>}
            <ul className="divide-y divide-stone-200">
              {category.items.map((item) => (
                <MenuItemRow
                  key={item.id}
                  item={item}
                  quantity={quantityOf(cart, item.id)}
                  canOrder={r.openNow}
                  onAdd={() => add(info, item)}
                  onIncrement={() => increment(item.id)}
                  onDecrement={() => decrement(item.id)}
                />
              ))}
            </ul>
          </section>
        ))
      )}

      <CartBar count={itemCount(cart)} subtotal={subtotalPaise(cart)} href={`/checkout?r=${encodeURIComponent(slug)}`} />
    </div>
  );
}
