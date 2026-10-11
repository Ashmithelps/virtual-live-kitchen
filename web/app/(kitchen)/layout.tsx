import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { KitchenShell } from "@/components/kds/kitchen-shell";

export const metadata: Metadata = {
  title: "Kitchen display · Virtual Live Kitchen",
  manifest: "/kitchen.webmanifest",
  appleWebApp: { capable: true, title: "Kitchen", statusBarStyle: "black-translucent" },
};

export const viewport: Viewport = {
  themeColor: "#1c1917",
  // Tablets on a wall: no accidental pinch-zoom.
  maximumScale: 1,
  userScalable: false,
};

export default function KitchenLayout({ children }: { children: ReactNode }) {
  return <KitchenShell>{children}</KitchenShell>;
}
