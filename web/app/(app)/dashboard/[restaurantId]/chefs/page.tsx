"use client";

import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { Chef } from "@/lib/api/types";
import { formatDateTime, isValidPhone, normalizePhone } from "@/lib/format";
import { chefsQuery, ownerKeys, stationsQuery } from "@/lib/owner/queries";
import { uploadImage } from "@/lib/owner/uploads";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Badge, Button, Card, EmptyState, ErrorState, Field, InlineError, PageLoading, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

export default function ChefsPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const path = { restaurantId };
  const chefs = useQuery(chefsQuery(restaurantId));
  const stations = useQuery(stationsQuery(restaurantId));
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Chef | null>(null);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ownerKeys.chefs(restaurantId) });
    void queryClient.invalidateQueries({ queryKey: ownerKeys.stations(restaurantId) });
  };
  const remove = useMutation({
    mutationFn: (chef: Chef) => unwrap(api.DELETE("/api/owner/restaurants/{restaurantId}/chefs/{chefId}", { params: { path: { ...path, chefId: chef.id } } })),
    onSuccess: (_, chef) => toast.success(`${chef.displayName} removed; their login is disabled`),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: refresh,
  });

  if (chefs.isPending) return <PageLoading />;
  if (chefs.isError) return <ErrorState message={errorMessage(chefs.error)} onRetry={() => chefs.refetch()} />;

  const stationsOf = (chefId: string) => (stations.data ?? []).filter((s) => s.chefs.some((c) => c.id === chefId)).map((s) => s.name);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="max-w-xl text-sm text-stone-600">
          Chefs log in at <code>/kitchen</code> with their mobile number and the password you set. They must accept recording consent before you can put them on a station.
          Tips go straight to each chef&apos;s own bank account through Razorpay; you see how many they received, never amounts, and can&apos;t redirect or hold them.
        </p>
        {!creating && <Button onClick={() => setCreating(true)}>Add chef</Button>}
      </div>

      {creating && (
        <CreateChefForm
          onCancel={() => setCreating(false)}
          onCreate={async (body) => {
            await unwrap(api.POST("/api/owner/restaurants/{restaurantId}/chefs", { params: { path }, body }));
            setCreating(false);
            toast.success(`Chef account created. Share the password with ${body.name}.`);
            refresh();
          }}
        />
      )}

      {chefs.data.length === 0 && !creating ? (
        <EmptyState title="No chefs yet" message="Add a login for each chef who cooks at your stations." />
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {chefs.data.map((chef) =>
            editing?.id === chef.id ? (
              <EditChefForm
                key={chef.id}
                restaurantId={restaurantId}
                chef={chef}
                onCancel={() => setEditing(null)}
                onSave={async (body) => {
                  await unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/chefs/{chefId}", { params: { path: { ...path, chefId: chef.id } }, body }));
                  setEditing(null);
                  toast.success("Chef updated");
                  refresh();
                }}
              />
            ) : (
              <Card key={chef.id} className="space-y-2">
                <div className="flex items-start gap-3">
                  {chef.photoUrl ? (
                     
                    <img src={chef.photoUrl} alt="" className="size-14 rounded-full object-cover" />
                  ) : (
                    <div className="flex size-14 items-center justify-center rounded-full bg-stone-100 text-xl" aria-hidden>
                      👩‍🍳
                    </div>
                  )}
                  <div className="mr-auto">
                    <p className="text-lg font-semibold">{chef.displayName}</p>
                    <p className="text-sm text-stone-500">{chef.phone}</p>
                    {chef.specialty && <p className="text-sm text-stone-600">{chef.specialty}</p>}
                  </div>
                  {chef.recordingConsent ? <Badge tone="green">Consent given</Badge> : <Badge tone="amber">No consent yet</Badge>}
                </div>
                <p className="text-sm text-stone-600">
                  {chef.recordingConsent && chef.consentAcceptedAt ? `Accepted ${formatDateTime(chef.consentAcceptedAt)} · ` : ""}
                  Station: {stationsOf(chef.id).join(", ") || "none"}
                </p>
                <p className="text-sm text-stone-600">
                  💸 {chef.tipCount} {chef.tipCount === 1 ? "tip" : "tips"} received · payouts{" "}
                  {chef.payoutStatus === "ACTIVE" ? "active" : chef.payoutStatus === "PENDING_KYC" ? "being verified" : "not set up"}
                </p>
                <div className="flex gap-2">
                  <Button variant="secondary" onClick={() => setEditing(chef)}>
                    Edit
                  </Button>
                  <ConfirmButton label="Remove" confirmLabel="Disable this login?" onConfirm={() => remove.mutate(chef)} />
                </div>
              </Card>
            ),
          )}
        </div>
      )}
    </div>
  );
}

type CreateChef = { name: string; phone: string; initialPassword: string; displayName?: string; specialty?: string };

function CreateChefForm({ onCreate, onCancel }: { onCreate: (body: CreateChef) => Promise<void>; onCancel: () => void }) {
  const [form, setForm] = useState({ name: "", phone: "", initialPassword: "", displayName: "", specialty: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const phone = normalizePhone(form.phone);
    if (!form.name.trim()) return setError("Enter the chef's name.");
    if (!isValidPhone(phone)) return setError("Enter a valid mobile number.");
    if (form.initialPassword.length < 8) return setError("The initial password needs at least 8 characters.");
    setBusy(true);
    try {
      await onCreate({ name: form.name.trim(), phone, initialPassword: form.initialPassword, displayName: form.displayName.trim() || undefined, specialty: form.specialty.trim() || undefined });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card className="border-brand-200">
      <form onSubmit={submit} className="space-y-3" noValidate>
        <h2 className="font-semibold">New chef</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Full name">
            <input className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} autoFocus />
          </Field>
          <Field label="Mobile number" hint="Their login">
            <input className={inputClass} value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} inputMode="tel" />
          </Field>
          <Field label="Initial password" hint="At least 8 characters; share it with the chef.">
            <input className={inputClass} type="text" value={form.initialPassword} onChange={(e) => setForm({ ...form, initialPassword: e.target.value })} autoComplete="off" />
          </Field>
          <Field label="Name customers see (optional)" hint="e.g. Chef Ravi">
            <input className={inputClass} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          </Field>
          <Field label="Specialty (optional)">
            <input className={inputClass} value={form.specialty} onChange={(e) => setForm({ ...form, specialty: e.target.value })} />
          </Field>
        </div>
        <InlineError message={error} />
        <div className="flex gap-2">
          <Button type="submit" busy={busy}>
            Create chef
          </Button>
          <Button type="button" variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  );
}

type UpdateChef = { displayName: string; photoKey?: string; bio?: string; specialty?: string };

function EditChefForm({ restaurantId, chef, onSave, onCancel }: { restaurantId: string; chef: Chef; onSave: (body: UpdateChef) => Promise<void>; onCancel: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState({ displayName: chef.displayName, bio: chef.bio ?? "", specialty: chef.specialty ?? "", photoKey: chef.photoKey });
  const [preview, setPreview] = useState(chef.photoUrl);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const pickPhoto = async (file: File | undefined) => {
    if (!file) return;
    setUploading(true);
    try {
      const key = await uploadImage(restaurantId, "CHEF_PHOTO", file);
      setForm((f) => ({ ...f, photoKey: key }));
      setPreview(URL.createObjectURL(file));
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setUploading(false);
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.displayName.trim()) return setError("Enter a display name.");
    setBusy(true);
    try {
      await onSave({ displayName: form.displayName.trim(), bio: form.bio.trim() || undefined, specialty: form.specialty.trim() || undefined, photoKey: form.photoKey ?? undefined });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card className="border-brand-200">
      <form onSubmit={submit} className="space-y-3" noValidate>
        <h2 className="font-semibold">Edit {chef.displayName}</h2>
        <Field label="Display name">
          <input className={inputClass} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
        </Field>
        <Field label="Specialty">
          <input className={inputClass} value={form.specialty} onChange={(e) => setForm({ ...form, specialty: e.target.value })} />
        </Field>
        <Field label="Short bio">
          <input className={inputClass} value={form.bio} onChange={(e) => setForm({ ...form, bio: e.target.value })} />
        </Field>
        <div className="flex items-center gap-3">
          {preview && (
             
            <img src={preview} alt="" className="size-12 rounded-full object-cover" />
          )}
          <label className="cursor-pointer rounded-xl border border-stone-300 px-3 py-2 text-sm font-semibold hover:bg-stone-100">
            {uploading ? "Uploading…" : "Choose photo"}
            <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={uploading} onChange={(e) => pickPhoto(e.target.files?.[0])} />
          </label>
        </div>
        <InlineError message={error} />
        <div className="flex gap-2">
          <Button type="submit" busy={busy} disabled={uploading}>
            Save
          </Button>
          <Button type="button" variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  );
}
