"use client";

import type { ReactNode } from "react";
import { RequireRole } from "@/components/layout/guards";

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <RequireRole roles={["ADMIN"]}>{children}</RequireRole>;
}
