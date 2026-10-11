import Link from "next/link";
import type { RestaurantSummary } from "@/lib/api/types";
import { ORDER_TYPE_LABEL } from "@/lib/format";
import { Badge } from "./ui";

export function RestaurantCard({ restaurant }: { restaurant: RestaurantSummary }) {
  return (
    <Link
      href={`/r/${restaurant.slug}`}
      className="block rounded-2xl border border-stone-200 bg-white p-4 transition hover:border-brand-500 focus-visible:outline-2 focus-visible:outline-brand-500"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-semibold">{restaurant.name}</h3>
        {restaurant.openNow ? <Badge tone="green">Open</Badge> : <Badge tone="stone">Closed</Badge>}
      </div>
      <p className="mt-0.5 line-clamp-1 text-sm text-stone-500">{restaurant.address}</p>
      <div className="mt-3 flex flex-wrap gap-1.5">
        {restaurant.pureVeg && <Badge tone="green">Pure veg</Badge>}
        {restaurant.cuisineTags.map((tag) => (
          <Badge key={tag}>{tag.replaceAll("-", " ")}</Badge>
        ))}
      </div>
      <p className="mt-2 text-xs text-stone-500">{restaurant.orderTypes.map((t) => ORDER_TYPE_LABEL[t]).join(" · ")}</p>
    </Link>
  );
}
