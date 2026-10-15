"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/lib/api/errors";
import { useAuth } from "@/lib/auth/auth-context";
import { ROLE_LABEL, homeFor, navFor, type Role } from "@/lib/auth/roles";
import type { Session } from "@/lib/auth/token-store";
import { DEV_ACCOUNTS, DEV_TOOLS_ENABLED } from "@/lib/dev/accounts";
import { useToast } from "@/components/toast";

/** Top bar for every page except the kitchen display and station phone; the menu depends on the role. */
export function AppHeader({ wide = false }: { wide?: boolean }) {
  const auth = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const links = navFor(auth.user);

  const logout = async () => {
    await auth.logout();
    router.push("/");
  };

  return (
    <header className="sticky top-0 z-20 border-b border-stone-200 bg-white/95 backdrop-blur">
      <nav className={`mx-auto flex min-h-14 flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2 ${wide ? "max-w-6xl" : "max-w-2xl"}`}>
        <Link href={auth.user ? homeFor(auth.user) : "/"} className="mr-auto font-bold text-brand-700">
          Virtual Live Kitchen
        </Link>
        {links.map((link) => {
          // Longest matching link wins, so "/admin" isn't highlighted on "/admin/hygiene".
          const matches = (href: string) => (href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(href + "/"));
          const active = matches(link.href) && !links.some((l) => l.href.length > link.href.length && l.href.startsWith(link.href) && matches(l.href));
          return (
            <Link
              key={link.href}
              href={link.href}
              aria-current={active ? "page" : undefined}
              className={`text-sm font-medium ${active ? "text-brand-700" : "text-stone-700 hover:text-brand-700"}`}
            >
              {link.label}
            </Link>
          );
        })}
        {auth.status === "authenticated" ? (
          <button onClick={logout} className="text-sm text-stone-500 hover:text-stone-800" title={`${auth.user.name} · ${ROLE_LABEL[auth.user.role]}`}>
            Log out
          </button>
        ) : auth.status === "anonymous" ? (
          <Link href={`/login?next=${encodeURIComponent(pathname)}`} className="text-sm font-medium text-brand-700">
            Log in
          </Link>
        ) : null}
        {DEV_TOOLS_ENABLED && <DevRoleSwitcher />}
      </nav>
    </header>
  );
}

/** `next dev` only: one click to become the dev-seed admin / owner / chef / customer. */
export function DevRoleSwitcher({ dark = false }: { dark?: boolean }) {
  const auth = useAuth();
  const router = useRouter();
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const become = async (role: Role) => {
    setBusy(true);
    try {
      const response = await fetch("/api/dev/login-as", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ role }) });
      const json = await response.json();
      if (!response.ok) throw new Error(json?.message ?? `Failed (${response.status})`);
      const user = auth.adopt(json as Session);
      toast.info(`Signed in as ${DEV_ACCOUNTS[role].label}`);
      router.push(homeFor(user));
    } catch (error) {
      toast.error(`Dev login failed: ${errorMessage(error)}. Is the backend running with the dev-seed profile?`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <label className={`flex items-center gap-1 rounded-lg border border-dashed px-2 py-1 text-xs ${dark ? "border-amber-500 text-amber-300" : "border-amber-500 text-amber-800"}`}>
      <span className="font-semibold">DEV</span>
      <select
        aria-label="Switch role (development only)"
        disabled={busy}
        value=""
        onChange={(e) => e.target.value && become(e.target.value as Role)}
        className="bg-transparent outline-none"
      >
        <option value="">Log in as…</option>
        {(Object.keys(DEV_ACCOUNTS) as Role[]).map((role) => (
          <option key={role} value={role} className="text-stone-900">
            {DEV_ACCOUNTS[role].label}
          </option>
        ))}
      </select>
    </label>
  );
}
