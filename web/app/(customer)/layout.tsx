import type { ReactNode } from "react";
import { AppHeader } from "@/components/layout/app-header";
import { SiteFooter } from "@/components/legal/site-footer";

export default function CustomerLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <AppHeader />
      <main className="mx-auto w-full max-w-2xl px-4 pb-8 pt-4">{children}</main>
      {/* Bottom padding keeps the footer clear of the fixed cart bar. */}
      <div className="pb-24">
        <SiteFooter />
      </div>
    </>
  );
}
