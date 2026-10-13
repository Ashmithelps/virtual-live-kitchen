"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/auth-context";
import { homeFor } from "@/lib/auth/roles";
import { session } from "@/lib/auth/token-store";
import { RequireRole } from "@/components/layout/guards";
import { Button, Card } from "@/components/ui";
import { useToast } from "@/components/toast";

/** Owners land here until an admin approves their account. */
export default function PendingApprovalPage() {
  return (
    <RequireRole roles={["RESTAURANT_OWNER"]} allowPending>
      <Pending />
    </RequireRole>
  );
}

function Pending() {
  const auth = useAuth();
  const router = useRouter();
  const toast = useToast();
  const [checking, setChecking] = useState(false);

  useEffect(() => {
    if (auth.user && auth.user.status !== "PENDING_APPROVAL") router.replace(homeFor(auth.user));
  }, [auth.user, router]);

  const check = async () => {
    setChecking(true);
    // A refresh re-reads the account, so an approval shows up immediately (the effect then redirects).
    const fresh = await session.refreshSession().catch(() => null);
    setChecking(false);
    if (fresh?.user.status === "PENDING_APPROVAL") toast.info("Still waiting for approval.");
  };

  return (
    <Card className="mx-auto mt-6 max-w-md space-y-3 text-center">
      <p className="text-4xl" aria-hidden>
        ⏳
      </p>
      <h1 className="text-xl font-semibold">Pending approval</h1>
      <p className="text-stone-600">
        Thanks, {auth.user?.name}. An admin reviews every new restaurant owner. Once you&apos;re approved you can set up your restaurant, menu and kitchen.
      </p>
      <Button onClick={check} busy={checking} className="w-full">
        Check again
      </Button>
    </Card>
  );
}
