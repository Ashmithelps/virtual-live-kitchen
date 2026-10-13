"use client";

import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { ownerKeys, restaurantQuery } from "@/lib/owner/queries";
import { RestaurantForm } from "@/components/dashboard/restaurant-form";
import { ErrorState, PageLoading } from "@/components/ui";
import { useToast } from "@/components/toast";

export default function RestaurantProfilePage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const restaurant = useQuery(restaurantQuery(restaurantId));

  if (restaurant.isPending) return <PageLoading />;
  if (restaurant.isError) return <ErrorState message={errorMessage(restaurant.error)} onRetry={() => restaurant.refetch()} />;

  return (
    <div className="max-w-3xl">
      <RestaurantForm
        key={restaurant.data.updatedAt}
        restaurant={restaurant.data}
        submitLabel="Save changes"
        onSubmit={async (body) => {
          const saved = await unwrap(api.PUT("/api/owner/restaurants/{restaurantId}", { params: { path: { restaurantId } }, body }));
          queryClient.setQueryData(ownerKeys.restaurant(restaurantId), saved);
          await queryClient.invalidateQueries({ queryKey: ownerKeys.restaurants });
          toast.success("Saved");
        }}
      />
    </div>
  );
}
