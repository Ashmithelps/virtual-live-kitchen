"use client";

import type { ReactNode } from "react";
import { RequireRole } from "@/components/layout/guards";

export default function DashboardLayout({ children }: { children: ReactNode }) {
  return <RequireRole roles={["RESTAURANT_OWNER"]}>{children}</RequireRole>;
}
