import type { ReactNode } from "react";
import { AppHeader } from "@/components/layout/app-header";

/** Staff areas (owner dashboard, admin, chef settings): same header as the customer site, wider page. */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <AppHeader wide />
      <main className="mx-auto w-full max-w-6xl px-4 pb-24 pt-4">{children}</main>
    </>
  );
}
