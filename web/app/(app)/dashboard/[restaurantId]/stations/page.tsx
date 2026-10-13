"use client";

import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import type { Station } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { chefsQuery, ownerKeys, stationsQuery } from "@/lib/owner/queries";
import { deviceOnline } from "@/lib/owner/status";
import { ConfirmButton } from "@/components/dashboard/confirm-button";
import { Badge, Button, Card, EmptyState, ErrorState, PageLoading, inputBaseClass, inputClass } from "@/components/ui";
import { useToast } from "@/components/toast";

type Pairing = components["schemas"]["PairingResponse"];

export default function StationsPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const path = { restaurantId };
  // Polls so the online dot and "paired" state follow the phone without a reload.
  const stations = useQuery({ ...stationsQuery(restaurantId), refetchInterval: 10_000 });
  const chefs = useQuery(chefsQuery(restaurantId));
  const [name, setName] = useState("");
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const [pairing, setPairingState] = useState<(Pairing & { shownAtMs: number }) | null>(null);
  const setPairing = (p: Pairing | null) => setPairingState(p && { ...p, shownAtMs: Date.now() });
  const now = useNow(10_000);

  const run = useMutation({
    mutationFn: (task: { work: () => Promise<unknown>; done: string }) => task.work(),
    onSuccess: (_, task) => toast.success(task.done),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ownerKeys.stations(restaurantId) });
      void queryClient.invalidateQueries({ queryKey: ownerKeys.chefs(restaurantId) });
    },
  });

  const startPairing = useMutation({
    mutationFn: (stationId: string) => unwrap(api.POST("/api/owner/restaurants/{restaurantId}/stations/{stationId}/pairing", { params: { path: { ...path, stationId } } })),
    onSuccess: setPairing,
    onError: (error) => toast.error(errorMessage(error)),
  });

  const add = (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    run.mutate({ work: () => unwrap(api.POST("/api/owner/restaurants/{restaurantId}/stations", { params: { path }, body: { name: name.trim() } })), done: `Station ${name.trim()} added` });
    setName("");
  };

  if (stations.isPending) return <PageLoading />;
  if (stations.isError) return <ErrorState message={errorMessage(stations.error)} onRetry={() => stations.refetch()} />;

  const pairedStation = pairing && stations.data.find((s) => s.id === pairing.stationId);
  // The phone has paired once the station reports a pairing newer than this QR.
  const pairedAfterQr = !!pairedStation?.devicePairedAt && Date.parse(pairedStation.devicePairedAt) >= pairing!.shownAtMs - 5_000;

  return (
    <div className="space-y-4">
      <form onSubmit={add} className="flex flex-wrap gap-2">
        <input className={`${inputBaseClass} w-56`} value={name} onChange={(e) => setName(e.target.value)} placeholder="Station name, e.g. Tandoor" aria-label="New station name" />
        <Button type="submit" disabled={!name.trim() || run.isPending}>
          Add station
        </Button>
      </form>
      <p className="text-sm text-stone-600">
        Each prep station has a phone running <code>/station</code> that records orders. Pair it by scanning the QR with the phone&apos;s camera. A phone shows as online while its page is open.
      </p>

      {pairing && (
        <Card className="flex flex-col items-center gap-3 text-center">
          {pairedAfterQr ? (
            <>
              <p className="text-2xl font-bold text-green-700">✓ Paired</p>
              <p>The {pairing.stationName} phone is ready to record.</p>
            </>
          ) : (
            <>
              <p className="font-semibold">Scan with the {pairing.stationName} phone</p>
              { }
              <img src={pairing.qrSvgDataUri} alt={`Pairing QR for ${pairing.stationName}`} className="size-64 rounded-xl bg-white p-2" />
              <a href={pairing.pairingUrl} target="_blank" rel="noreferrer" className="max-w-full truncate text-xs text-brand-700 underline">
                {pairing.pairingUrl}
              </a>
              <p className="text-sm text-stone-500">One-time code, valid until {formatDateTime(pairing.expiresAt)}.</p>
            </>
          )}
          <Button variant="secondary" onClick={() => setPairing(null)}>
            {pairedAfterQr ? "Done" : "Close"}
          </Button>
        </Card>
      )}

      {stations.data.length === 0 ? (
        <EmptyState title="No stations yet" message="Add a station for each place food is prepared (tandoor, curry, grill…)." />
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {stations.data.map((station: Station) => {
            const online = deviceOnline(station.deviceLastSeenAt, now);
            const unassigned = (chefs.data ?? []).filter((c) => !station.chefs.some((a) => a.id === c.id));
            return (
              <Card key={station.id} className="space-y-3">
                <div className="flex flex-wrap items-center gap-2">
                  {renaming?.id === station.id ? (
                    <form
                      className="flex gap-2"
                      onSubmit={(e) => {
                        e.preventDefault();
                        run.mutate({ work: () => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/stations/{stationId}", { params: { path: { ...path, stationId: station.id } }, body: { name: renaming.name.trim() } })), done: "Station renamed" });
                        setRenaming(null);
                      }}
                    >
                      <input className={inputClass} value={renaming.name} onChange={(e) => setRenaming({ ...renaming, name: e.target.value })} autoFocus aria-label="Station name" />
                      <Button type="submit">Save</Button>
                    </form>
                  ) : (
                    <p className="mr-auto text-lg font-semibold">{station.name}</p>
                  )}
                  {!station.deviceTokenIssued ? (
                    <Badge>No phone</Badge>
                  ) : online ? (
                    <Badge tone="green">● Phone online</Badge>
                  ) : (
                    <Badge tone="red">● Phone offline</Badge>
                  )}
                </div>
                <p className="text-sm text-stone-500">
                  {station.devicePairedAt ? `Paired ${formatDateTime(station.devicePairedAt)}` : "Not paired"}
                  {station.deviceLastSeenAt && ` · last seen ${formatDateTime(station.deviceLastSeenAt)}`}
                </p>

                <div>
                  <p className="text-sm font-medium">Chefs on this station</p>
                  {station.chefs.length === 0 && <p className="text-sm text-stone-500">None. Orders started here need a chef assigned.</p>}
                  <ul className="mt-1 flex flex-wrap gap-2">
                    {station.chefs.map((chef) => (
                      <li key={chef.id} className="flex items-center gap-1 rounded-full bg-stone-100 py-1 pl-3 pr-1 text-sm">
                        {chef.displayName}
                        <button
                          aria-label={`Remove ${chef.displayName} from ${station.name}`}
                          className="rounded-full px-2 text-stone-500 hover:bg-stone-200"
                          onClick={() => run.mutate({ work: () => unwrap(api.DELETE("/api/owner/restaurants/{restaurantId}/stations/{stationId}/chefs/{chefId}", { params: { path: { ...path, stationId: station.id, chefId: chef.id } } })), done: `${chef.displayName} removed from ${station.name}` })}
                        >
                          ×
                        </button>
                      </li>
                    ))}
                  </ul>
                  {unassigned.length > 0 && (
                    <select
                      className={`${inputBaseClass} mt-2 w-full max-w-64`}
                      value=""
                      aria-label={`Assign a chef to ${station.name}`}
                      onChange={(e) => {
                        const chef = unassigned.find((c) => c.id === e.target.value);
                        if (chef) run.mutate({ work: () => unwrap(api.PUT("/api/owner/restaurants/{restaurantId}/stations/{stationId}/chefs/{chefId}", { params: { path: { ...path, stationId: station.id, chefId: chef.id } } })), done: `${chef.displayName} assigned to ${station.name}` });
                      }}
                    >
                      <option value="">Assign a chef…</option>
                      {unassigned.map((c) => (
                        <option key={c.id} value={c.id} disabled={!c.recordingConsent}>
                          {c.displayName}
                          {c.recordingConsent ? "" : " (no recording consent yet)"}
                        </option>
                      ))}
                    </select>
                  )}
                </div>

                <div className="flex flex-wrap gap-2">
                  <Button onClick={() => startPairing.mutate(station.id)} busy={startPairing.isPending && startPairing.variables === station.id}>
                    {station.deviceTokenIssued ? "Re-pair phone" : "Pair phone"}
                  </Button>
                  <Button variant="secondary" onClick={() => setRenaming({ id: station.id, name: station.name })}>
                    Rename
                  </Button>
                  <ConfirmButton
                    label="Delete"
                    onConfirm={() => run.mutate({ work: () => unwrap(api.DELETE("/api/owner/restaurants/{restaurantId}/stations/{stationId}", { params: { path: { ...path, stationId: station.id } } })), done: `Station ${station.name} deleted` })}
                  />
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

/** Re-renders every `ms` so time-based labels (online/offline) stay current. */
function useNow(ms: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(id);
  }, [ms]);
  return now;
}
