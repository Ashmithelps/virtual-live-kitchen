"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-context";
import { ROLE_LABEL, homeFor, type Role } from "@/lib/auth/roles";
import { PageLoading } from "@/components/ui";

/**
 * Renders children only for signed-in users with one of `roles`. Visitors go to /login (and come back
 * afterwards), owners still awaiting approval go to /pending, and other roles see the 403 page.
 */
export function RequireRole({ roles, children, allowPending = false }: { roles: Role[]; children: ReactNode; allowPending?: boolean }) {
  const auth = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const pending = auth.user?.role === "RESTAURANT_OWNER" && auth.user.status === "PENDING_APPROVAL" && !allowPending;

  useEffect(() => {
    if (auth.status === "anonymous") router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    else if (pending) router.replace("/pending");
  }, [auth.status, pending, pathname, router]);

  if (auth.status !== "authenticated" || pending) return <PageLoading />;
  if (!roles.includes(auth.user.role)) return <Forbidden needs={roles} />;
  return <>{children}</>;
}

export function Forbidden({ needs }: { needs?: Role[] }) {
  const auth = useAuth();
  return (
    <div className="mx-auto max-w-md space-y-3 py-16 text-center">
      <p className="text-5xl font-bold text-stone-300">403</p>
      <h1 className="text-xl font-semibold">You don&apos;t have access to this page</h1>
      <p className="text-stone-600">
        {needs ? `It's for ${needs.map((r) => ROLE_LABEL[r].toLowerCase()).join(" / ")} accounts` : "Your account can't open it"}
        {auth.user ? `, and you're signed in as a ${ROLE_LABEL[auth.user.role].toLowerCase()}.` : "."}
      </p>
      <Link href={auth.user ? homeFor(auth.user) : "/"} className="inline-block font-medium text-brand-700 underline">
        Go to your home page
      </Link>
    </div>
  );
}
