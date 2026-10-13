import type { ReactNode } from "react";
import { AppHeader } from "@/components/layout/app-header";

export default function CustomerLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <AppHeader />
      <main className="mx-auto w-full max-w-2xl px-4 pb-32 pt-4">{children}</main>
    </>
  );
}
