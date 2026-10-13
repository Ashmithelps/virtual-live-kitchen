import type { OrderType } from "@/lib/api/types";

const wholeRupees = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const rupeesAndPaise = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 24900 → "₹249"; 24950 → "₹249.50". Money is always integer paise in the API. */
export function formatPaise(paise: number): string {
  return paise % 100 === 0 ? wholeRupees.format(paise / 100) : rupeesAndPaise.format(paise / 100);
}

/** "249" → 24900, "249.5" → 24950, "₹1,249.50" → 124950; null if it isn't a rupee amount. Parsed as text (no float maths). */
export function paiseFromRupees(input: string): number | null {
  const match = /^(\d{1,7})(?:\.(\d{1,2}))?$/.exec(input.replace(/[₹,\s]/g, ""));
  if (!match) return null;
  return Number(match[1]) * 100 + Number((match[2] ?? "").padEnd(2, "0"));
}

const istTime = new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", hour: "numeric", minute: "2-digit" });
const istDateTime = new Intl.DateTimeFormat("en-IN", {
  timeZone: "Asia/Kolkata",
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});

/** API timestamps are UTC instants; customers see Asia/Kolkata. */
export function formatTime(iso: string): string {
  return istTime.format(new Date(iso));
}

export function formatDateTime(iso: string): string {
  return istDateTime.format(new Date(iso));
}

export const ORDER_TYPE_LABEL: Record<OrderType, string> = {
  DINE_IN: "Dine-in",
  TAKEAWAY: "Takeaway",
  DELIVERY: "Delivery",
};

/** Accepts "98765 43210", "+91 98765-43210", "919876543210" → "+919876543210" (E.164). */
export function normalizePhone(input: string): string {
  const digits = input.replace(/[^\d+]/g, "");
  if (/^\d{10}$/.test(digits)) return `+91${digits}`;
  if (/^91\d{10}$/.test(digits)) return `+${digits}`;
  return digits;
}

export function isValidPhone(e164: string): boolean {
  return /^\+[1-9][0-9]{9,14}$/.test(e164);
}

/** Only same-site relative paths, so ?next= can't redirect to another origin. */
export function safeNextPath(next: string | null | undefined, fallback = "/"): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return fallback;
  return next;
}
