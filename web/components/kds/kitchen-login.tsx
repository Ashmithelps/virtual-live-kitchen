"use client";

import { useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/errors";
import { useAuth } from "@/lib/auth/auth-context";
import { isValidPhone, normalizePhone } from "@/lib/format";
import { unlockSound } from "@/lib/kds/chime";

export const kdsInput =
  "w-full rounded-xl border border-stone-600 bg-stone-900 px-4 py-3 text-lg text-white outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-600/40";

export function KitchenLogin() {
  const auth = useAuth();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    unlockSound(); // the login tap counts as the gesture browsers require before playing sound
    const normalized = normalizePhone(phone);
    if (!isValidPhone(normalized)) return setError("Enter a valid mobile number.");
    setBusy(true);
    setError(null);
    try {
      await auth.login(normalized, password);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center p-6">
      <form onSubmit={submit} className="w-full max-w-md space-y-4 rounded-3xl bg-stone-900 p-8" noValidate>
        <div>
          <h1 className="text-3xl font-bold">Kitchen display</h1>
          <p className="mt-1 text-stone-400">Chefs and owners: log in to see live orders.</p>
        </div>
        <label className="block space-y-1">
          <span className="text-sm text-stone-300">Mobile number</span>
          <input className={kdsInput} value={phone} onChange={(e) => setPhone(e.target.value)} inputMode="tel" autoComplete="username" />
        </label>
        <label className="block space-y-1">
          <span className="text-sm text-stone-300">Password</span>
          <input className={kdsInput} type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        </label>
        {error && (
          <p role="alert" className="rounded-xl bg-red-950 px-4 py-3 text-red-200">
            {error}
          </p>
        )}
        <button disabled={busy} className="min-h-14 w-full rounded-2xl bg-brand-600 text-xl font-bold text-white active:bg-brand-700 disabled:opacity-60">
          {busy ? "Logging in…" : "Log in"}
        </button>
      </form>
    </div>
  );
}
