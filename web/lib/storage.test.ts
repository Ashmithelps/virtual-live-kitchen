import { beforeEach, describe, expect, it } from "vitest";
import { readJson, writeJson } from "./storage";
import { activeGuestOrders } from "./guest-orders";
import { isSessionActive, type TableSession } from "./table-session";

describe("storage", () => {
  beforeEach(() => window.localStorage.clear());

  it("round-trips JSON and returns a stable reference until it changes", () => {
    writeJson("k", { a: 1 });
    const first = readJson<{ a: number }>("k");
    expect(first).toEqual({ a: 1 });
    expect(readJson("k")).toBe(first);
    writeJson("k", { a: 2 });
    expect(readJson("k")).not.toBe(first);
  });

  it("treats corrupted entries as missing", () => {
    window.localStorage.setItem("bad", "{not json");
    expect(readJson("bad")).toBeNull();
  });

  it("drops expired table sessions and guest tokens", () => {
    const now = Date.parse("2026-10-10T12:00:00Z");
    const session = { token: "t", expiresAt: "2026-10-10T11:59:00Z" } as TableSession;
    expect(isSessionActive(session, now)).toBe(false);
    expect(isSessionActive({ ...session, expiresAt: "2026-10-10T13:00:00Z" }, now)).toBe(true);
    expect(
      activeGuestOrders(
        [
          { orderId: "a", token: "t", expiresAt: "2026-10-10T11:00:00Z", displayNumber: 1, restaurantName: "x", placedAt: "" },
          { orderId: "b", token: "t", expiresAt: "2026-10-10T13:00:00Z", displayNumber: 2, restaurantName: "x", placedAt: "" },
        ],
        now,
      ).map((g) => g.orderId),
    ).toEqual(["b"]);
  });
});
