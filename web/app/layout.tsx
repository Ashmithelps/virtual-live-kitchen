import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { Providers } from "./providers";

export const metadata: Metadata = {
  // Absolute base for link previews (og:image etc.); the public address of this web app.
  metadataBase: new URL(process.env.SITE_URL ?? "http://localhost:3000"),
  title: "Virtual Live Kitchen",
  description: "Order from restaurants and watch your food being made.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#ea580c",
};

/** Shell only; each route group ((customer), (kitchen)) brings its own session and chrome. */
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en-IN">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
