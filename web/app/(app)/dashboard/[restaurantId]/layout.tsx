"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ApiError, errorMessage } from "@/lib/api/errors";
import { restaurantQuery } from "@/lib/owner/queries";
import { RESTAURANT_STATUS } from "@/lib/owner/status";
import { Badge, ErrorState, PageLoading } from "@/components/ui";

const TABS = [
  { href: "", label: "Live orders" },
  { href: "/menu", label: "Menu" },
  { href: "/tables", label: "Tables" },
  { href: "/stations", label: "Stations" },
  { href: "/chefs", label: "Chefs" },
  { href: "/clips", label: "Clips" },
  { href: "/hygiene", label: "Hygiene" },
  { href: "/billing", label: "Billing" },
  { href: "/profile", label: "Profile" },
];

export default function RestaurantDashboardLayout({ children }: { children: ReactNode }) {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const pathname = usePathname();
  const restaurant = useQuery(restaurantQuery(restaurantId));
  const base = `/dashboard/${restaurantId}`;

  if (restaurant.isPending) return <PageLoading />;
  if (restaurant.isError) {
    const missing = restaurant.error instanceof ApiError && restaurant.error.status === 404;
    return <ErrorState title={missing ? "Restaurant not found" : undefined} message={missing ? "It doesn't exist or isn't yours." : errorMessage(restaurant.error)} onRetry={missing ? undefined : () => restaurant.refetch()} />;
  }
  const status = RESTAURANT_STATUS[restaurant.data.status];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Link href="/dashboard" className="text-sm text-stone-500 hover:text-stone-800">
          ← All restaurants
        </Link>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold">{restaurant.data.name}</h1>
        <Badge tone={status.tone}>{status.label}</Badge>
        {restaurant.data.status === "ACTIVE" && (
          <Link href={`/r/${restaurant.data.slug}`} className="text-sm text-brand-700 underline" target="_blank">
            View public page
          </Link>
        )}
      </div>
      <nav className="-mx-4 flex gap-1 overflow-x-auto border-b border-stone-200 px-4" aria-label="Restaurant sections">
        {TABS.map((tab) => {
          const href = base + tab.href;
          const active = tab.href === "" ? pathname === base : pathname.startsWith(href);
          return (
            <Link
              key={tab.href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={`whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium ${active ? "border-brand-600 text-brand-700" : "border-transparent text-stone-600 hover:text-stone-900"}`}
            >
              {tab.label}
            </Link>
          );
        })}
      </nav>
      <div>{children}</div>
    </div>
  );
}
