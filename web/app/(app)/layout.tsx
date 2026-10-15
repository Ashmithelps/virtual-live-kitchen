import type { ReactNode } from "react";
import { AppHeader } from "@/components/layout/app-header";
import { SiteFooter } from "@/components/legal/site-footer";

/** Staff areas (owner dashboard, admin, chef settings): same header as the customer site, wider page. */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <AppHeader wide />
      <main className="mx-auto w-full max-w-6xl px-4 pb-12 pt-4">{children}</main>
      <SiteFooter />
    </>
  );
}
