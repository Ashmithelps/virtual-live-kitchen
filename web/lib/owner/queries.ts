import { api } from "@/lib/api/client";
import { unwrap } from "@/lib/api/errors";

/** Query keys and fetchers shared by the owner dashboard pages. */
export const ownerKeys = {
  restaurants: ["owner", "restaurants"] as const,
  restaurant: (id: string) => ["owner", "restaurant", id] as const,
  menu: (id: string) => ["owner", "menu", id] as const,
  tables: (id: string) => ["owner", "tables", id] as const,
  stations: (id: string) => ["owner", "stations", id] as const,
  chefs: (id: string) => ["owner", "chefs", id] as const,
  orders: (id: string) => ["owner", "orders", id] as const,
  clips: (id: string, status: string, page: number) => ["owner", "clips", id, status, page] as const,
};

export const restaurantsQuery = () => ({
  queryKey: ownerKeys.restaurants,
  queryFn: () => unwrap(api.GET("/api/owner/restaurants")),
});

export const restaurantQuery = (restaurantId: string) => ({
  queryKey: ownerKeys.restaurant(restaurantId),
  queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}", { params: { path: { restaurantId } } })),
});

export const stationsQuery = (restaurantId: string) => ({
  queryKey: ownerKeys.stations(restaurantId),
  queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/stations", { params: { path: { restaurantId } } })),
});

export const chefsQuery = (restaurantId: string) => ({
  queryKey: ownerKeys.chefs(restaurantId),
  queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/chefs", { params: { path: { restaurantId } } })),
});
