import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { StationShell } from "@/components/station/station-shell";

export const metadata: Metadata = {
  title: "Station recorder · Virtual Live Kitchen",
  manifest: "/station.webmanifest",
  appleWebApp: { capable: true, title: "Station", statusBarStyle: "black-translucent" },
};

export const viewport: Viewport = {
  themeColor: "#000000",
  maximumScale: 1,
  userScalable: false,
};

export default function StationLayout({ children }: { children: ReactNode }) {
  return <StationShell>{children}</StationShell>;
}
