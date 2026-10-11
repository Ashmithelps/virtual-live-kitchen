import { describe, expect, it } from "vitest";
import type { Order } from "@/lib/api/types";
import { actionsFor, columnOf, fulfilmentLabel, groupByColumn, minutesSince, newArrivals, upsertOrder, urgency, waitingHint } from "./board";

function order(id: string, status: Order["status"], placedAt = "2026-10-10T06:00:00Z", orderType: Order["orderType"] = "TAKEAWAY"): Order {
  return { id, status, orderType, timeline: { placedAt }, tableLabel: null } as unknown as Order;
}

describe("kitchen board", () => {
  it("puts statuses in the right columns and hides finished orders", () => {
    expect(columnOf(order("a", "PLACED"))).toBe("new");
    expect(columnOf(order("a", "ACCEPTED"))).toBe("new");
    expect(columnOf(order("a", "PREPARING"))).toBe("preparing");
    expect(columnOf(order("a", "READY"))).toBe("ready");
    expect(columnOf(order("a", "OUT_FOR_DELIVERY"))).toBe("ready");
    expect(columnOf(order("a", "PICKED_UP"))).toBeNull();
  });

  it("sorts each column oldest first", () => {
    const grouped = groupByColumn([order("late", "PLACED", "2026-10-10T06:10:00Z"), order("early", "ACCEPTED", "2026-10-10T06:00:00Z")]);
    expect(grouped.new.map((o) => o.id)).toEqual(["early", "late"]);
  });

  it("colours by age: amber from 15 min, red from 25 min", () => {
    const placed = "2026-10-10T06:00:00Z";
    const at = (min: number) => Date.parse(placed) + min * 60_000;
    expect(urgency(minutesSince(placed, at(14)))).toBe("normal");
    expect(urgency(minutesSince(placed, at(15)))).toBe("amber");
    expect(urgency(minutesSince(placed, at(25)))).toBe("red");
    expect(minutesSince(placed, at(-5))).toBe(0);
  });

  it("upserts updates and drops finished orders", () => {
    let list = upsertOrder(undefined, order("a", "PLACED"));
    list = upsertOrder(list, order("a", "ACCEPTED"));
    expect(list).toHaveLength(1);
    expect(list[0]!.status).toBe("ACCEPTED");
    expect(upsertOrder(list, order("a", "CANCELLED"))).toHaveLength(0);
  });

  it("detects new orders only once and only in the New column", () => {
    const seen = new Set(["old"]);
    expect(newArrivals(seen, [order("old", "PLACED"), order("fresh", "PLACED"), order("cooking", "PREPARING")])).toEqual(["fresh"]);
  });

  it("gives chefs only Start and Ready; owners accept, reject and close out", () => {
    const labels = (status: Order["status"], role: "CHEF" | "OWNER", type: Order["orderType"] = "TAKEAWAY") =>
      actionsFor(order("a", status, undefined, type), role).map((a) => a.status);
    expect(labels("PLACED", "CHEF")).toEqual([]);
    expect(labels("ACCEPTED", "CHEF")).toEqual(["PREPARING"]);
    expect(labels("PREPARING", "CHEF")).toEqual(["READY"]);
    expect(labels("READY", "CHEF")).toEqual([]);
    expect(labels("PLACED", "OWNER")).toEqual(["ACCEPTED", "REJECTED"]);
    expect(labels("ACCEPTED", "OWNER")).toEqual([]);
    expect(labels("READY", "OWNER", "DINE_IN")).toEqual(["SERVED"]);
    expect(labels("READY", "OWNER", "DELIVERY")).toEqual(["OUT_FOR_DELIVERY"]);
    expect(labels("OUT_FOR_DELIVERY", "OWNER", "DELIVERY")).toEqual(["DELIVERED"]);
    expect(waitingHint(order("a", "PLACED"), "CHEF")).toBe("Waiting for the owner to accept");
  });

  it("labels where the food goes", () => {
    expect(fulfilmentLabel({ orderType: "DINE_IN", tableLabel: "T4" })).toBe("Table T4");
    expect(fulfilmentLabel({ orderType: "TAKEAWAY", tableLabel: null })).toBe("Takeaway");
    expect(fulfilmentLabel({ orderType: "DELIVERY", tableLabel: null })).toBe("Delivery");
  });
});
