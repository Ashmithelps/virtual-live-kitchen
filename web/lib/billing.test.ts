import { describe, expect, it } from "vitest";
import { daysUntil, formatDate, planFeatureLines, type Plan } from "./billing";

describe("billing helpers", () => {
  it("counts whole days left, rounding up and never negative", () => {
    const now = Date.parse("2026-10-15T10:00:00Z");
    expect(daysUntil("2026-10-15T10:00:00Z", now)).toBe(0);
    expect(daysUntil("2026-10-15T11:00:00Z", now)).toBe(1);
    expect(daysUntil("2026-11-14T10:00:00Z", now)).toBe(30);
    expect(daysUntil("2026-10-01T00:00:00Z", now)).toBe(0);
  });

  it("formats dates in IST", () => {
    // 20:00 UTC is already the next day in India.
    expect(formatDate("2026-10-15T20:00:00Z")).toBe("16 Oct 2026");
  });

  it("describes plan features", () => {
    const plan = { features: { maxStations: 1, clipRetentionDays: 30, analytics: true } } as Plan;
    expect(planFeatureLines(plan)).toEqual(["1 recording station", "Clips kept 30 days", "Analytics"]);
  });
});
