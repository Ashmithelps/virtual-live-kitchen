"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { unwrap } from "@/lib/api/errors";
import { ownerKeys } from "@/lib/owner/queries";
import { RestaurantForm } from "@/components/dashboard/restaurant-form";
import { useToast } from "@/components/toast";

export default function NewRestaurantPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const toast = useToast();
  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <h1 className="text-2xl font-bold">Add a restaurant</h1>
      <p className="text-sm text-stone-600">New restaurants wait for an admin&apos;s approval before customers can see them.</p>
      <RestaurantForm
        submitLabel="Create restaurant"
        onSubmit={async (body) => {
          const created = await unwrap(api.POST("/api/owner/restaurants", { body }));
          await queryClient.invalidateQueries({ queryKey: ownerKeys.restaurants });
          toast.success("Restaurant created. Next: add your menu.");
          router.push(`/dashboard/${created.id}/menu`);
        }}
      />
    </div>
  );
}
