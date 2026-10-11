"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";

export function SiteHeader() {
  const auth = useAuth();
  return (
    <header className="sticky top-0 z-20 border-b border-stone-200 bg-white/95 backdrop-blur">
      <nav className="mx-auto flex h-14 max-w-2xl items-center justify-between px-4">
        <Link href="/" className="font-bold text-brand-700">
          Virtual Live Kitchen
        </Link>
        <div className="flex items-center gap-4 text-sm">
          <Link href="/orders" className="font-medium text-stone-700 hover:text-brand-700">
            My orders
          </Link>
          {auth.status === "authenticated" ? (
            <button onClick={() => auth.logout()} className="text-stone-500 hover:text-stone-800" title={auth.user.name}>
              Log out
            </button>
          ) : auth.status === "anonymous" ? (
            <Link href="/login" className="font-medium text-brand-700">
              Log in
            </Link>
          ) : null}
        </div>
      </nav>
    </header>
  );
}
