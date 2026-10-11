"use client";

import type { ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-context";
import { customerSession } from "@/lib/auth/token-store";
import { SiteHeader } from "@/components/site-header";

export default function CustomerLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider store={customerSession}>
      <SiteHeader />
      <main className="mx-auto w-full max-w-2xl px-4 pb-32 pt-4">{children}</main>
    </AuthProvider>
  );
}
