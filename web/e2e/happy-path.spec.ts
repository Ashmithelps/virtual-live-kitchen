import { expect, request, test, type Browser, type Page } from "@playwright/test";

/**
 * Smoke test of the dine-in happy path across four roles, each in its own browser context:
 * a guest orders by table QR (pay at the table) → the owner accepts → the chef starts and readies it
 * on /kitchen → the customer's page shows Ready live → the owner marks it paid at the counter.
 *
 * Needs the stack running with the backend's dev-seed profile (see playwright.config.ts).
 */
const API_URL = process.env.E2E_API_URL ?? "http://localhost:8080";
const PASSWORD = process.env.DEV_SEED_PASSWORD ?? "";
const OWNER_PHONE = "+919000000002";
const CHEF_PHONE = "+919000000003";
const RESTAURANT_SLUG = "dev-spice-kitchen";

test.beforeAll(() => {
  if (!PASSWORD) throw new Error("Set DEV_SEED_PASSWORD (same value as the backend's dev-seed profile).");
});

/** The QR token of table T1, read through the owner API (the printed QR encodes the same link). */
async function tableQrToken(): Promise<string> {
  const api = await request.newContext({ baseURL: API_URL });
  const login = await api.post("/api/auth/login", { data: { phone: OWNER_PHONE, password: PASSWORD } });
  expect(login.ok(), `owner login: ${await login.text()}`).toBeTruthy();
  const { accessToken } = await login.json();
  const headers = { Authorization: `Bearer ${accessToken}` };
  const restaurants = await (await api.get("/api/owner/restaurants", { headers })).json();
  const restaurant = restaurants.find((r: { slug: string }) => r.slug === RESTAURANT_SLUG);
  expect(restaurant, "dev-seed restaurant").toBeTruthy();
  const tables = await (await api.get(`/api/owner/restaurants/${restaurant.id}/tables`, { headers })).json();
  const table = tables.find((t: { label: string }) => t.label === "T1") ?? tables[0];
  await api.dispose();
  return table.qrToken;
}

async function newPage(browser: Browser): Promise<Page> {
  const context = await browser.newContext();
  return context.newPage();
}

async function logIn(page: Page, path: string, phone: string) {
  await page.goto(path);
  const form = page.locator("form");
  await form.getByLabel("Mobile number").fill(phone);
  await form.getByLabel("Password").fill(PASSWORD);
  await form.getByRole("button", { name: "Log in" }).click();
}

test("table QR order → kitchen → ready → paid at counter", async ({ browser }) => {
  const qrToken = await tableQrToken();

  // ── Customer: scan the table QR, order as a guest, pay at the table ──
  const customer = await newPage(browser);
  await customer.goto(`/t/${qrToken}`);
  await expect(customer).toHaveURL(new RegExp(`/r/${RESTAURANT_SLUG}`));
  await customer.locator("li", { hasText: "Paneer Tikka" }).getByRole("button", { name: "ADD" }).click();
  await customer.locator("li", { hasText: "Dal Makhani" }).getByRole("button", { name: "ADD" }).click();
  await customer.getByRole("link", { name: /View cart/ }).click();
  await expect(customer.getByText(/Dine-in at\s+Table T1/)).toBeVisible();
  await customer.getByRole("button", { name: "Pay at the table" }).click();
  await customer.getByLabel("Name").fill("E2E Guest");
  await customer.getByLabel("Mobile number").fill(`9${String(Date.now()).slice(-9)}`);
  await customer.getByRole("button", { name: /^Place order/ }).click();
  await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}/);
  const displayNumber = (await customer.getByText(/^#\d+$/).first().textContent())!.slice(1);
  await expect(customer.getByText("Order placed", { exact: true }).first()).toBeVisible();

  // ── Owner: accept it from the dashboard's live orders ──
  const owner = await newPage(browser);
  await logIn(owner, "/login", OWNER_PHONE);
  await expect(owner).toHaveURL(/\/dashboard$/);
  await owner.getByRole("link", { name: /Dev Spice Kitchen/ }).click();
  const ownerCard = owner.locator("section", { has: owner.getByText(`#${displayNumber}`, { exact: true }) });
  await ownerCard.getByRole("button", { name: "Accept" }).click();
  await expect(ownerCard.getByText("Accepted by restaurant", { exact: true })).toBeVisible();

  // ── Chef: kitchen display, pick the station, start and finish cooking ──
  const chef = await newPage(browser);
  await logIn(chef, "/kitchen", CHEF_PHONE);
  await chef.getByRole("button", { name: "Tandoor" }).click();
  const chefCard = chef.getByRole("article", { name: `Order ${displayNumber}` });
  await chefCard.getByRole("button", { name: "Start" }).click();
  await chefCard.getByRole("button", { name: "Ready" }).click();

  // ── Customer: the page updates live to Ready ──
  await expect(customer.getByText("Ready", { exact: true }).first()).toBeVisible();

  // ── Owner: settle the bill at the counter ──
  await ownerCard.getByRole("button", { name: "Cash" }).click();
  await expect(owner.getByText(`#${displayNumber} marked paid at counter`)).toBeVisible();
  await expect(ownerCard.getByText("Paid", { exact: true })).toBeVisible();
});
