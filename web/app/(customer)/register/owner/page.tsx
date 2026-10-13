"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/errors";
import { useAuth } from "@/lib/auth/auth-context";
import { homeFor } from "@/lib/auth/roles";
import { isValidPhone, normalizePhone } from "@/lib/format";
import { Button, Card, Field, InlineError, PageLoading, inputClass } from "@/components/ui";

/** Restaurant owners sign up here; the account stays PENDING_APPROVAL until an admin approves it. */
export default function OwnerRegisterPage() {
  const auth = useAuth();
  const router = useRouter();
  const [form, setForm] = useState({ name: "", phone: "", email: "", password: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (auth.status === "authenticated") router.replace(homeFor(auth.user));
  }, [auth.status, auth.user, router]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    const phone = normalizePhone(form.phone);
    if (!form.name.trim()) return setError("Enter your name.");
    if (!isValidPhone(phone)) return setError("Enter a valid mobile number, e.g. 98765 43210.");
    if (form.password.length < 8) return setError("Password must be at least 8 characters.");
    setBusy(true);
    try {
      await auth.register({ name: form.name.trim(), phone, email: form.email.trim() || undefined, password: form.password, role: "RESTAURANT_OWNER" });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  if (auth.status === "authenticated") return <PageLoading label="Setting up your account…" />;

  return (
    <div className="mx-auto max-w-sm space-y-4 pt-4">
      <div>
        <h1 className="text-2xl font-bold">List your restaurant</h1>
        <p className="mt-1 text-sm text-stone-600">Create an owner account. We review new owners before you can set up your restaurant.</p>
      </div>
      <Card>
        <form onSubmit={submit} className="space-y-3" noValidate>
          <Field label="Your name">
            <input className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} autoComplete="name" />
          </Field>
          <Field label="Mobile number">
            <input className={inputClass} value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} inputMode="tel" autoComplete="tel" placeholder="98765 43210" />
          </Field>
          <Field label="Email (optional)">
            <input className={inputClass} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} autoComplete="email" />
          </Field>
          <Field label="Password" hint="At least 8 characters.">
            <input className={inputClass} type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} autoComplete="new-password" />
          </Field>
          <InlineError message={error} />
          <Button type="submit" className="w-full" busy={busy}>
            Create owner account
          </Button>
        </form>
      </Card>
      <p className="text-center text-sm text-stone-600">
        Already registered?{" "}
        <Link href="/login" className="font-medium text-brand-700 underline">
          Log in
        </Link>
      </p>
    </div>
  );
}
