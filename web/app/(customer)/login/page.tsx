"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/errors";
import { useAuth } from "@/lib/auth/auth-context";
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
  const next = safeNextPath(useSearchParams().get("next"), "/orders");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [form, setForm] = useState({ name: "", phone: "", email: "", password: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (auth.status === "authenticated") router.replace(next);
  }, [auth.status, next, router]);

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
        </form>
      </Card>
      {mode === "register" && <p className="text-center text-xs text-stone-500">Ordered at a table before? Use the same mobile number and your past orders will appear.</p>}
    </div>
  );
}
