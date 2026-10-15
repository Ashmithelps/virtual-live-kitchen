"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/errors";
import { useAuth } from "@/lib/auth/auth-context";
import { homeFor } from "@/lib/auth/roles";
import { isValidPhone, normalizePhone, safeNextPath } from "@/lib/format";
import { Button, Card, Field, InlineError, PageLoading, inputClass } from "@/components/ui";

export default function LoginPage() {
  return (
    <Suspense fallback={<PageLoading />}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const auth = useAuth();
  const router = useRouter();
  const requested = useSearchParams().get("next");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [form, setForm] = useState({ name: "", phone: "", email: "", password: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Back to the page that sent you here, else your role's home (owners awaiting approval: /pending).
  useEffect(() => {
    if (auth.status !== "authenticated") return;
    const home = homeFor(auth.user);
    router.replace(requested && home !== "/pending" ? safeNextPath(requested, home) : home);
  }, [auth.status, auth.user, requested, router]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    const phone = normalizePhone(form.phone);
    if (!isValidPhone(phone)) return setError("Enter a valid mobile number, e.g. 98765 43210.");
    if (mode === "register" && !form.name.trim()) return setError("Enter your name.");
    if (mode === "register" && form.password.length < 8) return setError("Password must be at least 8 characters.");
    setBusy(true);
    try {
      if (mode === "login") await auth.login(phone, form.password);
      else await auth.register({ name: form.name.trim(), phone, email: form.email.trim() || undefined, password: form.password });
      // Redirect happens in the effect above once the session is set.
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  if (auth.status === "authenticated") return <PageLoading label="Signing you in…" />;

  return (
    <div className="mx-auto max-w-sm space-y-4 pt-4">
      <div className="grid grid-cols-2 rounded-xl bg-stone-200 p-1 text-sm font-semibold">
        {(["login", "register"] as const).map((m) => (
          <button
            key={m}
            type="button"
            aria-pressed={mode === m}
            onClick={() => {
              setMode(m);
              setError(null);
            }}
            className={`rounded-lg py-2 ${mode === m ? "bg-white shadow-sm" : "text-stone-600"}`}
          >
            {m === "login" ? "Log in" : "Create account"}
          </button>
        ))}
      </div>
      <Card>
        <form onSubmit={submit} className="space-y-3" noValidate>
          {mode === "register" && (
            <Field label="Name">
              <input className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} autoComplete="name" />
            </Field>
          )}
          <Field label="Mobile number">
            <input className={inputClass} value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} inputMode="tel" autoComplete="tel" placeholder="98765 43210" />
          </Field>
          {mode === "register" && (
            <Field label="Email (optional)">
              <input className={inputClass} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} autoComplete="email" />
            </Field>
          )}
          <Field label="Password" hint={mode === "register" ? "At least 8 characters." : undefined}>
            <input
              className={inputClass}
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
            />
          </Field>
          <InlineError message={error} />
          <Button type="submit" className="w-full" busy={busy}>
            {mode === "login" ? "Log in" : "Create account"}
          </Button>
          {mode === "register" && (
            <p className="text-xs text-stone-500">
              By creating an account you agree to the{" "}
              <Link href="/terms" className="underline">terms</Link> and <Link href="/privacy" className="underline">privacy policy</Link>.
            </p>
          )}
        </form>
      </Card>
      {mode === "register" && <p className="text-center text-xs text-stone-500">Ordered at a table before? Use the same mobile number and your past orders will appear.</p>}
      <p className="text-center text-sm text-stone-600">
        Own a restaurant?{" "}
        <Link href="/register/owner" className="font-medium text-brand-700 underline">
          List it with us
        </Link>
        . Chefs: log in with the number and password your manager gave you.
      </p>
    </div>
  );
}
