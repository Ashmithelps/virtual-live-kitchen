"use client";

import type { ReactNode } from "react";
import { RequireRole } from "@/components/layout/guards";

export default function ChefLayout({ children }: { children: ReactNode }) {
  return <RequireRole roles={["CHEF"]}>{children}</RequireRole>;
}
