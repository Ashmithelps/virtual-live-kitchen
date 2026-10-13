"use client";

import { useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/errors";
import type { OpeningSlot, OrderType, Restaurant, RestaurantRequest } from "@/lib/api/types";
import { ORDER_TYPE_LABEL } from "@/lib/format";
import { uploadImage } from "@/lib/owner/uploads";
import { Button, Card, Field, InlineError, inputBaseClass, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

const DAYS: OpeningSlot["day"][] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const ORDER_TYPES: OrderType[] = ["DINE_IN", "TAKEAWAY", "DELIVERY"];

type FormState = {
  name: string;
  slug: string;
  address: string;
  city: string;
  latitude: string;
  longitude: string;
  cuisineTags: string;
  orderTypes: OrderType[];
  deliveryRadiusKm: string;
  openingHours: OpeningSlot[];
  logoKey: string | null;
};

function initialState(r?: Restaurant): FormState {
  return {
    name: r?.name ?? "",
    slug: r?.slug ?? "",
    address: r?.address ?? "",
    city: r?.city ?? "",
    latitude: r ? String(r.latitude) : "",
    longitude: r ? String(r.longitude) : "",
    cuisineTags: r?.cuisineTags.join(", ") ?? "",
    orderTypes: r?.orderTypes ?? ["DINE_IN", "TAKEAWAY"],
    deliveryRadiusKm: r?.deliveryRadiusKm != null ? String(r.deliveryRadiusKm) : "",
    openingHours: r?.openingHours ?? DAYS.map((day) => ({ day, opens: "11:00", closes: "23:00" })),
    logoKey: r?.logoKey ?? null,
  };
}

/** Validates the form and builds the request, or returns the first problem. */
export function toRequest(f: FormState): RestaurantRequest | string {
  const lat = Number(f.latitude);
  const lng = Number(f.longitude);
  if (!f.name.trim()) return "Enter the restaurant's name.";
  if (!f.address.trim() || !f.city.trim()) return "Enter the address and city.";
  if (f.latitude.trim() === "" || !Number.isFinite(lat) || lat < -90 || lat > 90) return "Latitude must be a number between -90 and 90.";
  if (f.longitude.trim() === "" || !Number.isFinite(lng) || lng < -180 || lng > 180) return "Longitude must be a number between -180 and 180.";
  if (f.orderTypes.length === 0) return "Pick at least one order type.";
  const radius = f.deliveryRadiusKm.trim() === "" ? null : Number(f.deliveryRadiusKm);
  if (f.orderTypes.includes("DELIVERY") && (radius === null || !(radius > 0 && radius <= 50))) return "Delivery needs a radius between 0 and 50 km.";
  return {
    name: f.name.trim(),
    slug: f.slug.trim() || undefined,
    address: f.address.trim(),
    city: f.city.trim(),
    latitude: lat,
    longitude: lng,
    cuisineTags: f.cuisineTags.split(",").map((t) => t.trim()).filter(Boolean),
    openingHours: f.openingHours,
    orderTypes: f.orderTypes,
    deliveryRadiusKm: f.orderTypes.includes("DELIVERY") ? (radius ?? undefined) : undefined,
    logoKey: f.logoKey ?? undefined,
  };
}

export function RestaurantForm({
  restaurant,
  submitLabel,
  onSubmit,
}: {
  restaurant?: Restaurant;
  submitLabel: string;
  onSubmit: (request: RestaurantRequest) => Promise<unknown>;
}) {
  const toast = useToast();
  const [form, setForm] = useState<FormState>(() => initialState(restaurant));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [logoPreview, setLogoPreview] = useState<string | null>(null);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const request = toRequest(form);
    if (typeof request === "string") return setError(request);
    setError(null);
    setBusy(true);
    try {
      await onSubmit(request);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const useMyLocation = () => {
    if (!("geolocation" in navigator)) return toast.error("This browser can't share its location.");
    navigator.geolocation.getCurrentPosition(
      (p) => setForm((f) => ({ ...f, latitude: p.coords.latitude.toFixed(6), longitude: p.coords.longitude.toFixed(6) })),
      () => toast.error("Couldn't get your location."),
    );
  };

  const pickLogo = async (file: File | undefined) => {
    if (!file || !restaurant) return;
    setUploading(true);
    try {
      const key = await uploadImage(restaurant.id, "RESTAURANT_LOGO", file);
      set("logoKey", key);
      setLogoPreview(URL.createObjectURL(file));
      toast.success("Logo uploaded. Save to apply it.");
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setUploading(false);
    }
  };

  const setSlot = (index: number, patch: Partial<OpeningSlot>) =>
    set("openingHours", form.openingHours.map((s, i) => (i === index ? { ...s, ...patch } : s)));

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Card className="space-y-3">
        <h2 className="font-semibold">Basics</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Name">
            <input className={inputClass} value={form.name} onChange={(e) => set("name", e.target.value)} />
          </Field>
          <Field label="Web address" hint="Optional: generated from name and city. Lowercase letters, digits and dashes.">
            <input className={inputClass} value={form.slug} onChange={(e) => set("slug", e.target.value)} placeholder="spice-garden-bengaluru" />
          </Field>
          <Field label="Address">
            <input className={inputClass} value={form.address} onChange={(e) => set("address", e.target.value)} />
          </Field>
          <Field label="City">
            <input className={inputClass} value={form.city} onChange={(e) => set("city", e.target.value)} />
          </Field>
          <Field label="Latitude">
            <input className={inputClass} value={form.latitude} onChange={(e) => set("latitude", e.target.value)} inputMode="decimal" />
          </Field>
          <Field label="Longitude">
            <input className={inputClass} value={form.longitude} onChange={(e) => set("longitude", e.target.value)} inputMode="decimal" />
          </Field>
        </div>
        <Button type="button" variant="secondary" onClick={useMyLocation}>
          Use my current location
        </Button>
        <Field label="Cuisines" hint="Comma separated, e.g. North Indian, Biryani">
          <input className={inputClass} value={form.cuisineTags} onChange={(e) => set("cuisineTags", e.target.value)} />
        </Field>
      </Card>

      <Card className="space-y-3">
        <h2 className="font-semibold">Orders</h2>
        <div className="flex flex-wrap gap-4">
          {ORDER_TYPES.map((type) => (
            <label key={type} className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={form.orderTypes.includes(type)}
                onChange={(e) => set("orderTypes", e.target.checked ? [...form.orderTypes, type] : form.orderTypes.filter((t) => t !== type))}
              />
              {ORDER_TYPE_LABEL[type]}
            </label>
          ))}
        </div>
        {form.orderTypes.includes("DELIVERY") && (
          <Field label="Delivery radius (km)">
            <input className={`${inputBaseClass} max-w-40`} value={form.deliveryRadiusKm} onChange={(e) => set("deliveryRadiusKm", e.target.value)} inputMode="decimal" />
          </Field>
        )}
      </Card>

      <Card className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold">Opening hours (IST)</h2>
          <p className="text-xs text-stone-500">Closing at or before opening means after midnight; equal times mean open 24 hours.</p>
        </div>
        {form.openingHours.length === 0 && <p className="text-sm text-stone-500">No hours: the restaurant shows as closed.</p>}
        {form.openingHours.map((slot, i) => (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <select className={`${inputBaseClass} w-40`} value={slot.day} onChange={(e) => setSlot(i, { day: e.target.value as OpeningSlot["day"] })} aria-label="Day">
              {DAYS.map((d) => (
                <option key={d} value={d}>
                  {d.charAt(0) + d.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
            <input type="time" className={`${inputBaseClass} w-32`} value={slot.opens} onChange={(e) => setSlot(i, { opens: e.target.value })} aria-label="Opens" />
            <span className="text-stone-500">to</span>
            <input type="time" className={`${inputBaseClass} w-32`} value={slot.closes} onChange={(e) => setSlot(i, { closes: e.target.value })} aria-label="Closes" />
            <Button type="button" variant="ghost" onClick={() => set("openingHours", form.openingHours.filter((_, j) => j !== i))}>
              Remove
            </Button>
          </div>
        ))}
        <Button type="button" variant="secondary" onClick={() => set("openingHours", [...form.openingHours, { day: "MONDAY", opens: "11:00", closes: "23:00" }])}>
          Add hours
        </Button>
      </Card>

      <Card className="space-y-3">
        <h2 className="font-semibold">Logo</h2>
        {restaurant ? (
          <div className="flex flex-wrap items-center gap-3">
            {logoPreview ? (
               
              <img src={logoPreview} alt="New logo" className="size-16 rounded-xl object-cover" />
            ) : (
              <p className="text-sm text-stone-600">{form.logoKey ? "A logo is set; it's burned into kitchen clips." : "No logo yet."}</p>
            )}
            <label className="cursor-pointer rounded-xl border border-stone-300 px-4 py-2 text-sm font-semibold hover:bg-stone-100">
              {uploading ? "Uploading…" : "Choose image"}
              <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={uploading} onChange={(e) => pickLogo(e.target.files?.[0])} />
            </label>
            {form.logoKey && (
              <Button type="button" variant="ghost" onClick={() => { set("logoKey", null); setLogoPreview(null); }}>
                Remove logo
              </Button>
            )}
          </div>
        ) : (
          <p className="text-sm text-stone-600">You can add a logo after creating the restaurant.</p>
        )}
      </Card>

      <InlineError message={error} />
      <Button type="submit" busy={busy} disabled={uploading}>
        {submitLabel}
      </Button>
    </form>
  );
}
