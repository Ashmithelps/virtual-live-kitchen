"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { useAuth } from "@/lib/auth/auth-context";
import { useToast } from "@/components/toast";
import { Button } from "@/components/ui";

/** Follow / unfollow a chef (customers). Visitors are sent to log in first. */
export function FollowButton({ chefId, chefName, tippable }: { chefId: string; chefName: string; tippable: boolean }) {
  const auth = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const toast = useToast();
  const queryClient = useQueryClient();
  const isCustomer = auth.status === "authenticated" && auth.user.role === "CUSTOMER";
  const key = ["follow", chefId];
  const status = useQuery({
    queryKey: key,
    enabled: isCustomer,
    queryFn: () => unwrap(api.GET("/api/chefs/{chefId}/follow", { params: { path: { chefId } } })),
  });

  const toggle = useMutation({
    mutationFn: async (follow: boolean) => {
      if (follow) await unwrap(api.PUT("/api/chefs/{chefId}/follow", { params: { path: { chefId } } }));
      else await unwrap(api.DELETE("/api/chefs/{chefId}/follow", { params: { path: { chefId } } }));
      return follow;
    },
    onSuccess: (follow) => {
      queryClient.setQueryData(key, { following: follow });
      toast.success(follow ? `Following ${chefName}${tippable ? ". You can tip them at checkout." : "."}` : `Unfollowed ${chefName}`);
      router.refresh(); // follower count
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (auth.status === "loading") return null;
  if (auth.status === "anonymous") {
    return (
      <Link href={`/login?next=${encodeURIComponent(pathname)}`} className="rounded-xl border border-brand-600 px-5 py-2 text-sm font-semibold text-brand-700">
        Log in to follow
      </Link>
    );
  }
  if (!isCustomer) return null;
  const following = status.data?.following ?? false;
  return (
    <Button variant={following ? "secondary" : "primary"} busy={toggle.isPending || status.isPending} onClick={() => toggle.mutate(!following)}>
      {following ? "Following ✓" : `Follow ${chefName}`}
    </Button>
  );
}
