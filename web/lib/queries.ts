import { api } from "@/lib/api/client";
import { unwrap } from "@/lib/api/errors";

export const restaurantQuery = (slug: string) => ({
  queryKey: ["restaurant", slug],
  queryFn: () => unwrap(api.GET("/api/restaurants/{slug}", { params: { path: { slug } } })),
});
