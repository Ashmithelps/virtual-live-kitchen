"use client";

import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { MenuCategory, MenuItem, MenuItemRequest } from "@/lib/api/types";
import { formatPaise, paiseFromRupees } from "@/lib/format";
import { ownerKeys } from "@/lib/owner/queries";
import { uploadImage } from "@/lib/owner/uploads";
import { Badge, Button, Card, EmptyState, ErrorState, Field, InlineError, PageLoading, VegMark, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";
import { ConfirmButton } from "@/components/dashboard/confirm-button";

export default function MenuPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ownerKeys.menu(restaurantId);
  const menu = useQuery({
    queryKey: key,
    queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/menu", { params: { path: { restaurantId } } })),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: key });
  const path = { restaurantId };

  const run = useMutation({
    mutationFn: (task: { work: () => Promise<unknown>; done?: string }) => task.work(),
    onSuccess: (_, task) => task.done && toast.success(task.done),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: refresh,
  });

  const [editingCategory, setEditingCategory] = useState<MenuCategory | "new" | null>(null);
  const [editingItem, setEditingItem] = useState<{ item: MenuItem | null; categoryId: string } | null>(null);

  if (menu.isPending) return <PageLoading />;
  if (menu.isError) return <ErrorState message={errorMessage(menu.error)} onRetry={() => menu.refetch()} />;
  const categories = menu.data;

  const itemRequest = (item: MenuItem, patch: Partial<MenuItemRequest> = {}): MenuItemRequest => ({
    categoryId: item.categoryId,
    name: item.name,
    description: item.description ?? undefined,
    pricePaise: item.pricePaise,
    veg: item.veg,
    imageKey: item.imageKey ?? undefined,
    available: item.available,
    sortOrder: item.sortOrder,
    ...patch,
  });

  /** Moves one entry up/down by renumbering the whole list and saving the ones whose position changed. */
  const reorder = <T extends { id: string; sortOrder: number }>(list: T[], index: number, delta: -1 | 1, save: (entry: T, sortOrder: number) => Promise<unknown>) => {
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    const next = [...list];
    next.splice(target, 0, ...next.splice(index, 1));
    run.mutate({ work: () => Promise.all(next.map((entry, i) => (entry.sortOrder === i ? null : save(entry, i)))) });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-stone-600">Customers see categories and items in this order. Unavailable items stay listed but can&apos;t be ordered.</p>
        <Button onClick={() => setEditingCategory("new")}>Add category</Button>
      </div>

      {editingCategory && (
        <CategoryForm
          category={editingCategory === "new" ? null : editingCategory}
          nextSortOrder={categories.length}
          onCancel={() => setEditingCategory(null)}
          onSave={async (body) => {
            if (editingCategory === "new") await unwrap(api.POST("/api/owner/restaurants/{restaurantId}/menu/categories", { params: { path }, body }));
            else await unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/menu/categories/{categoryId}", { params: { path: { ...path, categoryId: editingCategory.id } }, body }));
            setEditingCategory(null);
            toast.success("Category saved");
            await refresh();
          }}
        />
      )}

      {categories.length === 0 && !editingCategory && <EmptyState title="Your menu is empty" message="Add a category (e.g. Starters), then add dishes to it." />}

      {categories.map((category, ci) => (
        <Card key={category.id} className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <div className="mr-auto">
              <h2 className="text-lg font-semibold">{category.name}</h2>
              {category.description && <p className="text-sm text-stone-500">{category.description}</p>}
            </div>
            <Button variant="ghost" aria-label={`Move ${category.name} up`} disabled={ci === 0 || run.isPending}
              onClick={() => reorder(categories, ci, -1, (c, sortOrder) => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/menu/categories/{categoryId}", { params: { path: { ...path, categoryId: c.id } }, body: { name: c.name, description: c.description ?? undefined, sortOrder } })))}>
              ↑
            </Button>
            <Button variant="ghost" aria-label={`Move ${category.name} down`} disabled={ci === categories.length - 1 || run.isPending}
              onClick={() => reorder(categories, ci, 1, (c, sortOrder) => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/menu/categories/{categoryId}", { params: { path: { ...path, categoryId: c.id } }, body: { name: c.name, description: c.description ?? undefined, sortOrder } })))}>
              ↓
            </Button>
            <Button variant="secondary" onClick={() => setEditingCategory(category)}>
              Edit
            </Button>
            <ConfirmButton
              label="Delete"
              confirmLabel={category.items.length ? `Delete with ${category.items.length} items?` : "Really delete?"}
              onConfirm={() => run.mutate({ work: () => unwrap(api.DELETE("/api/owner/restaurants/{restaurantId}/menu/categories/{categoryId}", { params: { path: { ...path, categoryId: category.id } } })), done: "Category deleted" })}
            />
          </div>

          {category.items.length === 0 && <p className="text-sm text-stone-500">No dishes in this category yet.</p>}
          <ul className="divide-y divide-stone-100">
            {category.items.map((item, ii) => (
              <li key={item.id} className="flex flex-wrap items-center gap-3 py-2">
                {item.imageUrl ? (
                   
                  <img src={item.imageUrl} alt="" className="size-12 rounded-lg object-cover" />
                ) : (
                  <div className="size-12 rounded-lg bg-stone-100" />
                )}
                <div className="mr-auto min-w-40">
                  <p className="flex items-center gap-2 font-medium">
                    <VegMark veg={item.veg} /> {item.name}
                    {!item.available && <Badge tone="red">Unavailable</Badge>}
                  </p>
                  <p className="text-sm text-stone-500">{formatPaise(item.pricePaise)}</p>
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={item.available}
                    disabled={run.isPending}
                    onChange={(e) => {
                      // Optimistic: flip it now; the refetch after the request settles corrects it if it failed.
                      const available = e.target.checked;
                      queryClient.setQueryData<MenuCategory[]>(key, (cats) =>
                        cats?.map((c) => ({ ...c, items: c.items.map((it) => (it.id === item.id ? { ...it, available } : it)) })),
                      );
                      run.mutate({
                        work: () => unwrap(api.PATCH("/api/owner/restaurants/{restaurantId}/menu/items/{itemId}/availability", { params: { path: { ...path, itemId: item.id } }, body: { available } })),
                        done: available ? `${item.name} is available` : `${item.name} marked unavailable`,
                      });
                    }}
                  />
                  Available
                </label>
                <Button variant="ghost" aria-label={`Move ${item.name} up`} disabled={ii === 0 || run.isPending}
                  onClick={() => reorder(category.items, ii, -1, (it, sortOrder) => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/menu/items/{itemId}", { params: { path: { ...path, itemId: it.id } }, body: itemRequest(it, { sortOrder }) })))}>
                  ↑
                </Button>
                <Button variant="ghost" aria-label={`Move ${item.name} down`} disabled={ii === category.items.length - 1 || run.isPending}
                  onClick={() => reorder(category.items, ii, 1, (it, sortOrder) => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/menu/items/{itemId}", { params: { path: { ...path, itemId: it.id } }, body: itemRequest(it, { sortOrder }) })))}>
                  ↓
                </Button>
                <Button variant="secondary" onClick={() => setEditingItem({ item, categoryId: category.id })}>
                  Edit
                </Button>
                <ConfirmButton
                  label="Delete"
                  onConfirm={() => run.mutate({ work: () => unwrap(api.DELETE("/api/owner/restaurants/{restaurantId}/menu/items/{itemId}", { params: { path: { ...path, itemId: item.id } } })), done: `${item.name} deleted` })}
                />
              </li>
            ))}
          </ul>

          {editingItem?.categoryId === category.id ? (
            <ItemForm
              restaurantId={restaurantId}
              categories={categories}
              item={editingItem.item}
              categoryId={category.id}
              nextSortOrder={category.items.length}
              onCancel={() => setEditingItem(null)}
              onSave={async (body) => {
                if (editingItem.item) await unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/menu/items/{itemId}", { params: { path: { ...path, itemId: editingItem.item.id } }, body }));
                else await unwrap(api.POST("/api/owner/restaurants/{restaurantId}/menu/items", { params: { path }, body }));
                setEditingItem(null);
                toast.success("Dish saved");
                await refresh();
              }}
            />
          ) : (
            <Button variant="secondary" onClick={() => setEditingItem({ item: null, categoryId: category.id })}>
              Add dish
            </Button>
          )}
        </Card>
      ))}
    </div>
  );
}

function CategoryForm({
  category,
  nextSortOrder,
  onSave,
  onCancel,
}: {
  category: MenuCategory | null;
  nextSortOrder: number;
  onSave: (body: { name: string; description?: string; sortOrder: number }) => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(category?.name ?? "");
  const [description, setDescription] = useState(category?.description ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return setError("Enter a name.");
    setBusy(true);
    try {
      await onSave({ name: name.trim(), description: description.trim() || undefined, sortOrder: category?.sortOrder ?? nextSortOrder });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3 border-brand-200">
      <form onSubmit={submit} className="space-y-3" noValidate>
        <h2 className="font-semibold">{category ? "Edit category" : "New category"}</h2>
        <Field label="Name">
          <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        </Field>
        <Field label="Description (optional)">
          <input className={inputClass} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <InlineError message={error} />
        <div className="flex gap-2">
          <Button type="submit" busy={busy}>
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

function ItemForm({
  restaurantId,
  categories,
  item,
  categoryId,
  nextSortOrder,
  onSave,
  onCancel,
}: {
  restaurantId: string;
  categories: MenuCategory[];
  item: MenuItem | null;
  categoryId: string;
  nextSortOrder: number;
  onSave: (body: MenuItemRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const toast = useToast();
  const [form, setForm] = useState({
    categoryId: item?.categoryId ?? categoryId,
    name: item?.name ?? "",
    description: item?.description ?? "",
    price: item ? (item.pricePaise / 100).toString() : "",
    veg: item?.veg ?? true,
    available: item?.available ?? true,
    imageKey: item?.imageKey ?? null,
  });
  const [preview, setPreview] = useState<string | null>(item?.imageUrl ?? null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const pickImage = async (file: File | undefined) => {
    if (!file) return;
    setUploading(true);
    try {
      const key = await uploadImage(restaurantId, "MENU_ITEM_IMAGE", file);
      setForm((f) => ({ ...f, imageKey: key }));
      setPreview(URL.createObjectURL(file));
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setUploading(false);
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const pricePaise = paiseFromRupees(form.price);
    if (!form.name.trim()) return setError("Enter the dish name.");
    if (pricePaise === null || pricePaise <= 0) return setError("Enter a price in rupees, e.g. 249 or 249.50.");
    setBusy(true);
    try {
      await onSave({
        categoryId: form.categoryId,
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        pricePaise,
        veg: form.veg,
        imageKey: form.imageKey ?? undefined,
        available: form.available,
        sortOrder: item && item.categoryId === form.categoryId ? item.sortOrder : nextSortOrder,
      });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border border-brand-200 p-3" noValidate>
      <h3 className="font-semibold">{item ? `Edit ${item.name}` : "New dish"}</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Name">
          <input className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} autoFocus />
        </Field>
        <Field label="Price (₹)">
          <input className={inputClass} value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} inputMode="decimal" placeholder="249" />
        </Field>
        <Field label="Category">
          <select className={inputClass} value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Description (optional)">
          <input className={inputClass} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </Field>
      </div>
      <div className="flex flex-wrap items-center gap-4">
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.veg} onChange={(e) => setForm({ ...form, veg: e.target.checked })} /> Vegetarian
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.available} onChange={(e) => setForm({ ...form, available: e.target.checked })} /> Available
        </label>
        {preview && (
           
          <img src={preview} alt="" className="size-12 rounded-lg object-cover" />
        )}
        <label className="cursor-pointer rounded-xl border border-stone-300 px-3 py-2 text-sm font-semibold hover:bg-stone-100">
          {uploading ? "Uploading…" : preview ? "Change photo" : "Add photo"}
          <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={uploading} onChange={(e) => pickImage(e.target.files?.[0])} />
        </label>
        {preview && (
          <Button type="button" variant="ghost" onClick={() => { setForm({ ...form, imageKey: null }); setPreview(null); }}>
            Remove photo
          </Button>
        )}
      </div>
      <InlineError message={error} />
      <div className="flex gap-2">
        <Button type="submit" busy={busy} disabled={uploading}>
          Save dish
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
