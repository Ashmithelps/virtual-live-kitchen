"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { formatDateTime } from "@/lib/format";
import { Badge, Button, Card, ErrorState, Field, InlineError, PageLoading, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

const STATUS = {
  NOT_SET_UP: { label: "Not set up", tone: "stone" as const, text: "Add your bank details to start receiving tips. Customers only see the tip button once your account is verified." },
  PENDING_KYC: { label: "Being verified", tone: "amber" as const, text: "Razorpay is checking your details. This usually takes a few hours to a couple of days. You'll be able to receive tips once it's done." },
  ACTIVE: { label: "Active", tone: "green" as const, text: "You can receive tips. They go straight to your bank account through Razorpay; the restaurant can't redirect or hold them." },
};

const EMPTY = { legalName: "", email: "", bankAccountNumber: "", ifsc: "", pan: "", street: "", city: "", state: "", postalCode: "" };

/** Chef payout onboarding: bank details go to Razorpay (Route linked account); we keep only masked digits. */
export default function PayoutsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const account = useQuery({ queryKey: ["chef-payout"], queryFn: () => unwrap(api.GET("/api/chef/payout-account")) });
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: () =>
      unwrap(
        api.PUT("/api/chef/payout-account", {
          body: { ...form, ifsc: form.ifsc.trim().toUpperCase(), pan: form.pan.trim().toUpperCase(), bankAccountNumber: form.bankAccountNumber.replace(/\s/g, "") },
        }),
      ),
    onSuccess: (saved) => {
      queryClient.setQueryData(["chef-payout"], saved);
      setEditing(false);
      setForm(EMPTY); // don't keep bank details in memory longer than needed
      toast.success(saved.status === "ACTIVE" ? "Payout details updated" : "Submitted. Razorpay is verifying your details.");
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const refresh = useMutation({
    mutationFn: () => unwrap(api.POST("/api/chef/payout-account/refresh")),
    onSuccess: (fresh) => {
      queryClient.setQueryData(["chef-payout"], fresh);
      toast.info(fresh.status === "ACTIVE" ? "Verified! You can receive tips now." : "Still being verified.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (account.isPending) return <PageLoading />;
  if (account.isError) return <ErrorState message={errorMessage(account.error)} onRetry={() => account.refetch()} />;
  const a = account.data;
  const status = STATUS[a.status];
  const firstTime = a.status === "NOT_SET_UP";

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!/^[A-Z]{3}P[A-Z][0-9]{4}[A-Z]$/.test(form.pan.trim().toUpperCase())) return setError("Enter your personal PAN, e.g. ABCPE1234F.");
    if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(form.ifsc.trim().toUpperCase())) return setError("Enter a valid IFSC, e.g. HDFC0001234.");
    if (!/^[0-9]{9,18}$/.test(form.bankAccountNumber.replace(/\s/g, ""))) return setError("The bank account number should be 9–18 digits.");
    submit.mutate();
  };
  const field = (key: keyof typeof EMPTY, label: string, extra: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <Field label={label}>
      <input className={inputClass} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} {...extra} />
    </Field>
  );

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold">Payouts</h1>
      <Card className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="mr-auto font-semibold">Tips to your bank account</h2>
          <Badge tone={status.tone}>{status.label}</Badge>
        </div>
        <p className="text-sm text-stone-600">{status.text}</p>
        {!firstTime && (
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-stone-500">Name</dt>
            <dd>{a.legalName}</dd>
            <dt className="text-stone-500">Bank account</dt>
            <dd className="font-mono">{a.bankAccountMasked}</dd>
            <dt className="text-stone-500">PAN</dt>
            <dd className="font-mono">{a.panMasked}</dd>
            {a.providerStatus && a.status !== "ACTIVE" && (
              <>
                <dt className="text-stone-500">Razorpay status</dt>
                <dd>{a.providerStatus.replaceAll("_", " ")}</dd>
              </>
            )}
            {a.activatedAt && (
              <>
                <dt className="text-stone-500">Active since</dt>
                <dd>{formatDateTime(a.activatedAt)}</dd>
              </>
            )}
          </dl>
        )}
        <div className="flex flex-wrap gap-2">
          {a.status === "PENDING_KYC" && (
            <Button variant="secondary" busy={refresh.isPending} onClick={() => refresh.mutate()}>
              Check status
            </Button>
          )}
          {!firstTime && !editing && (
            <Button variant="ghost" onClick={() => { setEditing(true); setForm({ ...EMPTY, legalName: a.legalName ?? "" }); }}>
              Change bank account
            </Button>
          )}
        </div>
      </Card>

      {(firstTime || editing) && (
        <Card>
          <form onSubmit={onSubmit} className="space-y-3" noValidate autoComplete="off">
            <h2 className="font-semibold">{firstTime ? "Your details" : "New bank account"}</h2>
            <p className="text-xs text-stone-500">
              Sent securely to Razorpay, our payment partner, to verify you (KYC) and pay you. We store only the last 4 digits.
              {!firstTime && " Your name and PAN can't change; enter them as before."}
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              {field("legalName", "Full name (as on PAN and bank account)", { autoComplete: "name" })}
              {field("email", "Email", { type: "email", autoComplete: "email" })}
              {field("pan", "PAN", { maxLength: 10, autoCapitalize: "characters" })}
              {field("bankAccountNumber", "Bank account number", { inputMode: "numeric" })}
              {field("ifsc", "IFSC", { maxLength: 11, autoCapitalize: "characters" })}
            </div>
            <h3 className="pt-2 text-sm font-semibold">Address</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              {field("street", "Street address")}
              {field("city", "City")}
              {field("state", "State")}
              {field("postalCode", "PIN code", { inputMode: "numeric", maxLength: 6 })}
            </div>
            <InlineError message={error} />
            <div className="flex gap-2">
              <Button type="submit" busy={submit.isPending}>
                {firstTime ? "Submit for verification" : "Update bank account"}
              </Button>
              {editing && (
                <Button type="button" variant="ghost" onClick={() => { setEditing(false); setForm(EMPTY); }}>
                  Cancel
                </Button>
              )}
            </div>
          </form>
        </Card>
      )}
    </div>
  );
}
