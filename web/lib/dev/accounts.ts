import type { Role } from "@/lib/auth/roles";

/**
 * The demo logins created by the backend's `dev-seed` profile (backend DevSeeder; keep the phones in
 * sync). Development only: the password stays on the Next server (DEV_SEED_PASSWORD in .env.local).
 */
export const DEV_ACCOUNTS: Record<Role, { phone: string; label: string }> = {
  ADMIN: { phone: "+919000000001", label: "Admin" },
  RESTAURANT_OWNER: { phone: "+919000000002", label: "Owner (Dev Spice Kitchen)" },
  CHEF: { phone: "+919000000003", label: "Chef Ravi (Tandoor)" },
  CUSTOMER: { phone: "+919000000005", label: "Customer" },
};

export const DEV_TOOLS_ENABLED = process.env.NODE_ENV === "development";
