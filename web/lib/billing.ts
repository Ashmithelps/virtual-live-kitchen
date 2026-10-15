import type { components } from "@/lib/api/schema";

export type Plan = components["schemas"]["PlanResponse"];
export type Invoice = components["schemas"]["InvoiceResponse"];
export type SubscriptionStatus = components["schemas"]["BillingOverviewResponse"]["status"];

export const SUBSCRIPTION_STATUS: Record<SubscriptionStatus, { label: string; tone: "green" | "amber" | "red" | "stone" | "brand" }> = {
  TRIALING: { label: "Free trial", tone: "brand" },
  ACTIVE: { label: "Active", tone: "green" },
  PAST_DUE: { label: "Payment overdue", tone: "amber" },
  SUSPENDED: { label: "Suspended", tone: "red" },
  CANCELLED: { label: "Cancelled", tone: "stone" },
};

export function planFeatureLines(plan: Plan): string[] {
  const f = plan.features;
  return [
    `${f.maxStations} recording ${f.maxStations === 1 ? "station" : "stations"}`,
    `Clips kept ${f.clipRetentionDays} days`,
    f.analytics ? "Analytics" : "No analytics",
  ];
}

/** Whole days from now until `iso`, never negative. */
export function daysUntil(iso: string, now = Date.now()): number {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - now) / 86_400_000));
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", { timeZone: "Asia/Kolkata", day: "numeric", month: "short", year: "numeric" });
}
