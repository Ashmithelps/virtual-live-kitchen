"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { OrderType } from "@/lib/api/types";
import { ORDER_TYPE_LABEL } from "@/lib/format";
import { useStoredJson, writeJson } from "@/lib/storage";
import { RestaurantCard } from "@/components/restaurant-card";
import { Button, EmptyState, ErrorState, PageLoading, inputClass } from "@/components/ui";

const POPULAR_CITIES = ["Bengaluru", "Mumbai", "Delhi", "Hyderabad", "Chennai", "Pune"];
const CITY_KEY = "vk.city";
const PAGE_SIZE = 20;

type Filters = { cuisine: string; vegOnly: boolean; openNow: boolean; orderType: OrderType | "" };
const NO_FILTERS: Filters = { cuisine: "", vegOnly: false, openNow: false, orderType: "" };

export default function HomePage() {
  const city = useStoredJson<string>(CITY_KEY);
  const [cityInput, setCityInput] = useState("");
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [cuisineInput, setCuisineInput] = useState("");

  const chooseCity = (value: string) => {
    const trimmed = value.trim();
    if (trimmed) writeJson(CITY_KEY, trimmed);
  };

  const results = useInfiniteQuery({
    queryKey: ["restaurants", city, filters],
    enabled: !!city,
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      unwrap(
        api.GET("/api/restaurants", {
          params: {
            query: {
              city: city!,
              cuisine: filters.cuisine || undefined,
              vegOnly: filters.vegOnly,
              openNow: filters.openNow,
              orderType: filters.orderType || undefined,
              page: pageParam,
              size: PAGE_SIZE,
            },
          },
        }),
      ),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });

  if (!city) {
    return (
      <div className="space-y-6 pt-6">
        <div>
          <h1 className="text-2xl font-bold">Where are you eating today?</h1>
          <p className="mt-1 text-stone-600">Pick your city to see restaurants near you.</p>
        </div>
        <CityForm value={cityInput} onChange={setCityInput} onSubmit={chooseCity} />
        <div className="flex flex-wrap gap-2">
          {POPULAR_CITIES.map((c) => (
            <button key={c} onClick={() => chooseCity(c)} className="rounded-full border border-stone-300 bg-white px-4 py-2 text-sm hover:border-brand-500">
              {c}
            </button>
          ))}
        </div>
      </div>
    );
  }

  const restaurants = results.data?.pages.flatMap((p) => p.items) ?? [];
  const total = results.data?.pages[0]?.totalItems ?? 0;
  const filtersActive = JSON.stringify(filters) !== JSON.stringify(NO_FILTERS);

  return (
    <div className="space-y-4">
      <div className="flex items-baseline justify-between gap-3">
        <h1 className="text-xl font-bold">Restaurants in {city}</h1>
        <button onClick={() => writeJson(CITY_KEY, null)} className="text-sm font-medium text-brand-700">
          Change city
        </button>
      </div>

      <form
        className="flex gap-2"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          setFilters((f) => ({ ...f, cuisine: cuisineInput.trim() }));
        }}
      >
        <input className={inputClass} placeholder="Cuisine, e.g. South Indian" value={cuisineInput} onChange={(e) => setCuisineInput(e.target.value)} />
        <Button type="submit" variant="secondary">
          Search
        </Button>
      </form>

      <div className="flex flex-wrap gap-2">
        <Toggle on={filters.vegOnly} onClick={() => setFilters((f) => ({ ...f, vegOnly: !f.vegOnly }))}>
          Pure veg
        </Toggle>
        <Toggle on={filters.openNow} onClick={() => setFilters((f) => ({ ...f, openNow: !f.openNow }))}>
          Open now
        </Toggle>
        {(Object.keys(ORDER_TYPE_LABEL) as OrderType[]).map((type) => (
          <Toggle key={type} on={filters.orderType === type} onClick={() => setFilters((f) => ({ ...f, orderType: f.orderType === type ? "" : type }))}>
            {ORDER_TYPE_LABEL[type]}
          </Toggle>
        ))}
        {filtersActive && (
          <button
            className="px-2 text-sm text-stone-500 underline"
            onClick={() => {
              setFilters(NO_FILTERS);
              setCuisineInput("");
            }}
          >
            Clear
          </button>
        )}
      </div>

      {results.isPending ? (
        <PageLoading label="Finding restaurants…" />
      ) : results.isError ? (
        <ErrorState message={errorMessage(results.error)} onRetry={() => results.refetch()} />
      ) : restaurants.length === 0 ? (
        <EmptyState
          title={filtersActive ? "No restaurants match these filters" : `No restaurants in ${city} yet`}
          message={filtersActive ? "Try removing a filter." : "Check the spelling or try another city."}
        />
      ) : (
        <>
          <p className="text-sm text-stone-500">
            {total} restaurant{total === 1 ? "" : "s"}
          </p>
          <ul className="space-y-3">
            {restaurants.map((r) => (
              <li key={r.slug}>
                <RestaurantCard restaurant={r} />
              </li>
            ))}
          </ul>
          {results.hasNextPage && (
            <Button variant="secondary" className="w-full" busy={results.isFetchingNextPage} onClick={() => results.fetchNextPage()}>
              Load more
            </Button>
          )}
        </>
      )}
    </div>
  );
}

function CityForm({ value, onChange, onSubmit }: { value: string; onChange: (v: string) => void; onSubmit: (v: string) => void }) {
  return (
    <form
      className="flex gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(value);
      }}
    >
      <input className={inputClass} placeholder="City" aria-label="City" value={value} onChange={(e) => onChange(e.target.value)} autoComplete="address-level2" />
      <Button type="submit" disabled={!value.trim()}>
        Go
      </Button>
    </form>
  );
}

function Toggle({ on, onClick, children }: { on: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`min-h-9 rounded-full border px-3 text-sm ${on ? "border-brand-600 bg-brand-600 text-white" : "border-stone-300 bg-white text-stone-700"}`}
    >
      {children}
    </button>
  );
}
