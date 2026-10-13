import { describe, expect, it } from "vitest";
import { distanceKm } from "./geo";

describe("distanceKm", () => {
  it("is zero for the same point and symmetric", () => {
    expect(distanceKm(12.97, 77.59, 12.97, 77.59)).toBe(0);
    expect(distanceKm(12.97, 77.59, 13.0, 77.6)).toBeCloseTo(distanceKm(13.0, 77.6, 12.97, 77.59), 9);
  });

  it("matches known distances", () => {
    // MG Road → Koramangala, Bengaluru: about 5 km
    expect(distanceKm(12.9756, 77.605, 12.9352, 77.6245)).toBeCloseTo(4.98, 1);
    // Mumbai → Delhi: about 1150 km
    expect(distanceKm(19.076, 72.8777, 28.6139, 77.209)).toBeGreaterThan(1140);
  });
});
