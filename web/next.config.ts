import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  async headers() {
    return [
      {
        // Kitchen PWA service worker: always revalidate so tablets pick up a new version promptly.
        source: "/kitchen-sw.js",
        headers: [
          { key: "Content-Type", value: "application/javascript; charset=utf-8" },
          { key: "Cache-Control", value: "no-cache, no-store, must-revalidate" },
          { key: "Service-Worker-Allowed", value: "/kitchen" },
        ],
      },
    ];
  },
};

export default nextConfig;
