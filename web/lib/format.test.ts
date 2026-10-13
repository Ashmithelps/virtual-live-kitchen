import { describe, expect, it } from "vitest";
import { formatPaise, formatTime, isValidPhone, normalizePhone, paiseFromRupees, safeNextPath } from "./format";

describe("format", () => {
  it("formats paise as rupees", () => {
    expect(formatPaise(24900)).toBe("₹249");
    expect(formatPaise(24950)).toBe("₹249.50");
    expect(formatPaise(12345600)).toBe("₹1,23,456");
  });

  it("shows times in IST regardless of the device timezone", () => {
    // 06:30 UTC = 12:00 IST
    expect(formatTime("2026-10-10T06:30:00Z").toLowerCase().replace(/\s/g, " ")).toBe("12:00 pm");
  });

  it("normalises Indian mobile numbers to E.164", () => {
    expect(normalizePhone("98765 43210")).toBe("+919876543210");
    expect(normalizePhone("+91 98765-43210")).toBe("+919876543210");
    expect(normalizePhone("919876543210")).toBe("+919876543210");
    expect(isValidPhone(normalizePhone("12345"))).toBe(false);
  });

  it("only allows same-site redirect targets", () => {
    expect(safeNextPath("/orders/1")).toBe("/orders/1");
    expect(safeNextPath("https://evil.example")).toBe("/");
    expect(safeNextPath("//evil.example")).toBe("/");
    expect(safeNextPath("/\\evil.example")).toBe("/");
    expect(safeNextPath(null, "/orders")).toBe("/orders");
  });
});

describe("paiseFromRupees", () => {
  it("parses rupee amounts into integer paise without float error", () => {
    expect(paiseFromRupees("249")).toBe(24900);
    expect(paiseFromRupees("249.5")).toBe(24950);
    expect(paiseFromRupees("0.29")).toBe(29);
    expect(paiseFromRupees("₹1,249.50")).toBe(124950);
    expect(paiseFromRupees(" 10.05 ")).toBe(1005);
  });

  it("rejects anything that isn't a rupee amount", () => {
    for (const bad of ["", "abc", "1.234", "-5", "1e3", "12.", "."]) expect(paiseFromRupees(bad)).toBeNull();
  });
});
