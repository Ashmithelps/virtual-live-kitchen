import type { User } from "@/lib/api/types";

export type Role = User["role"];

export const ROLE_LABEL: Record<Role, string> = {
  CUSTOMER: "Customer",
  RESTAURANT_OWNER: "Owner",
  CHEF: "Chef",
  ADMIN: "Admin",
};

/** Where each role lands after logging in (unless a ?next= page asked for somewhere else). */
export function homeFor(user: Pick<User, "role" | "status">): string {
  switch (user.role) {
    case "RESTAURANT_OWNER":
      return user.status === "PENDING_APPROVAL" ? "/pending" : "/dashboard";
    case "CHEF":
      return "/kitchen";
    case "ADMIN":
      return "/admin";
    default:
      return "/";
  }
}

export type NavLink = { href: string; label: string };

/** The header menu for each role (anonymous visitors get the customer links plus log in). */
export function navFor(user: Pick<User, "role" | "status"> | null): NavLink[] {
  if (!user) return [{ href: "/", label: "Restaurants" }];
  switch (user.role) {
    case "CUSTOMER":
      return [
        { href: "/", label: "Restaurants" },
        { href: "/orders", label: "My orders" },
      ];
    case "RESTAURANT_OWNER":
      return user.status === "PENDING_APPROVAL"
        ? [{ href: "/pending", label: "Approval" }]
        : [
            { href: "/dashboard", label: "Dashboard" },
            { href: "/kitchen", label: "Kitchen display" },
          ];
    case "CHEF":
      return [
        { href: "/kitchen", label: "Kitchen" },
        { href: "/chef/clips", label: "My clips" },
        { href: "/chef/tips", label: "My tips" },
        { href: "/chef/payouts", label: "Payouts" },
        { href: "/chef/settings", label: "Settings" },
      ];
    case "ADMIN":
      return [
        { href: "/admin", label: "Overview" },
        { href: "/admin/restaurants", label: "Restaurants" },
        { href: "/admin/users", label: "Users" },
        { href: "/admin/hygiene", label: "Hygiene" },
        { href: "/admin/billing", label: "Billing" },
        { href: "/admin/audit", label: "Audit log" },
      ];
  }
}
