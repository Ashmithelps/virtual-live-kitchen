import { defineConfig } from "@playwright/test";

/**
 * End-to-end smoke tests against a running stack (they don't start anything themselves):
 *   infra (docker compose) + backend with SPRING_PROFILES_ACTIVE=local,dev-seed + `npm run dev`.
 * DEV_SEED_PASSWORD must match the backend's. Uses the installed Google Chrome unless
 * PLAYWRIGHT_CHANNEL is set (e.g. "chromium" after `npx playwright install chromium`).
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
