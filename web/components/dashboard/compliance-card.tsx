"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { GST_STATES } from "@/lib/gst-states";
import { uploadImage } from "@/lib/owner/uploads";
import { Button, Card, ErrorState, Field, InlineError, PageLoading, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

type Compliance = components["schemas"]["ComplianceResponse"];

/** FSSAI licence (number + scan) and GST details. Admins review the licence; the GST details go on invoices. */
export function ComplianceCard({ restaurantId }: { restaurantId: string }) {
  const key = ["owner", "compliance", restaurantId];
  const current = useQuery({ queryKey: key, queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/compliance", { params: { path: { restaurantId } } })) });
  if (current.isPending) return <PageLoading />;
  if (current.isError) return <ErrorState message={errorMessage(current.error)} onRetry={() => current.refetch()} />;
  return <ComplianceForm restaurantId={restaurantId} saved={current.data} />;
}

function ComplianceForm({ restaurantId, saved }: { restaurantId: string; saved: Compliance }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ["owner", "compliance", restaurantId];
  const [form, setForm] = useState({
    fssaiNumber: saved.fssaiNumber ?? "",
    fssaiDocKey: saved.fssaiDocKey ?? "",
    legalName: saved.legalName ?? "",
    gstin: saved.gstin ?? "",
    billingStateCode: saved.billingStateCode ?? "",
  });
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => {
      const blank = (v: string) => (v.trim() === "" ? undefined : v.trim());
      return unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/compliance", { params: { path: { restaurantId } }, body: {
        fssaiNumber: blank(form.fssaiNumber), fssaiDocKey: blank(form.fssaiDocKey), legalName: blank(form.legalName),
        gstin: blank(form.gstin.toUpperCase()), billingStateCode: blank(form.billingStateCode),
      } }));
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved);
      void queryClient.invalidateQueries({ queryKey: ["owner", "billing", restaurantId] });
      toast.success("Licence and tax details saved");
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const upload = async (file: File | undefined) => {
    if (!file) return;
    setUploading(true);
    try {
      const key = await uploadImage(restaurantId, "FSSAI_LICENCE", file);
      setForm((f) => ({ ...f, fssaiDocKey: key }));
      toast.success("Licence uploaded. Save to keep it.");
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setUploading(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (form.fssaiNumber && !/^\d{14}$/.test(form.fssaiNumber.trim())) return setError("The FSSAI licence number has 14 digits.");
    if (form.gstin && !/^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(form.gstin.trim().toUpperCase())) return setError("That doesn't look like a GSTIN (15 characters).");
    save.mutate();
  };

  return (
    <Card>
      <form onSubmit={submit} className="space-y-3" noValidate>
        <div>
          <h2 className="font-semibold">Licence & tax details</h2>
          <p className="text-sm text-stone-600">Our team checks the FSSAI licence. The legal name and GST state appear on your subscription invoices.</p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="FSSAI licence number" hint="14 digits">
            <input className={inputClass} value={form.fssaiNumber} onChange={(e) => setForm({ ...form, fssaiNumber: e.target.value })} inputMode="numeric" maxLength={14} />
          </Field>
          <Field label="Licence document (PDF or photo)">
            <div className="flex items-center gap-2">
              <label className="cursor-pointer rounded-xl border border-stone-300 px-3 py-2 text-sm font-semibold hover:bg-stone-100">
                {uploading ? "Uploading…" : form.fssaiDocKey ? "Replace file" : "Upload file"}
                <input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" className="sr-only" disabled={uploading} onChange={(e) => upload(e.target.files?.[0])} />
              </label>
              {saved.fssaiDocUrl && form.fssaiDocKey === saved.fssaiDocKey && (
                <a href={saved.fssaiDocUrl} target="_blank" rel="noreferrer" className="text-sm text-brand-700 underline">View current</a>
              )}
              {form.fssaiDocKey && form.fssaiDocKey !== saved.fssaiDocKey && <span className="text-sm text-green-700">New file ready</span>}
            </div>
          </Field>
          <Field label="Legal name (for invoices)">
            <input className={inputClass} value={form.legalName} onChange={(e) => setForm({ ...form, legalName: e.target.value })} />
          </Field>
          <Field label="GSTIN (optional)" hint="Leave empty if not registered">
            <input className={inputClass} value={form.gstin} onChange={(e) => setForm({ ...form, gstin: e.target.value.toUpperCase() })} maxLength={15} />
          </Field>
          <Field label="GST state (place of supply)">
            <select className={inputClass} value={form.billingStateCode} onChange={(e) => setForm({ ...form, billingStateCode: e.target.value })}>
              <option value="">Choose…</option>
              {GST_STATES.map((s) => (
                <option key={s.code} value={s.code}>{s.code} · {s.name}</option>
              ))}
            </select>
          </Field>
        </div>
        <InlineError message={error} />
        <Button type="submit" busy={save.isPending} disabled={uploading}>Save details</Button>
      </form>
    </Card>
  );
}
