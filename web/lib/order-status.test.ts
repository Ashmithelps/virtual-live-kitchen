import { describe, expect, it } from "vitest";
import type { Order } from "@/lib/api/types";
import { canCustomerCancel, stepsFor, timeline } from "./order-status";

const empty: Order["timeline"] = {
  placedAt: "2026-10-10T06:00:00Z",
  paidAt: null,
  acceptedAt: null,
  rejectedAt: null,
  preparingAt: null,
  readyAt: null,
  servedAt: null,
  pickedUpAt: null,
  outForDeliveryAt: null,
  deliveredAt: null,
  cancelledAt: null,
  refundedAt: null,
};

describe("order status", () => {
  it("has a type-specific happy path", () => {
    expect(stepsFor("DINE_IN").at(-1)).toBe("SERVED");
    expect(stepsFor("TAKEAWAY").at(-1)).toBe("PICKED_UP");
    expect(stepsFor("DELIVERY").slice(-2)).toEqual(["OUT_FOR_DELIVERY", "DELIVERED"]);
  });

  it("marks done, current and upcoming steps", () => {
    const steps = timeline({ orderType: "TAKEAWAY", status: "PREPARING", timeline: { ...empty, acceptedAt: "x", preparingAt: "y" } });
    expect(steps.map((s) => s.state)).toEqual(["done", "done", "current", "upcoming", "upcoming"]);
  });

  it("ends a cancelled order at the cancellation", () => {
    const steps = timeline({ orderType: "DINE_IN", status: "CANCELLED", timeline: { ...empty, acceptedAt: "x", cancelledAt: "z" } });
    expect(steps.map((s) => [s.status, s.state])).toEqual([
      ["PLACED", "done"],
      ["ACCEPTED", "done"],
      ["CANCELLED", "stopped"],
    ]);
  });

  it("matches the backend's cancel rule", () => {
    expect(canCustomerCancel("PLACED")).toBe(true);
    expect(canCustomerCancel("ACCEPTED")).toBe(true);
    expect(canCustomerCancel("PREPARING")).toBe(false);
  });
});
