import type { MenuItem } from "@/lib/api/types";
import { formatPaise } from "@/lib/format";
import { MAX_QUANTITY } from "@/lib/cart/cart";
import { VegMark } from "./ui";

export function QuantityStepper({ quantity, onIncrement, onDecrement, label }: { quantity: number; onIncrement: () => void; onDecrement: () => void; label: string }) {
  return (
    <div className="inline-flex h-9 items-center overflow-hidden rounded-lg border border-brand-600 text-brand-700">
      <button aria-label={`Remove one ${label}`} onClick={onDecrement} className="h-full w-9 text-lg font-bold hover:bg-brand-50">
        −
      </button>
      <span aria-live="polite" className="min-w-6 text-center text-sm font-semibold">
        {quantity}
      </span>
      <button aria-label={`Add one ${label}`} onClick={onIncrement} disabled={quantity >= MAX_QUANTITY} className="h-full w-9 text-lg font-bold hover:bg-brand-50 disabled:opacity-40">
        +
      </button>
    </div>
  );
}

export function MenuItemRow({
  item,
  quantity,
  canOrder,
  onAdd,
  onIncrement,
  onDecrement,
}: {
  item: MenuItem;
  quantity: number;
  canOrder: boolean;
  onAdd: () => void;
  onIncrement: () => void;
  onDecrement: () => void;
}) {
  return (
    <li className={`flex gap-3 py-4 ${item.available ? "" : "opacity-60"}`}>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <VegMark veg={item.veg} />
          <h3 className="font-medium">{item.name}</h3>
        </div>
        <p className="mt-0.5 text-sm font-semibold">{formatPaise(item.pricePaise)}</p>
        {item.description && <p className="mt-1 line-clamp-2 text-sm text-stone-500">{item.description}</p>}
        {!item.available && <p className="mt-1 text-xs font-medium text-stone-500">Currently unavailable</p>}
      </div>
      <div className="flex w-28 shrink-0 flex-col items-center gap-2">
        {item.imageUrl && <img src={item.imageUrl} alt="" loading="lazy" className="size-24 rounded-xl object-cover" />}
        {item.available && canOrder &&
          (quantity > 0 ? (
            <QuantityStepper quantity={quantity} onIncrement={onIncrement} onDecrement={onDecrement} label={item.name} />
          ) : (
            <button onClick={onAdd} className="h-9 w-24 rounded-lg border border-brand-600 bg-white text-sm font-bold text-brand-700 hover:bg-brand-50">
              ADD
            </button>
          ))}
      </div>
    </li>
  );
}
