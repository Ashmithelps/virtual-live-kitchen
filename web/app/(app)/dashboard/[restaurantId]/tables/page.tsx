"use client";

import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { DiningTable } from "@/lib/api/types";
import { ownerKeys } from "@/lib/owner/queries";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Button, Card, EmptyState, ErrorState, PageLoading, inputBaseClass, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

export default function TablesPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ownerKeys.tables(restaurantId);
  const path = { restaurantId };
  const tables = useQuery({ queryKey: key, queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/tables", { params: { path } })) });
  const [label, setLabel] = useState("");
  const [renaming, setRenaming] = useState<{ id: string; label: string } | null>(null);
  const [downloading, setDownloading] = useState(false);

  const run = useMutation({
    mutationFn: (task: { work: () => Promise<unknown>; done: string }) => task.work(),
    onSuccess: (_, task) => toast.success(task.done),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => queryClient.invalidateQueries({ queryKey: key }),
  });

  const add = (e: FormEvent) => {
    e.preventDefault();
    if (!label.trim()) return;
    run.mutate({ work: () => unwrap(api.POST("/api/owner/restaurants/{restaurantId}/tables", { params: { path }, body: { label: label.trim() } })), done: `Table ${label.trim()} added` });
    setLabel("");
  };

  const downloadPdf = async () => {
    setDownloading(true);
    try {
      const result = await api.GET("/api/owner/restaurants/{restaurantId}/tables/qr-codes.pdf", { params: { path }, parseAs: "blob" });
      const status = result.response.status;
      if (!result.response.ok || !result.data) throw new Error(`Couldn't create the PDF (HTTP ${status})`);
      const url = URL.createObjectURL(result.data as Blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "table-qr-codes.pdf";
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setDownloading(false);
    }
  };

  if (tables.isPending) return <PageLoading />;
  if (tables.isError) return <ErrorState message={errorMessage(tables.error)} onRetry={() => tables.refetch()} />;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <form onSubmit={add} className="flex gap-2">
          <input className={`${inputBaseClass} w-40`} value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Table label, e.g. T4" aria-label="New table label" />
          <Button type="submit" disabled={!label.trim() || run.isPending}>
            Add table
          </Button>
        </form>
        <Button variant="secondary" onClick={downloadPdf} busy={downloading} disabled={tables.data.length === 0}>
          Download QR codes (PDF)
        </Button>
      </div>
      <p className="text-sm text-stone-600">Each table has its own QR code. Customers who scan it order straight to that table. Regenerating a code makes the old printout stop working.</p>

      {tables.data.length === 0 ? (
        <EmptyState title="No tables yet" message="Add your tables to print QR codes for dine-in ordering." />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {tables.data.map((table: DiningTable) => (
            <Card key={table.id} className="space-y-2">
              {renaming?.id === table.id ? (
                <form
                  className="flex gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    run.mutate({
                      work: () => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/tables/{tableId}", { params: { path: { ...path, tableId: table.id } }, body: { label: renaming.label.trim() } })),
                      done: "Table renamed",
                    });
                    setRenaming(null);
                  }}
                >
                  <input className={inputClass} value={renaming.label} onChange={(e) => setRenaming({ ...renaming, label: e.target.value })} autoFocus aria-label="Table label" />
                  <Button type="submit">Save</Button>
                </form>
              ) : (
                <p className="text-lg font-semibold">Table {table.label}</p>
              )}
              <a href={table.qrUrl} target="_blank" rel="noreferrer" className="block truncate text-xs text-brand-700 underline" title={table.qrUrl}>
                {table.qrUrl}
              </a>
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" onClick={() => setRenaming({ id: table.id, label: table.label })}>
                  Rename
                </Button>
                <ConfirmButton
                  label="New QR code"
                  confirmLabel="Old printout stops working. Continue?"
                  onConfirm={() => run.mutate({ work: () => unwrap(api.POST("/api/owner/restaurants/{restaurantId}/tables/{tableId}/qr-token", { params: { path: { ...path, tableId: table.id } } })), done: `New QR code for ${table.label}. Reprint it.` })}
                />
                <ConfirmButton
                  label="Delete"
                  onConfirm={() => run.mutate({ work: () => unwrap(api.DELETE("/api/owner/restaurants/{restaurantId}/tables/{tableId}", { params: { path: { ...path, tableId: table.id } } })), done: `Table ${table.label} deleted` })}
                />
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
